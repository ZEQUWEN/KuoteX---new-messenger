"""Серверная сторона создания auth_key (MTProto 2.0).

Зеркало клиентского MTProtoHandshake.kt: принимает req_pq_multi,
отдаёт resPQ, разбирает RSA-конверт и завершает DH.

Как и на клиенте, класс не знает про сокеты — только байты внутрь и
байты наружу. Благодаря этому весь протокол тестируется без сети.

Референс: https://core.telegram.org/mtproto/auth_key
"""
from __future__ import annotations

import hashlib
import os
import secrets
from dataclasses import dataclass, field
from enum import Enum, auto

from .tl import (
    CLIENT_DH_INNER_DATA, DH_GEN_OK, P_Q_INNER_DATA_DC, REQ_DH_PARAMS,
    REQ_PQ_MULTI, RES_PQ, SERVER_DH_INNER_DATA, SERVER_DH_PARAMS_OK,
    SET_CLIENT_DH_PARAMS, VECTOR, Reader, Writer, ige_decrypt, ige_encrypt,
)

# Та же группа, что и в MTProtoDh.kt — иначе стороны не сойдутся.
DH_PRIME = int(
    "C71CAEB9C6B1C9048E6C522F70F13F73980D40238E3E21C14934D037563D930F"
    "48198A0AA7C14058229493D22530F4DBFA336F6E0AC925139543AED44CCE7C37"
    "20FD51F69458705AC68CD4FE6B6B13ABDC9746512969328454F18FAF8C595F64"
    "2477FE96BB2A941D5BCD1D4AC8CC49880708FA9B378E3C4F3A9060BEE67CF9A4"
    "A4A695811051907E162753B56B0F6B410DBA74D8A84B2A14B3144E0EF1284754"
    "FD17ED950D5965B4B9DD46582DB1178D169C6BC465B0D6FF9CA3928FEF5B9AE4"
    "E418FC15E83EBEA0F87FA9FF5EED70050DED2849F47BF959D956850CE929851F"
    "0D8115F635B105EE2E4E15D04B2454BF6F4FADF034B10403119CD8E3B92FCC5B",
    16,
)
DH_G = 3
_MIN_BOUND = 1 << (2048 - 64)


class HandshakeError(Exception):
    """Нарушение протокола. Соединение обязано быть разорвано."""


class State(Enum):
    NEW = auto()
    WAIT_DH_PARAMS = auto()
    WAIT_CLIENT_DH = auto()
    DONE = auto()
    FAILED = auto()


@dataclass
class RsaPrivateKey:
    """Приватный RSA-ключ сервера.

    Отпечаток считается так же, как на клиенте: младшие 8 байт
    SHA1(TL(n) + TL(e)).
    """

    n: int
    e: int
    d: int

    @property
    def fingerprint(self) -> int:
        w = Writer()
        w.bytes(_strip(self.n.to_bytes((self.n.bit_length() + 7) // 8, "big")))
        w.bytes(_strip(self.e.to_bytes((self.e.bit_length() + 7) // 8, "big")))
        digest = hashlib.sha1(w.data()).digest()
        return int.from_bytes(digest[12:20], "little", signed=True)

    def decrypt_raw(self, data: bytes) -> bytes:
        """Сырое RSA без паддинга — как задано в спецификации."""
        c = int.from_bytes(data, "big")
        if c >= self.n:
            raise HandshakeError("RSA ciphertext >= modulus")
        m = pow(c, self.d, self.n)
        return m.to_bytes(256, "big")


def _strip(b: bytes) -> bytes:
    return b.lstrip(b"\x00") or b"\x00"


def _factorize_check(pq: int, p: int, q: int) -> None:
    if p * q != pq or p >= q or p <= 1:
        raise HandshakeError("client sent wrong factorization")


@dataclass
class ServerHandshake:
    """Конечный автомат серверной стороны."""

    rsa_keys: list[RsaPrivateKey]
    dc_id: int = 2

    state: State = State.NEW
    auth_key: bytes | None = None
    server_salt: int = 0

    _nonce: bytes = b""
    _server_nonce: bytes = b""
    _new_nonce: bytes = b""
    _a: int = 0
    _p: int = 0
    _q: int = 0
    _pq: int = 0

    # ------------------------------------------------------------ step 1

    def handle_req_pq(self, body: bytes) -> bytes:
        """req_pq_multi -> resPQ."""
        if self.state is not State.NEW:
            self._fail(f"unexpected req_pq in state {self.state}")

        r = Reader(body)
        if r.int32() != REQ_PQ_MULTI:
            self._fail("expected req_pq_multi")
        self._nonce = r.raw(16)
        self._server_nonce = os.urandom(16)

        # pq = p*q, где p и q простые около 2^31. Клиент обязан их
        # разложить: это доказательство работы против дешёвого DoS.
        self._p = _random_prime_31()
        self._q = _random_prime_31()
        while self._q == self._p:
            self._q = _random_prime_31()
        if self._p > self._q:
            self._p, self._q = self._q, self._p
        self._pq = self._p * self._q

        w = Writer()
        w.int32(RES_PQ)
        w.raw(self._nonce)
        w.raw(self._server_nonce)
        w.bytes(_strip(self._pq.to_bytes(8, "big")))
        w.int32(VECTOR)
        w.int32(len(self.rsa_keys))
        for key in self.rsa_keys:
            w.int64(key.fingerprint)

        self.state = State.WAIT_DH_PARAMS
        return w.data()

    # ------------------------------------------------------------ step 2

    def handle_req_dh_params(self, body: bytes) -> bytes:
        """req_DH_params -> server_DH_params_ok."""
        if self.state is not State.WAIT_DH_PARAMS:
            self._fail(f"unexpected req_DH_params in state {self.state}")

        r = Reader(body)
        if r.int32() != REQ_DH_PARAMS:
            self._fail("expected req_DH_params")
        if r.raw(16) != self._nonce:
            self._fail("nonce mismatch")
        if r.raw(16) != self._server_nonce:
            self._fail("server_nonce mismatch")

        p = int.from_bytes(r.bytes(), "big")
        q = int.from_bytes(r.bytes(), "big")
        _factorize_check(self._pq, p, q)

        fingerprint = r.int64()
        key = next((k for k in self.rsa_keys if k.fingerprint == fingerprint), None)
        if key is None:
            self._fail("unknown RSA fingerprint")

        encrypted = r.bytes()
        if len(encrypted) != 256:
            self._fail("bad encrypted_data length")

        # Расшифровываем и проверяем SHA1: без этого клиент мог бы
        # прислать мусор, а мы бы приняли его за валидные данные.
        plain = key.decrypt_raw(encrypted)[1:]      # снимаем ведущий ноль
        expected_hash, payload = plain[:20], plain[20:]

        ir = Reader(payload)
        if ir.int32() != P_Q_INNER_DATA_DC:
            self._fail("expected p_q_inner_data_dc")
        ir.bytes()                                   # pq
        ir.bytes()                                   # p
        ir.bytes()                                   # q
        if ir.raw(16) != self._nonce:
            self._fail("inner nonce mismatch")
        if ir.raw(16) != self._server_nonce:
            self._fail("inner server_nonce mismatch")
        self._new_nonce = ir.raw(32)
        ir.int32()                                   # dc_id

        inner_len = ir.offset
        if not secrets.compare_digest(
            expected_hash, hashlib.sha1(payload[:inner_len]).digest()
        ):
            self._fail("p_q_inner_data hash mismatch")

        # Готовим server_DH_inner_data.
        self._a = secrets.randbelow(DH_PRIME - 3) + 2
        g_a = pow(DH_G, self._a, DH_PRIME)
        _check_dh_value(g_a)

        iw = Writer()
        iw.int32(SERVER_DH_INNER_DATA)
        iw.raw(self._nonce)
        iw.raw(self._server_nonce)
        iw.int32(DH_G)
        iw.bytes(DH_PRIME.to_bytes(256, "big"))
        iw.bytes(g_a.to_bytes(256, "big"))
        iw.int32(int(__import__("time").time()))
        inner = iw.data()

        to_encrypt = hashlib.sha1(inner).digest() + inner
        to_encrypt += os.urandom((-len(to_encrypt)) % 16)
        aes_key, aes_iv = self._temp_key_iv()
        encrypted_answer = ige_encrypt(to_encrypt, aes_key, aes_iv)

        w = Writer()
        w.int32(SERVER_DH_PARAMS_OK)
        w.raw(self._nonce)
        w.raw(self._server_nonce)
        w.bytes(encrypted_answer)

        self.state = State.WAIT_CLIENT_DH
        return w.data()

    # ------------------------------------------------------------ step 3

    def handle_set_client_dh_params(self, body: bytes) -> bytes:
        """set_client_DH_params -> dh_gen_ok."""
        if self.state is not State.WAIT_CLIENT_DH:
            self._fail(f"unexpected set_client_DH_params in state {self.state}")

        r = Reader(body)
        if r.int32() != SET_CLIENT_DH_PARAMS:
            self._fail("expected set_client_DH_params")
        if r.raw(16) != self._nonce:
            self._fail("nonce mismatch")
        if r.raw(16) != self._server_nonce:
            self._fail("server_nonce mismatch")

        encrypted = r.bytes()
        if len(encrypted) % 16:
            self._fail("encrypted_data not aligned")

        aes_key, aes_iv = self._temp_key_iv()
        decrypted = ige_decrypt(encrypted, aes_key, aes_iv)
        expected_hash, payload = decrypted[:20], decrypted[20:]

        ir = Reader(payload)
        if ir.int32() != CLIENT_DH_INNER_DATA:
            self._fail("expected client_DH_inner_data")
        if ir.raw(16) != self._nonce:
            self._fail("inner nonce mismatch")
        if ir.raw(16) != self._server_nonce:
            self._fail("inner server_nonce mismatch")
        ir.int64()                                   # retry_id
        g_b_bytes = ir.bytes()
        inner_len = ir.offset

        if not secrets.compare_digest(
            expected_hash, hashlib.sha1(payload[:inner_len]).digest()
        ):
            self._fail("client_DH_inner_data hash mismatch")

        g_b = int.from_bytes(g_b_bytes, "big")
        # Проверка обязательна: без неё клиент подсунет g_b = 1
        # и общий секрет станет предсказуемым.
        # Оборачиваем, чтобы автомат гарантированно перешёл в FAILED:
        # соединение после такой попытки переиспользовать нельзя.
        try:
            _check_dh_value(g_b)
        except HandshakeError as exc:
            self._fail(str(exc))

        self.auth_key = pow(g_b, self._a, DH_PRIME).to_bytes(256, "big")
        self.server_salt = int.from_bytes(
            bytes(a ^ b for a, b in zip(self._new_nonce[:8], self._server_nonce[:8])),
            "little",
            signed=True,
        )

        w = Writer()
        w.int32(DH_GEN_OK)
        w.raw(self._nonce)
        w.raw(self._server_nonce)
        w.raw(_new_nonce_hash(self._new_nonce, 1, self.auth_key))

        self.state = State.DONE
        self._a = 0                                  # секрет больше не нужен
        return w.data()

    # ------------------------------------------------------------ helpers

    def handle(self, body: bytes) -> bytes:
        """Маршрутизирует запрос по конструктору.

        Любая ошибка внутри обработчиков переводит автомат в FAILED.
        Это важно: часть проверок (RSA, разбор TL, срез буфера) кидает
        исключения в обход _fail(), и без этой обёртки клиент мог бы
        продолжить handshake в том же соединении после неудачной
        попытки подделки.
        """
        if len(body) < 4:
            self._fail("message too short")
        ctor = int.from_bytes(body[:4], "little", signed=True)

        try:
            if ctor == REQ_PQ_MULTI:
                return self.handle_req_pq(body)
            if ctor == REQ_DH_PARAMS:
                return self.handle_req_dh_params(body)
            if ctor == SET_CLIENT_DH_PARAMS:
                return self.handle_set_client_dh_params(body)
        except Exception:
            self.state = State.FAILED
            raise

        self._fail(f"unexpected constructor {ctor:#x}")

    def _temp_key_iv(self) -> tuple[bytes, bytes]:
        nsn = hashlib.sha1(self._new_nonce + self._server_nonce).digest()
        snn = hashlib.sha1(self._server_nonce + self._new_nonce).digest()
        nnn = hashlib.sha1(self._new_nonce + self._new_nonce).digest()
        return nsn + snn[:12], snn[12:20] + nnn + self._new_nonce[:4]

    def _fail(self, message: str):
        self.state = State.FAILED
        raise HandshakeError(message)


def _check_dh_value(value: int) -> None:
    if value <= 1 or value >= DH_PRIME - 1:
        raise HandshakeError("DH value out of range")
    if value < _MIN_BOUND or value > DH_PRIME - _MIN_BOUND:
        raise HandshakeError("DH value too close to boundary")


def _new_nonce_hash(new_nonce: bytes, num: int, auth_key: bytes) -> bytes:
    aux = hashlib.sha1(auth_key).digest()[:8]
    return hashlib.sha1(new_nonce + bytes([num]) + aux).digest()[4:20]


def _random_prime_31() -> int:
    """Случайное простое чуть меньше 2^31 — как в настоящем MTProto."""
    while True:
        candidate = secrets.randbits(31) | (1 << 30) | 1
        if _is_prime(candidate):
            return candidate


def _is_prime(n: int) -> bool:
    if n < 2:
        return False
    for small in (2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37):
        if n % small == 0:
            return n == small
    d, s = n - 1, 0
    while d % 2 == 0:
        d //= 2
        s += 1
    for a in (2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37):
        x = pow(a, d, n)
        if x in (1, n - 1):
            continue
        for _ in range(s - 1):
            x = x * x % n
            if x == n - 1:
                break
        else:
            return False
    return True


def auth_key_id(auth_key: bytes) -> int:
    """Идентификатор ключа: младшие 8 байт SHA1(auth_key)."""
    return int.from_bytes(hashlib.sha1(auth_key).digest()[12:20], "little", signed=True)
