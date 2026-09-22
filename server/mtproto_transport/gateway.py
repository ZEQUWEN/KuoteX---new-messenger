"""Шлюз: сводит транспорт, handshake и хранилище ключей в рабочий сервер.

Это та точка, где всё соединяется:

    obfuscated TCP  ->  handshake (если ключа ещё нет)
                    ->  SessionRegistry (ключ из БД)
                    ->  расшифровка и разбор контейнера
                    ->  прикладной обработчик

Отдельно решает вопрос, ради которого затевалось хранилище:
после перезапуска процесса клиент продолжает работать со старым
auth_key, а не идёт на новый DH-обмен.
"""
from __future__ import annotations

import asyncio
import contextlib
import logging
import time
from dataclasses import dataclass
from typing import Awaitable, Callable

from . import crypto
from .crypto import SecurityViolation, ServerSession
from .handshake import HandshakeError, RsaPrivateKey, ServerHandshake, State
from .keystore import DEFAULT_MAX_AGE_SECONDS, AuthKeyStore
from .server import Connection, MTProtoServer, ServerConfig
from .session_registry import SessionRegistry, UnknownAuthKey
from .tl import (
    REQ_DH_PARAMS, REQ_PQ_MULTI, SET_CLIENT_DH_PARAMS, Reader, Writer,
)

log = logging.getLogger("kuotex.mtproto.gateway")

# Транспортный код: сервер не знает такой auth_key.
# Клиент (MTProtoTransport.TransportError.requiresNewAuthKey) поймёт,
# что нужен новый handshake.
TRANSPORT_ERROR_AUTH_KEY_UNKNOWN = -404
TRANSPORT_ERROR_BAD_REQUEST = -400


@dataclass
class GatewayStats:
    handshakes_started: int = 0
    handshakes_completed: int = 0
    handshakes_failed: int = 0
    messages_handled: int = 0
    keys_reused: int = 0
    auth_errors: int = 0


# Прикладной обработчик: (session, msg_id, тело) -> ответ или None
AppHandler = Callable[[ServerSession, int, bytes], Awaitable[bytes | None]]


class MTProtoGateway:
    """Связывает транспорт с хранилищем ключей и прикладной логикой."""

    def __init__(
        self,
        registry: SessionRegistry,
        rsa_keys: list[RsaPrivateKey],
        handler: AppHandler | None = None,
        purge_interval_seconds: float = 3600.0,
    ):
        self.registry = registry
        self.rsa_keys = rsa_keys
        self.handler = handler or self._default_handler
        self.stats = GatewayStats()
        self._purge_interval = purge_interval_seconds
        self._purge_task: asyncio.Task | None = None
        # Незавершённые handshake, по одному на соединение.
        self._handshakes: dict[int, ServerHandshake] = {}

    # ------------------------------------------------------------ lifecycle

    def start_maintenance(self) -> None:
        """Фоновая уборка просроченных ключей."""
        if self._purge_task is None:
            self._purge_task = asyncio.ensure_future(self._purge_loop())

    async def stop(self) -> None:
        if self._purge_task is not None:
            self._purge_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._purge_task
            self._purge_task = None
        self._handshakes.clear()

    async def _purge_loop(self) -> None:
        while True:
            try:
                await asyncio.sleep(self._purge_interval)
                removed = self.registry.purge_expired()
                if removed:
                    log.info("purged %s expired auth keys", removed)
            except asyncio.CancelledError:
                raise
            except Exception:
                log.exception("purge failed")

    # ------------------------------------------------------------ dispatch

    async def on_message(self, conn: Connection, payload: bytes) -> None:
        """Точка входа из транспорта."""
        if len(payload) < 8:
            await self._send_error(conn, TRANSPORT_ERROR_BAD_REQUEST)
            return

        auth_key_id = int.from_bytes(payload[:8], "little", signed=True)

        # auth_key_id == 0 означает незашифрованное сообщение,
        # то есть handshake: ключа пока нет.
        if auth_key_id == 0:
            await self._handle_handshake(conn, payload)
            return

        await self._handle_encrypted(conn, payload, auth_key_id)

    # ------------------------------------------------------------ handshake

    async def _handle_handshake(self, conn: Connection, payload: bytes) -> None:
        body = self._unwrap_unencrypted(payload)
        if body is None:
            await self._send_error(conn, TRANSPORT_ERROR_BAD_REQUEST)
            return

        conn_id = id(conn)
        handshake = self._handshakes.get(conn_id)

        if handshake is None:
            handshake = ServerHandshake(rsa_keys=self.rsa_keys)
            self._handshakes[conn_id] = handshake
            self.stats.handshakes_started += 1

        try:
            response = handshake.handle(body)
        except HandshakeError as exc:
            self.stats.handshakes_failed += 1
            log.debug("handshake failed for %s: %s", conn.peer, exc)
            # Автомат уже в FAILED — соединение переиспользовать нельзя.
            self._handshakes.pop(conn_id, None)
            conn.close()
            return

        await conn.send(self._wrap_unencrypted(response))

        if handshake.state is State.DONE and handshake.auth_key is not None:
            key_id = self.registry.register(
                handshake.auth_key, server_salt=handshake.server_salt
            )
            self.stats.handshakes_completed += 1
            self._handshakes.pop(conn_id, None)
            log.info("auth_key %s created for %s", key_id, conn.peer)

    def _unwrap_unencrypted(self, payload: bytes) -> bytes | None:
        """auth_key_id(0) + msg_id(8) + length(4) + body."""
        if len(payload) < 20:
            return None
        length = int.from_bytes(payload[16:20], "little", signed=True)
        if length < 0 or 20 + length > len(payload):
            return None
        return payload[20:20 + length]

    def _wrap_unencrypted(self, body: bytes) -> bytes:
        now = time.time()
        msg_id = ((int(now) << 32) & ~3) | 1
        return (
            (0).to_bytes(8, "little", signed=True)
            + msg_id.to_bytes(8, "little", signed=True)
            + len(body).to_bytes(4, "little", signed=True)
            + body
        )

    # ------------------------------------------------------------ encrypted

    async def _handle_encrypted(
        self, conn: Connection, payload: bytes, auth_key_id: int
    ) -> None:
        try:
            message = self.registry.unpack(payload)
        except UnknownAuthKey:
            # Ключ не найден: либо истёк, либо создан на другом сервере
            # без общего хранилища. Просим клиента пройти handshake заново.
            self.stats.auth_errors += 1
            await self._send_error(conn, TRANSPORT_ERROR_AUTH_KEY_UNKNOWN)
            conn.close()
            return
        except SecurityViolation as exc:
            # Подделка, повтор или сбитые часы. Молча рвём: подробности
            # клиенту не сообщаем, чтобы не помогать подбору.
            self.stats.auth_errors += 1
            log.debug("security violation from %s: %s", conn.peer, exc)
            conn.close()
            return

        conn.auth_key_id = auth_key_id
        self.stats.keys_reused += 1

        session = self.registry.session_for(auth_key_id, message.session_id)

        # Разворачиваем контейнер, если он есть.
        try:
            inner = crypto.parse_container(message.body)
            items = [(mid, body) for mid, _seq, body in inner]
        except SecurityViolation:
            items = [(message.msg_id, message.body)]

        acks: list[int] = []
        for msg_id, body in items:
            acks.append(msg_id)
            self.stats.messages_handled += 1
            try:
                reply = await self.handler(session, msg_id, body)
            except Exception:
                log.exception("app handler failed for %s", conn.peer)
                continue
            if reply is not None:
                await conn.send(session.pack(reply))

        if acks:
            # Подтверждаем пачкой: отдельный пакет на каждое сообщение —
            # лишний трафик в мобильной сети.
            await conn.send(session.pack(crypto.build_ack(acks),
                                         content_related=False))

    async def _send_error(self, conn: Connection, code: int) -> None:
        """Транспортная ошибка — 4 байта, вне шифрования.

        Ошибку нужно именно доставить: по коду -404 клиент понимает,
        что пора делать новый handshake. Connection.send() только кладёт
        пакет в очередь, а последующий close() отменяет задачу записи —
        поэтому здесь ждём, пока очередь опустеет.
        """
        with contextlib.suppress(Exception):
            await conn.send(code.to_bytes(4, "little", signed=True))
            # Даём _write_loop дописать пакет до закрытия соединения.
            for _ in range(50):
                if conn.queue.empty():
                    break
                await asyncio.sleep(0.01)

    async def _default_handler(
        self, session: ServerSession, msg_id: int, body: bytes
    ) -> bytes | None:
        """Заглушка: эхо. Замени на роутер KuoteX."""
        return body


def build_server(
    registry: SessionRegistry,
    rsa_keys: list[RsaPrivateKey],
    config: ServerConfig | None = None,
    handler: AppHandler | None = None,
) -> tuple[MTProtoServer, MTProtoGateway]:
    """Собирает готовый сервер со всеми слоями."""
    gateway = MTProtoGateway(registry, rsa_keys, handler)
    server = MTProtoServer(config or ServerConfig(), gateway.on_message)
    return server, gateway
