"""Тесты серверной части handshake и шифрования сообщений."""
from __future__ import annotations

import hashlib
import os
import sys
import time

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from cryptography.hazmat.primitives.asymmetric import rsa  # noqa: E402

from mtproto_transport import crypto, tl  # noqa: E402
from mtproto_transport.handshake import (  # noqa: E402
    DH_G, DH_PRIME, HandshakeError, RsaPrivateKey, ServerHandshake, State,
    auth_key_id,
)


# ---------------------------------------------------------------- fixtures

@pytest.fixture(scope="module")
def rsa_key() -> RsaPrivateKey:
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    numbers = private.private_numbers()
    pub = numbers.public_numbers
    return RsaPrivateKey(n=pub.n, e=pub.e, d=numbers.d)


class FakeClient:
    """Клиент, повторяющий логику MTProtoHandshake.kt."""

    def __init__(self, rsa_key: RsaPrivateKey, corrupt_gb: bool = False):
        self.rsa = rsa_key
        self.nonce = os.urandom(16)
        self.server_nonce = b""
        self.new_nonce = os.urandom(32)
        self.b = int.from_bytes(os.urandom(256), "big") % (DH_PRIME - 3) + 2
        self.auth_key: bytes | None = None
        self.corrupt_gb = corrupt_gb

    def req_pq(self) -> bytes:
        w = tl.Writer()
        w.int32(tl.REQ_PQ_MULTI)
        w.raw(self.nonce)
        return w.data()

    def on_res_pq(self, body: bytes) -> bytes:
        r = tl.Reader(body)
        assert r.int32() == tl.RES_PQ
        assert r.raw(16) == self.nonce
        self.server_nonce = r.raw(16)
        pq = int.from_bytes(r.bytes(), "big")
        assert r.int32() == tl.VECTOR
        count = r.int32()
        fingerprint = [r.int64() for _ in range(count)][0]

        p, q = _factorize(pq)

        iw = tl.Writer()
        iw.int32(tl.P_Q_INNER_DATA_DC)
        iw.bytes(_strip(pq.to_bytes(8, "big")))
        iw.bytes(_strip(p.to_bytes(8, "big")))
        iw.bytes(_strip(q.to_bytes(8, "big")))
        iw.raw(self.nonce)
        iw.raw(self.server_nonce)
        iw.raw(self.new_nonce)
        iw.int32(2)
        inner = iw.data()

        data = hashlib.sha1(inner).digest() + inner
        data += os.urandom(255 - len(data))
        encrypted = pow(int.from_bytes(data, "big"), self.rsa.e, self.rsa.n)

        w = tl.Writer()
        w.int32(tl.REQ_DH_PARAMS)
        w.raw(self.nonce)
        w.raw(self.server_nonce)
        w.bytes(_strip(p.to_bytes(8, "big")))
        w.bytes(_strip(q.to_bytes(8, "big")))
        w.int64(fingerprint)
        w.bytes(encrypted.to_bytes(256, "big"))
        return w.data()

    def on_server_dh(self, body: bytes) -> bytes:
        r = tl.Reader(body)
        assert r.int32() == tl.SERVER_DH_PARAMS_OK
        r.raw(16)
        r.raw(16)
        encrypted = r.bytes()

        key, iv = self._temp_key_iv()
        decrypted = tl.ige_decrypt(encrypted, key, iv)
        payload = decrypted[20:]

        ir = tl.Reader(payload)
        assert ir.int32() == tl.SERVER_DH_INNER_DATA
        ir.raw(16)
        ir.raw(16)
        g = ir.int32()
        prime = int.from_bytes(ir.bytes(), "big")
        g_a = int.from_bytes(ir.bytes(), "big")
        assert prime == DH_PRIME and g == DH_G

        g_b = 1 if self.corrupt_gb else pow(DH_G, self.b, DH_PRIME)
        self.auth_key = pow(g_a, self.b, DH_PRIME).to_bytes(256, "big")

        iw = tl.Writer()
        iw.int32(tl.CLIENT_DH_INNER_DATA)
        iw.raw(self.nonce)
        iw.raw(self.server_nonce)
        iw.int64(0)
        iw.bytes(g_b.to_bytes(256, "big"))
        inner = iw.data()

        to_enc = hashlib.sha1(inner).digest() + inner
        to_enc += os.urandom((-len(to_enc)) % 16)
        enc = tl.ige_encrypt(to_enc, key, iv)

        w = tl.Writer()
        w.int32(tl.SET_CLIENT_DH_PARAMS)
        w.raw(self.nonce)
        w.raw(self.server_nonce)
        w.bytes(enc)
        return w.data()

    def on_dh_gen(self, body: bytes) -> bool:
        r = tl.Reader(body)
        assert r.int32() == tl.DH_GEN_OK
        r.raw(16)
        r.raw(16)
        received = r.raw(16)
        aux = hashlib.sha1(self.auth_key).digest()[:8]
        expected = hashlib.sha1(self.new_nonce + bytes([1]) + aux).digest()[4:20]
        return received == expected

    def _temp_key_iv(self):
        nsn = hashlib.sha1(self.new_nonce + self.server_nonce).digest()
        snn = hashlib.sha1(self.server_nonce + self.new_nonce).digest()
        nnn = hashlib.sha1(self.new_nonce + self.new_nonce).digest()
        return nsn + snn[:12], snn[12:20] + nnn + self.new_nonce[:4]


def _strip(b: bytes) -> bytes:
    return b.lstrip(b"\x00") or b"\x00"


def _factorize(pq: int) -> tuple[int, int]:
    """Полларда «ро» — как в MTProtoPq.kt.

    Перебор делителей до sqrt(pq) для 62-битного числа занимает минуты,
    поэтому здесь тот же алгоритм, что и в бою.
    """
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


# ---------------------------------------------------------------- handshake

def test_full_handshake_produces_matching_keys(rsa_key):
    server = ServerHandshake(rsa_keys=[rsa_key])
    client = FakeClient(rsa_key)

    res_pq = server.handle(client.req_pq())
    req_dh = client.on_res_pq(res_pq)
    server_dh = server.handle(req_dh)
    set_client = client.on_server_dh(server_dh)
    dh_gen = server.handle(set_client)

    assert client.on_dh_gen(dh_gen), "new_nonce_hash mismatch"
    assert server.state is State.DONE
    assert server.auth_key == client.auth_key
    assert len(server.auth_key) == 256


def test_derived_key_encrypts_real_messages(rsa_key):
    server = ServerHandshake(rsa_keys=[rsa_key])
    client = FakeClient(rsa_key)
    res_pq = server.handle(client.req_pq())
    server_dh = server.handle(client.on_res_pq(res_pq))
    server.handle(client.on_server_dh(server_dh))

    key = server.auth_key
    session = crypto.ServerSession(auth_key=key, session_id=12345,
                                   server_salt=server.server_salt)

    body = "ответ сервера".encode()
    body += b"\x00" * ((4 - len(body) % 4) % 4)
    envelope = session.pack(body)

    decrypted = crypto.decrypt(key, envelope, from_client=False)
    length = int.from_bytes(decrypted[28:32], "little")
    assert decrypted[32:32 + length] == body


def test_server_rejects_degenerate_gb(rsa_key):
    """Клиент прислал g_b = 1 — общий секрет стал бы предсказуемым."""
    server = ServerHandshake(rsa_keys=[rsa_key])
    client = FakeClient(rsa_key, corrupt_gb=True)

    res_pq = server.handle(client.req_pq())
    server_dh = server.handle(client.on_res_pq(res_pq))
    with pytest.raises(HandshakeError, match="out of range"):
        server.handle(client.on_server_dh(server_dh))
    assert server.state is State.FAILED


def test_server_rejects_wrong_factorization(rsa_key):
    server = ServerHandshake(rsa_keys=[rsa_key])
    client = FakeClient(rsa_key)
    res_pq = server.handle(client.req_pq())
    # Забираем server_nonce из ответа, иначе споткнёмся о более раннюю проверку.
    r = tl.Reader(res_pq)
    r.int32(); r.raw(16)
    client.server_nonce = r.raw(16)

    w = tl.Writer()
    w.int32(tl.REQ_DH_PARAMS)
    w.raw(client.nonce)
    w.raw(client.server_nonce)
    w.bytes((7).to_bytes(1, "big"))       # заведомо неверные p и q
    w.bytes((11).to_bytes(1, "big"))
    w.int64(rsa_key.fingerprint)
    w.bytes(os.urandom(256))

    with pytest.raises(HandshakeError, match="factorization"):
        server.handle(w.data())


def test_server_rejects_unknown_fingerprint(rsa_key):
    other = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    nums = other.private_numbers()
    other_key = RsaPrivateKey(n=nums.public_numbers.n, e=nums.public_numbers.e, d=nums.d)

    server = ServerHandshake(rsa_keys=[rsa_key])
    client = FakeClient(other_key)
    res_pq = server.handle(client.req_pq())
    # Клиент подставит отпечаток из resPQ, но зашифрует ЧУЖИМ ключом,
    # поэтому расшифровка даст мусор и разбор упадёт. Главное — отказ.
    with pytest.raises(HandshakeError):
        server.handle(client.on_res_pq(res_pq))
    assert server.state is State.FAILED


def test_server_rejects_out_of_order_messages(rsa_key):
    server = ServerHandshake(rsa_keys=[rsa_key])
    with pytest.raises(HandshakeError, match="unexpected"):
        server.handle(tl.Writer().int32(tl.SET_CLIENT_DH_PARAMS).data())


def test_nonce_mismatch_is_rejected(rsa_key):
    server = ServerHandshake(rsa_keys=[rsa_key])
    client = FakeClient(rsa_key)
    res_pq = server.handle(client.req_pq())
    r = tl.Reader(res_pq)
    r.int32(); r.raw(16)
    client.server_nonce = r.raw(16)

    w = tl.Writer()
    w.int32(tl.REQ_DH_PARAMS)
    w.raw(os.urandom(16))                 # чужой nonce
    w.raw(client.server_nonce)
    w.bytes(b"\x02")
    w.bytes(b"\x03")
    w.int64(rsa_key.fingerprint)
    w.bytes(os.urandom(256))

    with pytest.raises(HandshakeError, match="nonce mismatch"):
        server.handle(w.data())


# ---------------------------------------------------------------- crypto

def test_kdf_directions_differ():
    key = os.urandom(256)
    msg_key = os.urandom(16)
    assert crypto.derive_key_iv(key, msg_key, True) != \
        crypto.derive_key_iv(key, msg_key, False)


def test_envelope_roundtrip():
    key = os.urandom(256)
    payload = os.urandom(64)
    envelope = crypto.encrypt(key, payload, from_client=True)
    assert crypto.decrypt(key, envelope, from_client=True)[:64] == payload


def test_tampered_message_rejected():
    key = os.urandom(256)
    envelope = bytearray(crypto.encrypt(key, os.urandom(32), from_client=True))
    envelope[30] ^= 1
    with pytest.raises(crypto.SecurityViolation, match="msg_key"):
        crypto.decrypt(key, bytes(envelope), from_client=True)


def test_unknown_auth_key_rejected():
    envelope = crypto.encrypt(os.urandom(256), os.urandom(32), from_client=True)
    with pytest.raises(crypto.SecurityViolation, match="auth_key_id"):
        crypto.decrypt(os.urandom(256), envelope, from_client=True)


def test_padding_is_randomized():
    key = os.urandom(256)
    payload = os.urandom(32)
    a = crypto.encrypt(key, payload, from_client=True)
    b = crypto.encrypt(key, payload, from_client=True)
    assert a != b


def test_session_rejects_replay():
    key = os.urandom(256)
    session = crypto.ServerSession(auth_key=key, session_id=555)

    now = int(time.time())
    msg_id = (now << 32) & ~3
    body = b"test"
    payload = (
        (0).to_bytes(8, "little")
        + (555).to_bytes(8, "little")
        + msg_id.to_bytes(8, "little")
        + (1).to_bytes(4, "little")
        + len(body).to_bytes(4, "little")
        + body
    )
    envelope = crypto.encrypt(key, payload, from_client=True)

    assert session.unpack(envelope).body == body
    with pytest.raises(crypto.SecurityViolation, match="replay"):
        session.unpack(envelope)


def test_session_rejects_odd_client_msg_id():
    key = os.urandom(256)
    session = crypto.ServerSession(auth_key=key, session_id=555)
    now = int(time.time())
    msg_id = ((now << 32) & ~3) | 1        # нечётный — так шлёт сервер
    payload = (
        (0).to_bytes(8, "little")
        + (555).to_bytes(8, "little")
        + msg_id.to_bytes(8, "little")
        + (1).to_bytes(4, "little")
        + (4).to_bytes(4, "little")
        + b"test"
    )
    with pytest.raises(crypto.SecurityViolation, match="parity"):
        session.unpack(crypto.encrypt(key, payload, from_client=True))


def test_session_rejects_stale_msg_id():
    key = os.urandom(256)
    session = crypto.ServerSession(auth_key=key, session_id=555)
    old = int(time.time()) - 10_000
    msg_id = (old << 32) & ~3
    payload = (
        (0).to_bytes(8, "little")
        + (555).to_bytes(8, "little")
        + msg_id.to_bytes(8, "little")
        + (1).to_bytes(4, "little")
        + (4).to_bytes(4, "little")
        + b"test"
    )
    with pytest.raises(crypto.SecurityViolation, match="window"):
        session.unpack(crypto.encrypt(key, payload, from_client=True))


# ---------------------------------------------------------------- containers

def test_container_roundtrip():
    messages = [(4, 1, b"aaaa"), (8, 3, b"bbbbbbbb")]
    parsed = crypto.parse_container(crypto.build_container(messages))
    assert parsed == messages


def test_container_rejects_bad_length():
    w = tl.Writer()
    w.int32(tl.MSG_CONTAINER)
    w.int32(1)
    w.int64(4)
    w.int32(1)
    w.int32(5)                             # не кратно 4
    w.raw(b"aaaa")
    with pytest.raises(crypto.SecurityViolation, match="inner length"):
        crypto.parse_container(w.data())


def test_ack_roundtrip():
    ids = [4, 8, 12]
    assert crypto.parse_ack(crypto.build_ack(ids)) == ids


def test_auth_key_id_matches_spec():
    key = os.urandom(256)
    expected = int.from_bytes(
        hashlib.sha1(key).digest()[12:20], "little", signed=True
    )
    assert auth_key_id(key) == expected
    assert crypto.auth_key_id(key) == expected


# ---------------------------------------------------------------- TL

def test_tl_bytes_roundtrip():
    for size in (0, 1, 100, 253, 254, 255, 1000):
        data = os.urandom(size)
        r = tl.Reader(tl.Writer().bytes(data).data())
        assert r.bytes() == data


def test_tl_numbers_roundtrip():
    w = tl.Writer().int32(-42).int64(-1234567890123).int32(0x7FFFFFFF)
    r = tl.Reader(w.data())
    assert r.int32() == -42
    assert r.int64() == -1234567890123
    assert r.int32() == 0x7FFFFFFF


def test_reader_detects_truncation():
    with pytest.raises(tl.TLError):
        tl.Reader(b"\x01\x02").int32()


def test_ige_reference_vector():
    """Тот же вектор, что проверен на Kotlin-стороне."""
    key = bytes.fromhex(
        "000102030405060708090A0B0C0D0E0F000102030405060708090A0B0C0D0E0F"
    )
    iv = bytes.fromhex(
        "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F"
    )
    ct = tl.ige_encrypt(bytes(32), key, iv)
    assert ct.hex() == (
        "636c6201ca3e54586e9e60841e48d23d27bc72f486d9e09a7a65785e77ecc4b1"
    )
    assert tl.ige_decrypt(ct, key, iv) == bytes(32)
