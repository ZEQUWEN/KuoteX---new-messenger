"""Тесты транспортного сервера KuoteX.

Проверяют не только «работает», но и поведение при сбоях мобильной сети:
резкий обрыв, медленный клиент, флуд, мусорный трафик от сканеров.
"""
from __future__ import annotations

import asyncio
import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from mtproto_transport.obfuscation import (  # noqa: E402
    ObfuscationError, accept_header, frame, make_client_header,
)
from mtproto_transport.server import (  # noqa: E402
    Connection, MTProtoServer, ServerConfig,
)

pytestmark = pytest.mark.asyncio


# ---------------------------------------------------------------- helpers

class Client:
    """Минимальный клиент obfuscated2 для тестов."""

    def __init__(self, dc_id: int = 2):
        self.header, self.enc, self.dec = make_client_header(dc_id)
        self.reader = None
        self.writer = None

    async def connect(self, port: int):
        self.reader, self.writer = await asyncio.open_connection("127.0.0.1", port)
        self.writer.write(self.header)
        await self.writer.drain()

    async def send(self, payload: bytes):
        self.writer.write(self.enc(frame(payload)))
        await self.writer.drain()

    async def recv(self) -> bytes:
        first = self.dec(await self.reader.readexactly(1))[0]
        if first < 0x7F:
            words = first
        else:
            words = int.from_bytes(self.dec(await self.reader.readexactly(3)), "little")
        return self.dec(await self.reader.readexactly(words * 4))

    async def close(self):
        if self.writer:
            self.writer.close()
            with __import__("contextlib").suppress(Exception):
                await self.writer.wait_closed()


async def start_server(handler=None, **cfg_kwargs):
    async def echo(conn: Connection, payload: bytes):
        await conn.send(payload)

    config = ServerConfig(host="127.0.0.1", port=0, **cfg_kwargs)
    server = MTProtoServer(config, handler or echo)
    # start() уже начинает принимать соединения — serve_forever() здесь
    # не нужен и приводит к повторному ожиданию корутины.
    await server.start()
    return server


# ---------------------------------------------------------------- obfuscation

async def test_header_roundtrip():
    header, enc, dec = make_client_header(dc_id=4)
    stream = accept_header(header)
    assert stream.dc_id == 4

    # Клиент -> сервер
    payload = b"hello world!"
    assert stream.decrypt(enc(payload)) == payload
    # Сервер -> клиент
    reply = b"server reply"
    assert dec(stream.encrypt(reply)) == reply


async def test_header_rejects_http_scanner():
    """Сканер шлёт обычный HTTP — сервер обязан его отбить."""
    bogus = b"GET / HTTP/1.1\r\nHost: x\r\n\r\n".ljust(64, b"\x00")
    with pytest.raises(ObfuscationError):
        accept_header(bogus)


async def test_header_rejects_tls_probe():
    bogus = bytes([0x16, 0x03, 0x01]) + os.urandom(61)
    with pytest.raises(ObfuscationError):
        accept_header(bogus)


async def test_header_rejects_wrong_size():
    with pytest.raises(ObfuscationError):
        accept_header(os.urandom(32))


async def test_headers_are_unique():
    """Одинаковые заголовки выдали бы клиента статистическому анализу."""
    seen = {make_client_header()[0] for _ in range(200)}
    assert len(seen) == 200


# ---------------------------------------------------------------- server

async def test_echo_roundtrip():
    server = await start_server()
    client = Client()
    await client.connect(server.port)

    for size in (4, 64, 1024, 8192):
        payload = os.urandom(size)
        await client.send(payload)
        assert await client.recv() == payload

    await client.close()
    await server.stop()


async def test_large_frame_extended_length():
    """Пакеты >= 0x7F слов используют расширенный заголовок длины."""
    server = await start_server()
    client = Client()
    await client.connect(server.port)

    payload = os.urandom(256 * 1024)
    await client.send(payload)
    assert await client.recv() == payload

    await client.close()
    await server.stop()


async def test_many_concurrent_clients():
    """Пул соединений не должен виснуть под нагрузкой.

    Лимит на IP поднят: в тесте все 100 клиентов идут с 127.0.0.1,
    тогда как в бою это 100 разных абонентов.
    """
    server = await start_server(max_connections_per_ip=200)
    clients = [Client() for _ in range(100)]
    await asyncio.gather(*(c.connect(server.port) for c in clients))

    async def exchange(c: Client, idx: int):
        payload = f"client-{idx:04d}".encode().ljust(16, b"\x00")
        await c.send(payload)
        assert await c.recv() == payload

    await asyncio.gather(*(exchange(c, i) for i, c in enumerate(clients)))
    assert server.stats.active == 100

    await asyncio.gather(*(c.close() for c in clients))
    await asyncio.sleep(0.2)
    await server.stop()


async def test_abrupt_disconnect_frees_slot():
    """Резкий обрыв (потеря сети) не должен оставлять висящее соединение."""
    server = await start_server()
    clients = [Client() for _ in range(20)]
    await asyncio.gather(*(c.connect(server.port) for c in clients))
    await asyncio.sleep(0.2)
    assert server.stats.active == 20

    # Рвём соединения без всякого прощания.
    for c in clients:
        c.writer.transport.abort()

    await asyncio.sleep(0.5)
    assert server.stats.active == 0, "connections leaked after abrupt disconnect"
    await server.stop()


async def test_per_ip_limit():
    """Флуд с одного адреса не должен исчерпать дескрипторы."""
    server = await start_server(max_connections_per_ip=5)
    good = [Client() for _ in range(5)]
    await asyncio.gather(*(c.connect(server.port) for c in good))
    await asyncio.sleep(0.2)

    extra = Client()
    await extra.connect(server.port)
    await asyncio.sleep(0.3)

    assert server.stats.rejected_limit >= 1
    assert server.stats.active <= 5

    await asyncio.gather(*(c.close() for c in good))
    await extra.close()
    await server.stop()


async def test_handshake_timeout_drops_silent_client():
    """Клиент подключился и молчит — типичное поведение сканера портов."""
    server = await start_server(handshake_timeout=0.3)
    reader, writer = await asyncio.open_connection("127.0.0.1", server.port)
    await asyncio.sleep(0.8)

    assert server.stats.timeouts >= 1
    assert server.stats.active == 0

    writer.close()
    await server.stop()


async def test_garbage_header_rejected():
    """Мусор вместо заголовка — соединение закрывается, сервер жив."""
    server = await start_server()
    reader, writer = await asyncio.open_connection("127.0.0.1", server.port)
    writer.write(b"POST /api HTTP/1.1\r\n".ljust(64, b"\x00"))
    await writer.drain()
    await asyncio.sleep(0.3)

    assert server.stats.rejected_bad_header >= 1
    writer.close()

    # Сервер продолжает обслуживать нормальных клиентов.
    client = Client()
    await client.connect(server.port)
    await client.send(b"ok\x00\x00")
    assert await client.recv() == b"ok\x00\x00"
    await client.close()
    await server.stop()


async def test_handler_exception_does_not_kill_connection():
    """Ошибка прикладного слоя не должна рвать соединение."""
    calls = {"n": 0}

    async def flaky(conn: Connection, payload: bytes):
        calls["n"] += 1
        if calls["n"] == 1:
            raise RuntimeError("boom")
        await conn.send(payload)

    server = await start_server(flaky)
    client = Client()
    await client.connect(server.port)

    await client.send(b"aaaa")       # обработчик упадёт
    await asyncio.sleep(0.2)
    await client.send(b"bbbb")       # соединение должно быть живо
    assert await client.recv() == b"bbbb"

    await client.close()
    await server.stop()


async def test_stats_counters():
    server = await start_server()
    client = Client()
    await client.connect(server.port)
    await client.send(b"test")
    await client.recv()

    assert server.stats.total_accepted == 1
    assert server.stats.bytes_in > 0
    assert server.stats.bytes_out > 0

    await client.close()
    await server.stop()


async def test_server_stop_closes_everything():
    server = await start_server()
    clients = [Client() for _ in range(10)]
    await asyncio.gather(*(c.connect(server.port) for c in clients))
    await asyncio.sleep(0.2)

    await server.stop()
    assert server.stats.active == 0

    for c in clients:
        await c.close()


# ---------------------------------------------------------------- framing

async def test_frame_lengths():
    assert frame(b"\x00" * 4)[0] == 1
    big = frame(b"\x00" * 4 * 200)
    assert big[0] == 0x7F
    assert int.from_bytes(big[1:4], "little") == 200


async def test_frame_rejects_unaligned():
    with pytest.raises(ValueError):
        frame(b"abc")

