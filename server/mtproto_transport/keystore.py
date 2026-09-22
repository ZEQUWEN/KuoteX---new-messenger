"""Серверное хранилище auth_key и состояний сессий.

Зачем: сейчас ключи живут в памяти процесса. Любой перезапуск сервера —
и все клиенты получают -404, идут на новый handshake. При тысяче
подключений это тысяча дорогих DH-обменов одновременно, то есть
самому себе устроенный DDoS сразу после деплоя.

Здесь три реализации за одним интерфейсом:
  * InMemoryKeyStore  — тесты и одиночный процесс;
  * SqliteKeyStore    — один сервер, ноль зависимостей, переживает рестарт;
  * RedisKeyStore     — несколько серверов за балансировщиком.

Изоляция ключей — отдельная забота. auth_key не должен лежать в базе
открытым: сервер обычно бэкапится, а бэкап утекает чаще самой базы.
Поэтому ключи шифруются мастер-ключом (AES-256-GCM), который берётся
из переменной окружения и в базу не попадает.
"""
from __future__ import annotations

import abc
import base64
import hashlib
import os
import secrets
import sqlite3
import threading
import time
from dataclasses import dataclass

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

AUTH_KEY_SIZE = 256
DEFAULT_MAX_AGE_SECONDS = 7 * 24 * 60 * 60


class KeyStoreError(Exception):
    """Ошибка хранилища."""


@dataclass(slots=True)
class AuthKeyRecord:
    """Одна запись: ключ и связанное с ним состояние."""

    auth_key_id: int
    auth_key: bytes
    server_salt: int = 0
    created_at: float = 0.0
    last_used_at: float = 0.0
    # Привязка к клиенту: помогает отзывать ключи при выходе из аккаунта.
    user_id: int | None = None

    def __post_init__(self):
        if len(self.auth_key) != AUTH_KEY_SIZE:
            raise KeyStoreError("auth_key must be 256 bytes")
        if not self.created_at:
            self.created_at = time.time()

    def age_seconds(self, now: float | None = None) -> float:
        return (now or time.time()) - self.created_at

    def is_expired(self, max_age: float = DEFAULT_MAX_AGE_SECONDS,
                   now: float | None = None) -> bool:
        age = self.age_seconds(now)
        # Отрицательный возраст = часы сервера перевели назад.
        return age < 0 or age > max_age


# ---------------------------------------------------------------- encryption

class KeyEncryptor:
    """Шифрует auth_key перед записью в хранилище (envelope encryption).

    Мастер-ключ живёт в переменной окружения или в KMS, но не в базе.
    Утечка дампа базы без мастер-ключа бесполезна.
    """

    __slots__ = ("_aead",)

    def __init__(self, master_key: bytes):
        if len(master_key) != 32:
            raise KeyStoreError("master key must be 32 bytes (256 bit)")
        self._aead = AESGCM(master_key)

    @classmethod
    def from_env(cls, var: str = "MTPROTO_MASTER_KEY") -> "KeyEncryptor":
        raw = os.getenv(var)
        if not raw:
            raise KeyStoreError(
                f"{var} is not set; generate one with KeyEncryptor.generate_master_key()"
            )
        try:
            key = base64.b64decode(raw)
        except Exception as exc:
            raise KeyStoreError(f"{var} must be base64") from exc
        return cls(key)

    @staticmethod
    def generate_master_key() -> str:
        """Новый мастер-ключ в base64 — положить в переменную окружения."""
        return base64.b64encode(os.urandom(32)).decode()

    def encrypt(self, auth_key: bytes, aad: bytes = b"") -> bytes:
        # Nonce хранится рядом с шифротекстом: он не секретный,
        # но обязан быть уникальным для каждой записи.
        nonce = os.urandom(12)
        return nonce + self._aead.encrypt(nonce, auth_key, aad)

    def decrypt(self, blob: bytes, aad: bytes = b"") -> bytes:
        if len(blob) < 13:
            raise KeyStoreError("encrypted blob too short")
        return self._aead.decrypt(blob[:12], blob[12:], aad)


# ---------------------------------------------------------------- interface

class AuthKeyStore(abc.ABC):
    """Общий интерфейс серверного хранилища."""

    @abc.abstractmethod
    def save(self, record: AuthKeyRecord) -> None: ...

    @abc.abstractmethod
    def load(self, auth_key_id: int) -> AuthKeyRecord | None: ...

    @abc.abstractmethod
    def update_salt(self, auth_key_id: int, server_salt: int) -> None: ...

    @abc.abstractmethod
    def touch(self, auth_key_id: int) -> None:
        """Отметить ключ как использованный (для сборки мусора)."""

    @abc.abstractmethod
    def delete(self, auth_key_id: int) -> None: ...

    @abc.abstractmethod
    def delete_for_user(self, user_id: int) -> int:
        """Отозвать все ключи пользователя. Возвращает число удалённых."""

    @abc.abstractmethod
    def purge_expired(self, max_age: float = DEFAULT_MAX_AGE_SECONDS) -> int: ...

    @abc.abstractmethod
    def count(self) -> int: ...

    @abc.abstractmethod
    def close(self) -> None: ...


# ---------------------------------------------------------------- in-memory

class InMemoryKeyStore(AuthKeyStore):
    """Для тестов и одиночного процесса. Не переживает перезапуск."""

    def __init__(self):
        self._data: dict[int, AuthKeyRecord] = {}
        self._lock = threading.RLock()

    def save(self, record: AuthKeyRecord) -> None:
        with self._lock:
            # Копия: вызывающий код может затереть свой массив.
            self._data[record.auth_key_id] = AuthKeyRecord(
                auth_key_id=record.auth_key_id,
                auth_key=bytes(record.auth_key),
                server_salt=record.server_salt,
                created_at=record.created_at,
                last_used_at=record.last_used_at,
                user_id=record.user_id,
            )

    def load(self, auth_key_id: int) -> AuthKeyRecord | None:
        with self._lock:
            found = self._data.get(auth_key_id)
            if found is None:
                return None
            return AuthKeyRecord(
                auth_key_id=found.auth_key_id,
                auth_key=bytes(found.auth_key),
                server_salt=found.server_salt,
                created_at=found.created_at,
                last_used_at=found.last_used_at,
                user_id=found.user_id,
            )

    def update_salt(self, auth_key_id: int, server_salt: int) -> None:
        with self._lock:
            if auth_key_id in self._data:
                self._data[auth_key_id].server_salt = server_salt

    def touch(self, auth_key_id: int) -> None:
        with self._lock:
            if auth_key_id in self._data:
                self._data[auth_key_id].last_used_at = time.time()

    def delete(self, auth_key_id: int) -> None:
        with self._lock:
            self._data.pop(auth_key_id, None)

    def delete_for_user(self, user_id: int) -> int:
        with self._lock:
            victims = [k for k, v in self._data.items() if v.user_id == user_id]
            for key in victims:
                del self._data[key]
            return len(victims)

    def purge_expired(self, max_age: float = DEFAULT_MAX_AGE_SECONDS) -> int:
        now = time.time()
        with self._lock:
            victims = [k for k, v in self._data.items() if v.is_expired(max_age, now)]
            for key in victims:
                del self._data[key]
            return len(victims)

    def count(self) -> int:
        with self._lock:
            return len(self._data)

    def close(self) -> None:
        with self._lock:
            self._data.clear()


# ---------------------------------------------------------------- sqlite

class SqliteKeyStore(AuthKeyStore):
    """Хранилище на SQLite: переживает перезапуск, без внешних сервисов.

    Подходит для одного сервера — то, что нужно на старте. Когда
    серверов станет несколько, меняется одна строка на RedisKeyStore.
    """

    _SCHEMA = """
    CREATE TABLE IF NOT EXISTS auth_keys (
        auth_key_id   INTEGER PRIMARY KEY,
        auth_key_enc  BLOB    NOT NULL,
        server_salt   INTEGER NOT NULL DEFAULT 0,
        created_at    REAL    NOT NULL,
        last_used_at  REAL    NOT NULL DEFAULT 0,
        user_id       INTEGER
    );
    CREATE INDEX IF NOT EXISTS idx_auth_keys_user ON auth_keys(user_id);
    CREATE INDEX IF NOT EXISTS idx_auth_keys_created ON auth_keys(created_at);
    """

    def __init__(self, path: str, encryptor: KeyEncryptor):
        self._encryptor = encryptor
        self._lock = threading.RLock()
        # check_same_thread=False: сервер асинхронный, обращения приходят
        # из разных потоков пула. Доступ сериализуем своим локом.
        self._conn = sqlite3.connect(path, check_same_thread=False)
        # WAL: читатели не блокируют писателя — важно под нагрузкой.
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._conn.execute("PRAGMA synchronous=NORMAL")
        self._conn.executescript(self._SCHEMA)
        self._conn.commit()

    def _aad(self, auth_key_id: int) -> bytes:
        """Привязка шифротекста к идентификатору.

        Без AAD злоумышленник с доступом к базе мог бы переставить
        зашифрованные ключи между строками, и подмена прошла бы незаметно.
        """
        return str(auth_key_id).encode()

    def save(self, record: AuthKeyRecord) -> None:
        blob = self._encryptor.encrypt(record.auth_key, self._aad(record.auth_key_id))
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO auth_keys
                    (auth_key_id, auth_key_enc, server_salt, created_at, last_used_at, user_id)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(auth_key_id) DO UPDATE SET
                    auth_key_enc = excluded.auth_key_enc,
                    server_salt  = excluded.server_salt,
                    created_at   = excluded.created_at,
                    last_used_at = excluded.last_used_at,
                    user_id      = excluded.user_id
                """,
                (record.auth_key_id, blob, record.server_salt,
                 record.created_at, record.last_used_at, record.user_id),
            )
            self._conn.commit()

    def load(self, auth_key_id: int) -> AuthKeyRecord | None:
        with self._lock:
            row = self._conn.execute(
                "SELECT auth_key_enc, server_salt, created_at, last_used_at, user_id "
                "FROM auth_keys WHERE auth_key_id = ?",
                (auth_key_id,),
            ).fetchone()
        if row is None:
            return None

        try:
            auth_key = self._encryptor.decrypt(row[0], self._aad(auth_key_id))
        except Exception:
            # Мастер-ключ сменили или запись повреждена. Держать
            # нерасшифровываемую строку смысла нет.
            self.delete(auth_key_id)
            return None

        return AuthKeyRecord(
            auth_key_id=auth_key_id,
            auth_key=auth_key,
            server_salt=row[1],
            created_at=row[2],
            last_used_at=row[3],
            user_id=row[4],
        )

    def update_salt(self, auth_key_id: int, server_salt: int) -> None:
        with self._lock:
            self._conn.execute(
                "UPDATE auth_keys SET server_salt = ? WHERE auth_key_id = ?",
                (server_salt, auth_key_id),
            )
            self._conn.commit()

    def touch(self, auth_key_id: int) -> None:
        with self._lock:
            self._conn.execute(
                "UPDATE auth_keys SET last_used_at = ? WHERE auth_key_id = ?",
                (time.time(), auth_key_id),
            )
            self._conn.commit()

    def delete(self, auth_key_id: int) -> None:
        with self._lock:
            self._conn.execute(
                "DELETE FROM auth_keys WHERE auth_key_id = ?", (auth_key_id,)
            )
            self._conn.commit()

    def delete_for_user(self, user_id: int) -> int:
        with self._lock:
            cursor = self._conn.execute(
                "DELETE FROM auth_keys WHERE user_id = ?", (user_id,)
            )
            self._conn.commit()
            return cursor.rowcount

    def purge_expired(self, max_age: float = DEFAULT_MAX_AGE_SECONDS) -> int:
        threshold = time.time() - max_age
        with self._lock:
            cursor = self._conn.execute(
                "DELETE FROM auth_keys WHERE created_at < ?", (threshold,)
            )
            self._conn.commit()
            return cursor.rowcount

    def count(self) -> int:
        with self._lock:
            return self._conn.execute("SELECT COUNT(*) FROM auth_keys").fetchone()[0]

    def close(self) -> None:
        with self._lock:
            self._conn.close()


# ---------------------------------------------------------------- redis

class RedisKeyStore(AuthKeyStore):
    """Хранилище на Redis: общее для нескольких серверов.

    Нужно, когда за балансировщиком стоит больше одного процесса:
    клиент может попасть на любой из них, и ключ должен быть виден всем.

    Требует пакет `redis` (см. requirements.txt).
    """

    def __init__(self, client, encryptor: KeyEncryptor,
                 prefix: str = "mtproto:authkey:",
                 ttl_seconds: int = DEFAULT_MAX_AGE_SECONDS):
        self._redis = client
        self._encryptor = encryptor
        self._prefix = prefix
        self._ttl = ttl_seconds

    @classmethod
    def from_url(cls, url: str, encryptor: KeyEncryptor, **kwargs) -> "RedisKeyStore":
        try:
            import redis
        except ImportError as exc:
            raise KeyStoreError(
                "redis package is required: pip install redis"
            ) from exc
        return cls(redis.Redis.from_url(url), encryptor, **kwargs)

    def _key(self, auth_key_id: int) -> str:
        return f"{self._prefix}{auth_key_id}"

    def _user_key(self, user_id: int) -> str:
        return f"{self._prefix}user:{user_id}"

    def _aad(self, auth_key_id: int) -> bytes:
        return str(auth_key_id).encode()

    def save(self, record: AuthKeyRecord) -> None:
        blob = self._encryptor.encrypt(record.auth_key, self._aad(record.auth_key_id))
        mapping = {
            "key": blob,
            "salt": str(record.server_salt),
            "created": str(record.created_at),
            "used": str(record.last_used_at),
        }
        if record.user_id is not None:
            mapping["user"] = str(record.user_id)

        pipe = self._redis.pipeline()
        pipe.hset(self._key(record.auth_key_id), mapping=mapping)
        # TTL заменяет сборку мусора: Redis сам выбросит просроченное.
        pipe.expire(self._key(record.auth_key_id), self._ttl)
        if record.user_id is not None:
            # Обратный индекс: чтобы отозвать все ключи пользователя.
            pipe.sadd(self._user_key(record.user_id), record.auth_key_id)
            pipe.expire(self._user_key(record.user_id), self._ttl)
        pipe.execute()

    def load(self, auth_key_id: int) -> AuthKeyRecord | None:
        data = self._redis.hgetall(self._key(auth_key_id))
        if not data:
            return None

        def field(name: str):
            return data.get(name.encode()) or data.get(name)

        blob = field("key")
        if blob is None:
            return None
        try:
            auth_key = self._encryptor.decrypt(blob, self._aad(auth_key_id))
        except Exception:
            self.delete(auth_key_id)
            return None

        user_raw = field("user")
        return AuthKeyRecord(
            auth_key_id=auth_key_id,
            auth_key=auth_key,
            server_salt=int(field("salt") or 0),
            created_at=float(field("created") or 0),
            last_used_at=float(field("used") or 0),
            user_id=int(user_raw) if user_raw else None,
        )

    def update_salt(self, auth_key_id: int, server_salt: int) -> None:
        if self._redis.exists(self._key(auth_key_id)):
            self._redis.hset(self._key(auth_key_id), "salt", str(server_salt))

    def touch(self, auth_key_id: int) -> None:
        name = self._key(auth_key_id)
        if self._redis.exists(name):
            pipe = self._redis.pipeline()
            pipe.hset(name, "used", str(time.time()))
            pipe.expire(name, self._ttl)     # продлеваем жизнь активному ключу
            pipe.execute()

    def delete(self, auth_key_id: int) -> None:
        self._redis.delete(self._key(auth_key_id))

    def delete_for_user(self, user_id: int) -> int:
        members = self._redis.smembers(self._user_key(user_id))
        if not members:
            return 0
        pipe = self._redis.pipeline()
        for member in members:
            pipe.delete(self._key(int(member)))
        pipe.delete(self._user_key(user_id))
        pipe.execute()
        return len(members)

    def purge_expired(self, max_age: float = DEFAULT_MAX_AGE_SECONDS) -> int:
        # Redis удаляет по TTL сам, поэтому ручная чистка не нужна.
        return 0

    def count(self) -> int:
        return len(list(self._redis.scan_iter(match=f"{self._prefix}*", count=1000)))

    def close(self) -> None:
        with_close = getattr(self._redis, "close", None)
        if callable(with_close):
            with_close()


# ---------------------------------------------------------------- factory

def create_keystore(
    backend: str | None = None,
    *,
    sqlite_path: str | None = None,
    redis_url: str | None = None,
    encryptor: KeyEncryptor | None = None,
) -> AuthKeyStore:
    """Выбирает реализацию по конфигурации.

    backend: "memory" | "sqlite" | "redis" (по умолчанию из окружения).
    """
    backend = (backend or os.getenv("KEYSTORE_BACKEND", "sqlite")).lower()

    if backend == "memory":
        return InMemoryKeyStore()

    enc = encryptor or KeyEncryptor.from_env()

    if backend == "sqlite":
        path = sqlite_path or os.getenv("KEYSTORE_SQLITE_PATH", "mtproto_keys.db")
        return SqliteKeyStore(path, enc)

    if backend == "redis":
        url = redis_url or os.getenv("REDIS_URL", "redis://localhost:6379/0")
        return RedisKeyStore.from_url(url, enc)

    raise KeyStoreError(f"unknown backend: {backend}")


def auth_key_id_of(auth_key: bytes) -> int:
    """Идентификатор ключа — как в MTProtoCrypto.authKeyId на клиенте."""
    return int.from_bytes(hashlib.sha1(auth_key).digest()[12:20], "little", signed=True)
