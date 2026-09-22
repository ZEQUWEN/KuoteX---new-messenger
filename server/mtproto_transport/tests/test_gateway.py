"""Интеграционные тесты шлюза.

Полный путь: obfuscated TCP -> handshake -> хранилище -> сообщения.
Главный сценарий — перезапуск сервера не заставляет клиента делать
новый handshake.
"""
from __future__ import annotations

import asyncio
import contextlib
import hashlib
import os
import sys
import tempfile

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".."))

from cryptography.hazmat.primitives.asymmetric import rsa  # noqa: E402

from mtproto_transport import crypto, tl  # noqa: E402
from mtproto_transport.gateway import (  # noqa: E402
    TRANSPORT_ERROR_AUTH_KEY_UNKNOWN, MTProtoGateway, build_server,
)
from mtproto_transport.handshake import (  # noqa: E402
    DH_G, DH_PRIME, RsaPrivateKey,
)
from mtproto_transport.keystore import (  # noqa: E402
    KeyEncryptor, SqliteKeyStore, auth_key_id_of,
)
from mtproto_transport.obfuscation import frame, make_client_header  # noqa: E402
from mtproto_transport.server import ServerConfig  # noqa: E402
from mtproto_transport.session_registry import SessionRegistry  # noqa: E402

pytestmark = pytest.mark.asyncio


# ---------------------------------------------------------------- fixtures

@pytest.fixture(scope="module")
def rsa_key() -> RsaPrivateKey:
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    numbers = private.private_numbers()
    return RsaPrivateKey(
        n=numbers.public_numbers.n, e=numbers.public_numbers.e, d=numbers.d
    )


@pytest.fixture
def db_path():
    with tempfile.TemporaryDirectory() as tmp:
        yield os.path.join(tmp, "keys.db")


@pytest.fixture
def encryptor():
    return KeyEncryptor(os.urandom(32))


# ---------------------------------------------------------------- client

class GatewayClient:
    """Клиент: обфускация + handshake + шифрованные сообщения."""

    def __init__(self, rsa_key: RsaPrivateKey, dc_id: int = 2):
        self.rsa = rsa_key
        self.header, self.enc, self.dec = make_client_header(dc_id)
        self.reader = None
        self.writer = None
        self.auth_key: bytes | None = None
        self.server_salt = 0
        self.session_id = int.from_bytes(os.urandom(8), "little", signed=True)
        self._msg_seq = 0

    async def connect(self, port: int):
        self.reader, self.writer = await asyncio.open_connection("127.0.0.1", port)
        self.writer.write(self.header)
        await self.writer.drain()

    async def _send_raw(self, payload: bytes):
        self.writer.write(self.enc(frame(payload)))
        await self.writer.drain()

    async def _recv_raw(self) -> bytes:
        first = self.dec(await self.reader.readexactly(1))[0]
        if first < 0x7F:
            words = first
        else:
            words = int.from_bytes(self.dec(await self.reader.readexactly(3)), "little")
        return self.dec(await self.reader.readexactly(words * 4))

    # -------------------------------------------------- handshake

    def _wrap_unencrypted(self, body: bytes) -> bytes:
        import time
        msg_id = (int(time.time()) << 32) & ~3
        return (
            (0).to_bytes(8, "little", signed=True)
            + msg_id.to_bytes(8, "little", signed=True)
            + len(body).to_bytes(4, "little", signed=True)
            + body
        )

    def _unwrap_unencrypted(self, data: bytes) -> bytes:
        length = int.from_bytes(data[16:20], "little", signed=True)
        return data[20:20 + length]

    async def _rpc_plain(self, body: bytes) -> bytes:
        await self._send_raw(self._wrap_unencrypted(body))
        return self._unwrap_unencrypted(await self._recv_raw())

    async def handshake(self):
        nonce = os.urandom(16)
        new_nonce = os.urandom(32)

        w = tl.Writer()
        w.int32(tl.REQ_PQ_MULTI)
        w.raw(nonce)
        res_pq = await self._rpc_plain(w.data())

        r = tl.Reader(res_pq)
        assert r.int32() == tl.RES_PQ
        assert r.raw(16) == nonce
        server_nonce = r.raw(16)
        pq = int.from_bytes(r.bytes(), "big")
        r.int32()
        count = r.int32()
        fingerprint = [r.int64() for _ in range(count)][0]

        p, q = _factorize(pq)

        iw = tl.Writer()
        iw.int32(tl.P_Q_INNER_DATA_DC)
        iw.bytes(_strip(pq.to_bytes(8, "big")))
        iw.bytes(_strip(p.to_bytes(8, "big")))
        iw.bytes(_strip(q.to_bytes(8, "big")))
        iw.raw(nonce)
        iw.raw(server_nonce)
        iw.raw(new_nonce)
        iw.int32(2)
        inner = iw.data()

        data = hashlib.sha1(inner).digest() + inner
        data += os.urandom(255 - len(data))
        encrypted = pow(int.from_bytes(data, "big"), self.rsa.e, self.rsa.n)

        w2 = tl.Writer()
        w2.int32(tl.REQ_DH_PARAMS)
        w2.raw(nonce)
        w2.raw(server_nonce)
        w2.bytes(_strip(p.to_bytes(8, "big")))
        w2.bytes(_strip(q.to_bytes(8, "big")))
        w2.int64(fingerprint)
        w2.bytes(encrypted.to_bytes(256, "big"))
        server_dh = await self._rpc_plain(w2.data())

        key, iv = _temp_key_iv(new_nonce, server_nonce)
        rd = tl.Reader(server_dh)
        assert rd.int32() == tl.SERVER_DH_PARAMS_OK
        rd.raw(16)
        rd.raw(16)
        decrypted = tl.ige_decrypt(rd.bytes(), key, iv)

        ir = tl.Reader(decrypted[20:])
        assert ir.int32() == tl.SERVER_DH_INNER_DATA
        ir.raw(16)
        ir.raw(16)
        ir.int32()
        ir.bytes()
        g_a = int.from_bytes(ir.bytes(), "big")

        b = int.from_bytes(os.urandom(256), "big") % (DH_PRIME - 3) + 2
        g_b = pow(DH_G, b, DH_PRIME)
        self.auth_key = pow(g_a, b, DH_PRIME).to_bytes(256, "big")

        cw = tl.Writer()
        cw.int32(tl.CLIENT_DH_INNER_DATA)
        cw.raw(nonce)
        cw.raw(server_nonce)
        cw.int64(0)
        cw.bytes(g_b.to_bytes(256, "big"))
        client_inner = cw.data()

        to_enc = hashlib.sha1(client_inner).digest() + client_inner
        to_enc += os.urandom((-len(to_enc)) % 16)

        sw = tl.Writer()
        sw.int32(tl.SET_CLIENT_DH_PARAMS)
        sw.raw(nonce)
        sw.raw(server_nonce)
        sw.bytes(tl.ige_encrypt(to_enc, key, iv))
        dh_gen = await self._rpc_plain(sw.data())

        assert tl.Reader(dh_gen).int32() == tl.DH_GEN_OK
        self.server_salt = int.from_bytes(
            bytes(a ^ c for a, c in zip(new_nonce[:8], server_nonce[:8])),
            "little", signed=True,
        )
        return self.auth_key

    # -------------------------------------------------- messages

    def _next_msg_id(self) -> int:
        import time
        self._msg_seq += 1
        return ((int(time.time()) << 32) & ~3) + self._msg_seq * 4

    async def send_encrypted(self, body: bytes, msg_id: int | None = None) -> bytes:
        payload = (
            self.server_salt.to_bytes(8, "little", signed=True)
            + self.session_id.to_bytes(8, "little", signed=True)
            + (msg_id or self._next_msg_id()).to_bytes(8, "little", signed=True)
            + (1).to_bytes(4, "little")
            + len(body).to_bytes(4, "little")
            + body
        )
        await self._send_raw(crypto.encrypt(self.auth_key, payload, from_client=True))
        return await self._recv_raw()

    def decrypt_reply(self, envelope: bytes):
        decrypted = crypto.decrypt(self.auth_key, envelope, from_client=False)
        length = int.from_bytes(decrypted[28:32], "little", signed=True)
        return decrypted[32:32 + length]

    async def close(self):
        if self.writer:
            self.writer.close()
            with contextlib.suppress(Exception):
                await self.writer.wait_closed()


def _strip(b: bytes) -> bytes:
    return b.lstrip(b"\x00") or b"\x00"


def _temp_key_iv(new_nonce: bytes, server_nonce: bytes):
    nsn = hashlib.sha1(new_nonce + server_nonce).digest()
    snn = hashlib.sha1(server_nonce + new_nonce).digest()
    nnn = hashlib.sha1(new_nonce + new_nonce).digest()
    return nsn + snn[:12], snn[12:20] + nnn + new_nonce[:4]


def _factorize(pq: int):
    import math
    import random
    if pq % 2 == 0:
        return 2, pq // 2
    while True:
        y, c, m = (random.randrange(1, pq) for _ in range(3))
        g = r = q_val = 1
        x = ys = 0
        while g == 1:
            x = y
            for _ in range(r):
                y = (y * y + c) % pq
            k = 0
            while k < r and g == 1:
                ys = y
                for _ in range(min(m, r - k)):
                    y = (y * y + c) % pq
                    q_val = q_val * abs(x - y) % pq
                g = math.gcd(q_val, pq)
                k += m
            r *= 2
        if g == pq:
            g = 1
            while g == 1:
                ys = (ys * ys + c) % pq
                g = math.gcd(abs(x - ys), pq)
        if g != pq:
            other = pq // g
            return (g, other) if g < other else (other, g)


async def start_gateway(db_path, encryptor, rsa_key, handler=None):
    registry = SessionRegistry(SqliteKeyStore(db_path, encryptor))
    server, gateway = build_server(
        registry, [rsa_key],
        ServerConfig(host="127.0.0.1", port=0, max_connections_per_ip=500),
        handler,
    )
    await server.start()
    return server, gateway, registry


# ---------------------------------------------------------------- tests

async def test_handshake_then_encrypted_message(db_path, encryptor, rsa_key):
    server, gateway, registry = await start_gateway(db_path, encryptor, rsa_key)

    client = GatewayClient(rsa_key)
    await client.connect(server.port)
    auth_key = await client.handshake()

    assert gateway.stats.handshakes_completed == 1
    # Ключ обязан оказаться в базе, а не только в памяти.
    assert registry._store.load(auth_key_id_of(auth_key)) is not None

    reply = await client.send_encrypted(b"helo")
    assert client.decrypt_reply(reply) == b"helo"

    await client.close()
    await server.stop()
    registry.close()


async def test_key_survives_server_restart(db_path, encryptor, rsa_key):
    """Ради этого всё и делалось: рестарт не заставляет делать handshake."""
    server, gateway, registry = await start_gateway(db_path, encryptor, rsa_key)

    client = GatewayClient(rsa_key)
    await client.connect(server.port)
    auth_key = await client.handshake()
    await client.send_encrypted(b"aaaa")
    await client.close()

    old_port = server.port
    await server.stop()
    registry.close()

    # Полный перезапуск: новый процесс, та же база.
    server2, gateway2, registry2 = await start_gateway(db_path, encryptor, rsa_key)

    client2 = GatewayClient(rsa_key)
    client2.auth_key = auth_key                  # клиент помнит старый ключ
    client2.server_salt = client.server_salt
    client2.session_id = client.session_id
    await client2.connect(server2.port)

    reply = await client2.send_encrypted(b"bbbb")
    assert client2.decrypt_reply(reply) == b"bbbb"
    # Ни одного нового handshake не потребовалось.
    assert gateway2.stats.handshakes_started == 0
    assert gateway2.stats.keys_reused >= 1

    await client2.close()
    await server2.stop()
    registry2.close()


async def test_unknown_key_gets_404(db_path, encryptor, rsa_key):
    """Ключа нет в базе — сервер обязан ответить -404, а не молчать."""
    server, gateway, registry = await start_gateway(db_path, encryptor, rsa_key)

    client = GatewayClient(rsa_key)
    client.auth_key = os.urandom(256)            # ключ, которого сервер не знает
    await client.connect(server.port)

    raw = await client.send_encrypted(b"helo")
    assert len(raw) == 4
    code = int.from_bytes(raw, "little", signed=True)
    assert code == TRANSPORT_ERROR_AUTH_KEY_UNKNOWN

    await client.close()
    await server.stop()
    registry.close()


async def test_replay_blocked_after_reconnect(db_path, encryptor, rsa_key):
    """Переподключение не сбрасывает защиту от повторов."""
    server, gateway, registry = await start_gateway(db_path, encryptor, rsa_key)

    client = GatewayClient(rsa_key)
    await client.connect(server.port)
    await client.handshake()

    fixed_msg_id = client._next_msg_id()
    await client.send_encrypted(b"pay!", msg_id=fixed_msg_id)
    await client.close()

    # Новое TCP-соединение, тот же ключ и тот же msg_id.
    replay = GatewayClient(rsa_key)
    replay.auth_key = client.auth_key
    replay.server_salt = client.server_salt
    replay.session_id = client.session_id
    await replay.connect(server.port)
    await replay._send_raw(crypto.encrypt(
        replay.auth_key,
        replay.server_salt.to_bytes(8, "little", signed=True)
        + replay.session_id.to_bytes(8, "little", signed=True)
        + fixed_msg_id.to_bytes(8, "little", signed=True)
        + (1).to_bytes(4, "little")
        + (4).to_bytes(4, "little")
        + b"pay!",
        from_client=True,
    ))

    # Сервер обязан оборвать соединение, а не обработать повтор.
    with pytest.raises((asyncio.IncompleteReadError, ConnectionError)):
        await asyncio.wait_for(replay._recv_raw(), timeout=3)

    assert registry.stats.replays_blocked >= 1

    await replay.close()
    await server.stop()
    registry.close()


async def test_container_is_unwrapped(db_path, encryptor, rsa_key):
    seen: list[bytes] = []

    async def collector(session, msg_id, body):
        seen.append(body)
        return None

    server, gateway, registry = await start_gateway(
        db_path, encryptor, rsa_key, collector
    )

    client = GatewayClient(rsa_key)
    await client.connect(server.port)
    await client.handshake()

    container = crypto.build_container([
        (client._next_msg_id(), 1, b"aaaa"),
        (client._next_msg_id(), 3, b"bbbb"),
    ])
    await client.send_encrypted(container)

    assert seen == [b"aaaa", b"bbbb"]
    assert gateway.stats.messages_handled == 2

    await client.close()
    await server.stop()
    registry.close()


async def test_two_clients_are_isolated(db_path, encryptor, rsa_key):
    server, gateway, registry = await start_gateway(db_path, encryptor, rsa_key)

    a = GatewayClient(rsa_key)
    b = GatewayClient(rsa_key)
    await a.connect(server.port)
    await b.connect(server.port)
    key_a = await a.handshake()
    key_b = await b.handshake()

    assert key_a != key_b
    assert gateway.stats.handshakes_completed == 2

    assert a.decrypt_reply(await a.send_encrypted(b"aaaa")) == b"aaaa"
    assert b.decrypt_reply(await b.send_encrypted(b"bbbb")) == b"bbbb"

    await a.close()
    await b.close()
    await server.stop()
    registry.close()


async def test_failed_handshake_closes_connection(db_path, encryptor, rsa_key):
    server, gateway, registry = await start_gateway(db_path, encryptor, rsa_key)

    client = GatewayClient(rsa_key)
    await client.connect(server.port)

    # Сразу шлём шаг 3, пропустив первые два.
    w = tl.Writer()
    w.int32(tl.SET_CLIENT_DH_PARAMS)
    w.raw(os.urandom(16))
    w.raw(os.urandom(16))
    w.bytes(os.urandom(32))
    await client._send_raw(client._wrap_unencrypted(w.data()))

    with pytest.raises((asyncio.IncompleteReadError, ConnectionError)):
        await asyncio.wait_for(client._recv_raw(), timeout=3)

    assert gateway.stats.handshakes_failed == 1

    await client.close()
    await server.stop()
    registry.close()


async def test_many_clients_handshake_concurrently(db_path, encryptor, rsa_key):
    server, gateway, registry = await start_gateway(db_path, encryptor, rsa_key)

    clients = [GatewayClient(rsa_key) for _ in range(10)]
    await asyncio.gather(*(c.connect(server.port) for c in clients))
    await asyncio.gather(*(c.handshake() for c in clients))

    assert gateway.stats.handshakes_completed == 10
    assert registry._store.count() == 10

    replies = await asyncio.gather(
        *(c.send_encrypted(f"msg-{i}".encode().ljust(8, b"\x00"))
          for i, c in enumerate(clients))
    )
    for i, (client, raw) in enumerate(zip(clients, replies)):
        assert client.decrypt_reply(raw) == f"msg-{i}".encode().ljust(8, b"\x00")

    await asyncio.gather(*(c.close() for c in clients))
    await server.stop()
    registry.close()


async def test_revoked_key_is_rejected(db_path, encryptor, rsa_key):
    """Logout: после отзыва ключа сервер отвечает -404."""
    server, gateway, registry = await start_gateway(db_path, encryptor, rsa_key)

    client = GatewayClient(rsa_key)
    await client.connect(server.port)
    auth_key = await client.handshake()
    await client.send_encrypted(b"aaaa")
    # Первое сообщение порождает ещё и msgs_ack — вычитываем его,
    # иначе он будет принят за ответ на следующий запрос.
    await asyncio.wait_for(client._recv_raw(), timeout=3)

    registry.forget(auth_key_id_of(auth_key))

    raw = await client.send_encrypted(b"bbbb")
    assert len(raw) == 4, f"expected 4-byte transport error, got {len(raw)}"
    assert int.from_bytes(raw, "little", signed=True) == \
        TRANSPORT_ERROR_AUTH_KEY_UNKNOWN

    await client.close()
    await server.stop()
    registry.close()
