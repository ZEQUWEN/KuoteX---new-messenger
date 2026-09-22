"""Тесты MTProto-прокси.

Поднимают настоящий сервер-«дата-центр», прокси перед ним и клиента,
и проверяют сквозной проход, а также поведение при сбоях.
"""
from __future__ import annotations

import asyncio
import contextlib
import hashlib
import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".."))

from mtproto_transport.obfuscation import (  # noqa: E402
    ABRIDGED_TAG, FORBIDDEN_PREFIXES, HEADER_SIZE, _ctr, frame,
)
from mtproto_transport.proxy import (  # noqa: E402
    MTProtoProxy, ProxyAuthError, ProxyConfig, accept_with_secret,
    generate_secret,
)
from mtproto_transport.server import (  # noqa: E402
    Connection, MTProtoServer, ServerConfig,
)

pytestmark = pytest.mark.asyncio


# ---------------------------------------------------------------- helpers

def make_client_header_with_secret(dc_id: int, secret: bytes | None):
    """Клиентский заголовок, согласованный с secret-режимом прокси."""
    while True:
        buf = bytearray(os.urandom(HEADER_SIZE))
        if buf[0] == 0xEF:
            continue
        if any(bytes(buf).startswith(p) for p in FORBIDDEN_PREFIXES):
            continue
        if buf[4:8] == b"\x00\x00\x00\x00":
            continue
        break

    buf[56:60] = ABRIDGED_TAG
    buf[60:62] = dc_id.to_bytes(2, "little", signed=True)

    enc_key, enc_iv = bytes(buf[8:40]), bytes(buf[40:56])
    reversed_block = bytes(buf[8:56])[::-1]
    dec_key, dec_iv = reversed_block[:32], reversed_block[32:48]

    if secret:
        enc_key = hashlib.sha256(enc_key + secret).digest()
        dec_key = hashlib.sha256(dec_key + secret).digest()

    encryptor = _ctr(enc_key, enc_iv)
    decryptor = _ctr(dec_key, dec_iv)

    encrypted = encryptor.update(bytes(buf))
    header = bytes(buf[:56]) + encrypted[56:64]
    return header, encryptor.update, decryptor.update


class ProxyClient:
    def __init__(self, dc_id: int = 2, secret: bytes | None = None):
        self.header, self.enc, self.dec = make_client_header_with_secret(dc_id, secret)
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
            with contextlib.suppress(Exception):
                await self.writer.wait_closed()


async def start_datacenter(handler=None) -> MTProtoServer:
    """Настоящий сервер, играющий роль дата-центра."""

    async def echo(conn: Connection, payload: bytes):
        await conn.send(payload)

    server = MTProtoServer(
        ServerConfig(host="127.0.0.1", port=0, max_connections_per_ip=1000),
        handler or echo,
    )
    await server.start()
    return server


async def start_proxy(dc_port: int, secret: str = "") -> MTProtoProxy:
    proxy = MTProtoProxy(ProxyConfig(
        host="127.0.0.1",
        port=0,
        secret=secret,
        datacenters={2: ("127.0.0.1", dc_port)},
        max_connections_per_ip=1000,
    ))
    await proxy.start()
    return proxy


# ---------------------------------------------------------------- secret

async def test_generated_secret_is_16_bytes():
    secret = generate_secret()
    assert len(bytes.fromhex(secret)) == 16
    assert generate_secret() != generate_secret()


async def test_rejects_invalid_secret_length():
    with pytest.raises(ValueError, match="16 bytes"):
        MTProtoProxy(ProxyConfig(secret="aabb"))


async def test_accept_with_correct_secret():
    secret = bytes.fromhex(generate_secret())
    header, _, _ = make_client_header_with_secret(2, secret)
    stream = accept_with_secret(header, secret)
    assert stream.dc_id == 2


async def test_rejects_wrong_secret():
    """Клиент с чужим секретом не должен пройти."""
    right = bytes.fromhex(generate_secret())
    wrong = bytes.fromhex(generate_secret())
    header, _, _ = make_client_header_with_secret(2, wrong)
    with pytest.raises(ProxyAuthError):
        accept_with_secret(header, right)


async def test_rejects_http_probe():
    secret = bytes.fromhex(generate_secret())
    with pytest.raises(Exception):
        accept_with_secret(b"GET / HTTP/1.1\r\n".ljust(64, b"\x00"), secret)


# ---------------------------------------------------------------- e2e

async def test_end_to_end_through_proxy():
    """Клиент -> прокси -> ДЦ и обратно."""
    dc = await start_datacenter()
    secret = generate_secret()
    proxy = await start_proxy(dc.port, secret)

    client = ProxyClient(secret=bytes.fromhex(secret))
    await client.connect(proxy.port)

    for size in (4, 64, 1024, 16384):
        payload = os.urandom(size)
        await client.send(payload)
        assert await client.recv() == payload

    await client.close()
    await proxy.stop()
    await dc.stop()


async def test_large_payload_uses_extended_frame():
    dc = await start_datacenter()
    secret = generate_secret()
    proxy = await start_proxy(dc.port, secret)

    client = ProxyClient(secret=bytes.fromhex(secret))
    await client.connect(proxy.port)

    payload = os.urandom(256 * 1024)
    await client.send(payload)
    assert await client.recv() == payload

    await client.close()
    await proxy.stop()
    await dc.stop()


async def test_many_clients_through_proxy():
    dc = await start_datacenter()
    secret = generate_secret()
    proxy = await start_proxy(dc.port, secret)

    clients = [ProxyClient(secret=bytes.fromhex(secret)) for _ in range(40)]
    await asyncio.gather(*(c.connect(proxy.port) for c in clients))

    async def exchange(c: ProxyClient, idx: int):
        payload = f"client-{idx:04d}".encode().ljust(32, b"\x00")
        await c.send(payload)
        assert await c.recv() == payload

    await asyncio.gather(*(exchange(c, i) for i, c in enumerate(clients)))
    assert proxy.stats.accepted == 40

    await asyncio.gather(*(c.close() for c in clients))
    await proxy.stop()
    await dc.stop()


async def test_wrong_secret_connection_is_dropped():
    dc = await start_datacenter()
    secret = generate_secret()
    proxy = await start_proxy(dc.port, secret)

    intruder = ProxyClient(secret=bytes.fromhex(generate_secret()))
    await intruder.connect(proxy.port)
    await asyncio.sleep(0.3)

    assert proxy.stats.rejected_auth >= 1

    # Легитимный клиент по-прежнему обслуживается.
    good = ProxyClient(secret=bytes.fromhex(secret))
    await good.connect(proxy.port)
    await good.send(b"okok")
    assert await good.recv() == b"okok"

    await good.close()
    await intruder.close()
    await proxy.stop()
    await dc.stop()


async def test_proxy_does_not_see_plaintext():
    """Прокси обязан оставаться слепым к содержимому.

    Он снимает обфускацию, но полезная нагрузка для него — просто байты
    конверта MTProto, которые он не расшифровывает.
    """
    seen: list[bytes] = []

    async def spy(conn: Connection, payload: bytes):
        seen.append(payload)
        await conn.send(payload)

    dc = await start_datacenter(spy)
    secret = generate_secret()
    proxy = await start_proxy(dc.port, secret)

    client = ProxyClient(secret=bytes.fromhex(secret))
    await client.connect(proxy.port)

    marker = b"KUOTEX_PRIVATE_MESSAGE_MARKER___"
    await client.send(marker)
    await client.recv()

    # ДЦ получил ровно то, что отправил клиент — прокси не исказил.
    assert seen and seen[0] == marker

    await client.close()
    await proxy.stop()
    await dc.stop()


async def test_handshake_timeout_drops_silent_client():
    dc = await start_datacenter()
    proxy = MTProtoProxy(ProxyConfig(
        host="127.0.0.1", port=0, secret=generate_secret(),
        datacenters={2: ("127.0.0.1", dc.port)},
        handshake_timeout=0.3,
    ))
    await proxy.start()

    reader, writer = await asyncio.open_connection("127.0.0.1", proxy.port)
    await asyncio.sleep(0.8)
    assert proxy.stats.active == 0

    writer.close()
    await proxy.stop()
    await dc.stop()


async def test_unreachable_datacenter_is_handled():
    """ДЦ лежит — прокси не должен падать."""
    secret = generate_secret()
    proxy = MTProtoProxy(ProxyConfig(
        host="127.0.0.1", port=0, secret=secret,
        datacenters={2: ("127.0.0.1", 1)},     # заведомо закрытый порт
        connect_timeout=1.0,
    ))
    await proxy.start()

    client = ProxyClient(secret=bytes.fromhex(secret))
    await client.connect(proxy.port)
    await asyncio.sleep(0.5)

    assert proxy.stats.upstream_failures >= 1
    assert proxy.stats.active == 0

    await client.close()
    await proxy.stop()


async def test_client_disconnect_frees_slot():
    dc = await start_datacenter()
    secret = generate_secret()
    proxy = await start_proxy(dc.port, secret)

    clients = [ProxyClient(secret=bytes.fromhex(secret)) for _ in range(10)]
    await asyncio.gather(*(c.connect(proxy.port) for c in clients))
    await asyncio.sleep(0.3)
    assert proxy.stats.active == 10

    for c in clients:
        c.writer.transport.abort()          # резкий обрыв
    await asyncio.sleep(0.5)

    assert proxy.stats.active == 0, "connections leaked"
    await proxy.stop()
    await dc.stop()


async def test_per_ip_limit():
    dc = await start_datacenter()
    secret = generate_secret()
    proxy = MTProtoProxy(ProxyConfig(
        host="127.0.0.1", port=0, secret=secret,
        datacenters={2: ("127.0.0.1", dc.port)},
        max_connections_per_ip=3,
    ))
    await proxy.start()

    clients = [ProxyClient(secret=bytes.fromhex(secret)) for _ in range(5)]
    for c in clients:
        await c.connect(proxy.port)
    await asyncio.sleep(0.4)

    assert proxy.stats.rejected_limit >= 1
    assert proxy.stats.active <= 3

    for c in clients:
        await c.close()
    await proxy.stop()
    await dc.stop()


async def test_stats_count_traffic():
    dc = await start_datacenter()
    secret = generate_secret()
    proxy = await start_proxy(dc.port, secret)

    client = ProxyClient(secret=bytes.fromhex(secret))
    await client.connect(proxy.port)
    await client.send(os.urandom(256))
    await client.recv()

    assert proxy.stats.bytes_client_to_dc >= 256
    assert proxy.stats.bytes_dc_to_client >= 256

    await client.close()
    await proxy.stop()
    await dc.stop()


async def test_proxy_stops_quickly():
    """Остановка не должна висеть до idle_timeout."""
    import time

    dc = await start_datacenter()
    secret = generate_secret()
    proxy = await start_proxy(dc.port, secret)

    clients = [ProxyClient(secret=bytes.fromhex(secret)) for _ in range(5)]
    await asyncio.gather(*(c.connect(proxy.port) for c in clients))
    await asyncio.sleep(0.3)

    started = time.monotonic()
    await proxy.stop()
    elapsed = time.monotonic() - started

    assert elapsed < 3.0, f"stop took {elapsed:.1f}s"

    for c in clients:
        await c.close()
    await dc.stop()
