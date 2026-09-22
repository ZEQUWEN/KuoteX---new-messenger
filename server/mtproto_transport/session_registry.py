"""Реестр сессий: связывает хранилище ключей с живыми соединениями.

Решает три задачи, из-за которых сервер иначе разваливается:

1. **Ключ переживает перезапуск.** Соединение оборвалось, процесс
   перезапустили — клиент подключается снова и продолжает работать
   с тем же auth_key, без нового handshake.

2. **Анти-replay переживает переподключение.** Окно принятых msg_id
   привязано к паре (auth_key_id, session_id), а не к TCP-соединению.
   Иначе злоумышленник переподключался бы и переигрывал перехваченные
   пакеты заново.

3. **Изоляция.** Ключ в памяти живёт ровно столько, сколько нужен,
   и затирается при выселении из кеша.
"""
from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field

from .crypto import ServerSession, SecurityViolation
from .keystore import (
    AuthKeyRecord, AuthKeyStore, DEFAULT_MAX_AGE_SECONDS, auth_key_id_of,
)


class UnknownAuthKey(SecurityViolation):
    """Сервер не знает такой auth_key — клиенту нужен новый handshake.

    Соответствует транспортной ошибке -404, которую обрабатывает
    MTProtoTransport.TransportError.requiresNewAuthKey на клиенте.
    """

    def __init__(self, auth_key_id: int):
        super().__init__(f"unknown auth_key_id {auth_key_id}")
        self.auth_key_id = auth_key_id


@dataclass
class RegistryStats:
    cache_hits: int = 0
    cache_misses: int = 0
    store_loads: int = 0
    unknown_keys: int = 0
    evictions: int = 0
    replays_blocked: int = 0


class SessionRegistry:
    """Кеш активных сессий поверх постоянного хранилища."""

    def __init__(
        self,
        store: AuthKeyStore,
        max_cached_sessions: int = 10_000,
        max_key_age: float = DEFAULT_MAX_AGE_SECONDS,
    ):
        self._store = store
        self._max_cached = max_cached_sessions
        self._max_age = max_key_age
        self._lock = threading.RLock()
        # (auth_key_id, session_id) -> ServerSession
        self._sessions: dict[tuple[int, int], ServerSession] = {}
        # порядок обращений для вытеснения по LRU
        self._lru: list[tuple[int, int]] = []
        self.stats = RegistryStats()

    # ------------------------------------------------------------ keys

    def register(self, auth_key: bytes, server_salt: int = 0,
                 user_id: int | None = None) -> int:
        """Сохраняет свежий ключ после handshake. Возвращает auth_key_id."""
        key_id = auth_key_id_of(auth_key)
        self._store.save(AuthKeyRecord(
            auth_key_id=key_id,
            auth_key=auth_key,
            server_salt=server_salt,
            created_at=time.time(),
            last_used_at=time.time(),
            user_id=user_id,
        ))
        return key_id

    def forget(self, auth_key_id: int) -> None:
        """Отзывает ключ и выбрасывает связанные сессии."""
        self._store.delete(auth_key_id)
        with self._lock:
            victims = [k for k in self._sessions if k[0] == auth_key_id]
            for key in victims:
                self._drop_locked(key)

    def logout_user(self, user_id: int) -> int:
        """Выход из аккаунта: все ключи пользователя недействительны."""
        removed = self._store.delete_for_user(user_id)
        with self._lock:
            self._sessions.clear()
            self._lru.clear()
        return removed

    # ------------------------------------------------------------ sessions

    def session_for(self, auth_key_id: int, session_id: int) -> ServerSession:
        """Возвращает сессию, поднимая ключ из хранилища при необходимости.

        :raises UnknownAuthKey: ключа нет или он просрочен -> ответить -404
        """
        cache_key = (auth_key_id, session_id)

        with self._lock:
            cached = self._sessions.get(cache_key)
            if cached is not None:
                self.stats.cache_hits += 1
                self._mark_used_locked(cache_key)
                return cached
            self.stats.cache_misses += 1

        record = self._store.load(auth_key_id)
        self.stats.store_loads += 1

        if record is None:
            self.stats.unknown_keys += 1
            raise UnknownAuthKey(auth_key_id)

        if record.is_expired(self._max_age):
            # Просроченный ключ удаляем сразу, чтобы не поднимать его снова.
            self._store.delete(auth_key_id)
            self.stats.unknown_keys += 1
            raise UnknownAuthKey(auth_key_id)

        session = ServerSession(
            auth_key=record.auth_key,
            session_id=session_id,
            server_salt=record.server_salt,
        )

        with self._lock:
            # Пока читали из хранилища, сессию мог создать другой поток.
            existing = self._sessions.get(cache_key)
            if existing is not None:
                self.stats.cache_hits += 1
                self._mark_used_locked(cache_key)
                return existing
            self._sessions[cache_key] = session
            self._lru.append(cache_key)
            self._evict_if_needed_locked()

        self._store.touch(auth_key_id)
        return session

    def unpack(self, envelope: bytes):
        """Расшифровывает конверт клиента, сам находя нужный ключ.

        Точка входа для сервера: auth_key_id читается из открытой части
        конверта, session_id — из уже расшифрованного тела.
        """
        if len(envelope) < 40:
            raise SecurityViolation("envelope too short")

        auth_key_id = int.from_bytes(envelope[:8], "little", signed=True)
        record = self._store.load(auth_key_id)
        if record is None:
            self.stats.unknown_keys += 1
            raise UnknownAuthKey(auth_key_id)
        if record.is_expired(self._max_age):
            self._store.delete(auth_key_id)
            self.stats.unknown_keys += 1
            raise UnknownAuthKey(auth_key_id)

        # Сначала расшифровываем, чтобы узнать session_id, затем берём
        # постоянную сессию — только у неё есть окно анти-replay.
        from .crypto import decrypt as _decrypt

        decrypted = _decrypt(record.auth_key, envelope, from_client=True)
        if len(decrypted) < 32:
            raise SecurityViolation("payload too short")
        session_id = int.from_bytes(decrypted[8:16], "little", signed=True)

        session = self.session_for(auth_key_id, session_id)
        try:
            return session.unpack(envelope)
        except SecurityViolation as exc:
            if "replay" in str(exc):
                self.stats.replays_blocked += 1
            raise

    def update_salt(self, auth_key_id: int, server_salt: int) -> None:
        """Новая соль: пишем и в хранилище, и в живые сессии."""
        self._store.update_salt(auth_key_id, server_salt)
        with self._lock:
            for (key_id, _), session in self._sessions.items():
                if key_id == auth_key_id:
                    session.server_salt = server_salt

    # ------------------------------------------------------------ maintenance

    def purge_expired(self) -> int:
        """Периодическая уборка. Вызывать по таймеру раз в час."""
        return self._store.purge_expired(self._max_age)

    def cached_sessions(self) -> int:
        with self._lock:
            return len(self._sessions)

    def close(self) -> None:
        with self._lock:
            self._sessions.clear()
            self._lru.clear()
        self._store.close()

    # ------------------------------------------------------------ internals

    def _mark_used_locked(self, cache_key) -> None:
        try:
            self._lru.remove(cache_key)
        except ValueError:
            pass
        self._lru.append(cache_key)

    def _evict_if_needed_locked(self) -> None:
        while len(self._sessions) > self._max_cached:
            oldest = self._lru.pop(0)
            self._drop_locked(oldest, already_removed_from_lru=True)
            self.stats.evictions += 1

    def _drop_locked(self, cache_key, already_removed_from_lru: bool = False) -> None:
        session = self._sessions.pop(cache_key, None)
        if session is not None:
            # Затираем ключ в памяти: он больше не нужен этой сессии.
            session.auth_key = b"\x00" * len(session.auth_key)
        if not already_removed_from_lru:
            try:
                self._lru.remove(cache_key)
            except ValueError:
                pass
