"""Шифрование сообщений MTProto 2.0 на сервере.

Зеркало MTProtoCrypto.kt и MTProtoSession.kt. Ключевое отличие
направлений: сервер шифрует с x=8, а расшифровывает клиентские
сообщения с x=0.
"""
from __future__ import annotations

import hashlib
import os
import secrets
import time
from dataclasses import dataclass, field

from .tl import MSG_CONTAINER, MSGS_ACK, VECTOR, Reader, Writer, ige_decrypt, ige_encrypt


class SecurityViolation(Exception):
    """Криптографический инвариант нарушен: соединение рвать."""


AUTH_KEY_SIZE = 256


def auth_key_id(auth_key: bytes) -> int:
    return int.from_bytes(hashlib.sha1(auth_key).digest()[12:20], "little", signed=True)


def message_key(auth_key: bytes, plaintext: bytes, from_client: bool) -> bytes:
    """msg_key = middle 128 bits of SHA256(substr(auth_key, 88+x, 32) + plaintext)."""
    x = 0 if from_client else 8
    return hashlib.sha256(auth_key[88 + x:88 + x + 32] + plaintext).digest()[8:24]


def derive_key_iv(auth_key: bytes, msg_key: bytes, from_client: bool) -> tuple[bytes, bytes]:
    """KDF версии 2.0."""
    if len(auth_key) != AUTH_KEY_SIZE:
        raise SecurityViolation("auth_key must be 256 bytes")
    if len(msg_key) != 16:
        raise SecurityViolation("msg_key must be 16 bytes")

    x = 0 if from_client else 8
    sha_a = hashlib.sha256(msg_key + auth_key[x:x + 36]).digest()
    sha_b = hashlib.sha256(auth_key[40 + x:40 + x + 36] + msg_key).digest()

    aes_key = sha_a[:8] + sha_b[8:24] + sha_a[24:32]
    aes_iv = sha_b[:8] + sha_a[8:24] + sha_b[24:32]
    return aes_key, aes_iv


def encrypt(auth_key: bytes, payload: bytes, from_client: bool) -> bytes:
    """Собирает конверт: auth_key_id(8) + msg_key(16) + шифротекст."""
    pad_len = 12 + (-(len(payload) + 12)) % 16
    pad_len += secrets.randbelow(8) * 16       # рандомизация длины
    padded = payload + os.urandom(pad_len)

    msg_key = message_key(auth_key, padded, from_client)
    aes_key, aes_iv = derive_key_iv(auth_key, msg_key, from_client)
    encrypted = ige_encrypt(padded, aes_key, aes_iv)

    return (
        auth_key_id(auth_key).to_bytes(8, "little", signed=True)
        + msg_key
        + encrypted
    )


def decrypt(auth_key: bytes, envelope: bytes, from_client: bool) -> bytes:
    """Расшифровывает конверт с обязательной проверкой msg_key."""
    if len(envelope) < 40 or (len(envelope) - 24) % 16:
        raise SecurityViolation("bad envelope length")

    received_id = int.from_bytes(envelope[:8], "little", signed=True)
    if received_id != auth_key_id(auth_key):
        raise SecurityViolation("unknown auth_key_id")

    msg_key = envelope[8:24]
    aes_key, aes_iv = derive_key_iv(auth_key, msg_key, from_client)
    decrypted = ige_decrypt(envelope[24:], aes_key, aes_iv)

    # Проверка ДО разбора: иначе получаем padding oracle.
    if not secrets.compare_digest(
        msg_key, message_key(auth_key, decrypted, from_client)
    ):
        raise SecurityViolation("msg_key mismatch: message forged or corrupted")

    return decrypted


@dataclass
class IncomingMessage:
    salt: int
    session_id: int
    msg_id: int
    seq_no: int
    body: bytes


@dataclass
class ServerSession:
    """Состояние сессии на стороне сервера.

    Хранит окно принятых msg_id (анти-replay) и выдаёт корректные
    нечётные msg_id для собственных сообщений.
    """

    auth_key: bytes
    session_id: int = 0
    server_salt: int = 0

    _last_msg_id: int = 0
    _seen: set[int] = field(default_factory=set)
    _seen_order: list[int] = field(default_factory=list)
    _seq_no: int = 0

    MAX_SEEN = 4096
    PAST_WINDOW = 300
    FUTURE_WINDOW = 30

    def next_msg_id(self) -> int:
        """Сервер использует нечётные msg_id."""
        now = time.time()
        msg_id = (int(now) << 32) | (int((now % 1) * (1 << 30)) << 2 & 0xFFFFFFFC)
        msg_id = (msg_id & ~3) | 1
        if msg_id <= self._last_msg_id:
            msg_id = self._last_msg_id + 4
        self._last_msg_id = msg_id
        return msg_id

    def next_seq_no(self, content_related: bool) -> int:
        if content_related:
            self._seq_no += 1
            return self._seq_no * 2 - 1
        return self._seq_no * 2

    def unpack(self, envelope: bytes) -> IncomingMessage:
        """Расшифровывает и валидирует сообщение клиента."""
        decrypted = decrypt(self.auth_key, envelope, from_client=True)
        if len(decrypted) < 32:
            raise SecurityViolation("payload too short")

        salt = int.from_bytes(decrypted[0:8], "little", signed=True)
        session_id = int.from_bytes(decrypted[8:16], "little", signed=True)
        msg_id = int.from_bytes(decrypted[16:24], "little", signed=True)
        seq_no = int.from_bytes(decrypted[24:28], "little", signed=True)
        length = int.from_bytes(decrypted[28:32], "little", signed=True)

        if length < 0 or length % 4 or 32 + length > len(decrypted):
            raise SecurityViolation("bad body length")
        if len(decrypted) - 32 - length < 12:
            raise SecurityViolation("padding shorter than 12 bytes")
        # Сообщения клиента обязаны иметь чётный msg_id.
        if msg_id % 2 != 0:
            raise SecurityViolation("bad msg_id parity")

        timestamp = msg_id >> 32
        now = int(time.time())
        if timestamp < now - self.PAST_WINDOW or timestamp > now + self.FUTURE_WINDOW:
            raise SecurityViolation("msg_id outside acceptance window")

        if msg_id in self._seen:
            raise SecurityViolation("replayed msg_id")
        self._seen.add(msg_id)
        self._seen_order.append(msg_id)
        if len(self._seen_order) > self.MAX_SEEN:
            for old in self._seen_order[: self.MAX_SEEN // 4]:
                self._seen.discard(old)
            del self._seen_order[: self.MAX_SEEN // 4]

        if self.session_id == 0:
            self.session_id = session_id      # первая сессия клиента

        return IncomingMessage(salt, session_id, msg_id, seq_no, decrypted[32:32 + length])

    def pack(self, body: bytes, content_related: bool = True) -> bytes:
        """Шифрует ответ клиенту."""
        payload = (
            self.server_salt.to_bytes(8, "little", signed=True)
            + self.session_id.to_bytes(8, "little", signed=True)
            + self.next_msg_id().to_bytes(8, "little", signed=True)
            + self.next_seq_no(content_related).to_bytes(4, "little", signed=True)
            + len(body).to_bytes(4, "little", signed=True)
            + body
        )
        return encrypt(self.auth_key, payload, from_client=False)


# ---------------------------------------------------------------- containers

def parse_container(body: bytes) -> list[tuple[int, int, bytes]]:
    """Разворачивает msg_container -> [(msg_id, seq_no, body)]."""
    r = Reader(body)
    if r.int32() != MSG_CONTAINER:
        raise SecurityViolation("not a msg_container")
    count = r.int32()
    if count < 0 or count > 1024:
        raise SecurityViolation(f"bad container size: {count}")

    result = []
    for _ in range(count):
        msg_id = r.int64()
        seq_no = r.int32()
        length = r.int32()
        if length < 0 or length % 4:
            raise SecurityViolation("bad inner length")
        result.append((msg_id, seq_no, r.raw(length)))
    return result


def build_container(messages: list[tuple[int, int, bytes]]) -> bytes:
    """Собирает msg_container из [(msg_id, seq_no, body)]."""
    w = Writer()
    w.int32(MSG_CONTAINER)
    w.int32(len(messages))
    for msg_id, seq_no, body in messages:
        if len(body) % 4:
            raise ValueError("message body must be 4-byte aligned")
        w.int64(msg_id)
        w.int32(seq_no)
        w.int32(len(body))
        w.raw(body)
    return w.data()


def build_ack(msg_ids: list[int]) -> bytes:
    w = Writer()
    w.int32(MSGS_ACK)
    w.int32(VECTOR)
    w.int32(len(msg_ids))
    for msg_id in msg_ids:
        w.int64(msg_id)
    return w.data()


def parse_ack(body: bytes) -> list[int]:
    r = Reader(body)
    if r.int32() != MSGS_ACK:
        raise SecurityViolation("not msgs_ack")
    if r.int32() != VECTOR:
        raise SecurityViolation("msgs_ack without vector")
    count = r.int32()
    if count < 0 or count > 8192:
        raise SecurityViolation("bad ack count")
    return [r.int64() for _ in range(count)]
