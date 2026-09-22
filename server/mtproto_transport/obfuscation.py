"""Серверная сторона obfuscated2 для KuoteX.

Разбирает 64-байтный заголовок клиента и выводит из него два потока
AES-CTR — по одному на каждое направление. Совместимо с
MTProtoObfuscator.kt и MTProtoTransport.kt на стороне Android.

Формат заголовка (64 байта, приходят открытым текстом):
    [0:8]   случайный префикс (не 0xEF, не HTTP/TLS-сигнатура)
    [8:40]  ключ AES-256 для направления клиент -> сервер
    [40:56] IV для того же направления
    [56:60] тег протокола (0xEFEFEFEF = abridged), зашифрован
    [60:62] dc_id, зашифрован
"""
from __future__ import annotations

import os
from dataclasses import dataclass

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

ABRIDGED_TAG = b"\xef\xef\xef\xef"
HEADER_SIZE = 64

# Префиксы, по которым DPI опознаёт протокол. Клиент их не генерирует,
# и если такой пришёл — это чужой трафик (сканер, HTTP-запрос).
FORBIDDEN_PREFIXES = (
    b"POST", b"GET ", b"HEAD", b"OPTI", b"PUT ",
    b"\xee\xee\xee\xee", b"\xdd\xdd\xdd\xdd", b"\x16\x03\x01",
)


class ObfuscationError(ValueError):
    """Заголовок не является валидным obfuscated2."""


def _ctr(key: bytes, iv: bytes):
    return Cipher(algorithms.AES(key), modes.CTR(iv)).encryptor()


@dataclass(slots=True)
class ObfuscatedStream:
    """Пара шифраторов для одного соединения."""

    dc_id: int
    protocol: bytes
    _decryptor: object   # расшифровывает входящее от клиента
    _encryptor: object   # шифрует исходящее к клиенту

    def decrypt(self, data: bytes) -> bytes:
        return self._decryptor.update(data)

    def encrypt(self, data: bytes) -> bytes:
        return self._encryptor.update(data)


def accept_header(header: bytes) -> ObfuscatedStream:
    """Разбирает заголовок клиента и возвращает готовые шифраторы.

    :raises ObfuscationError: если заголовок не похож на obfuscated2.
    """
    if len(header) != HEADER_SIZE:
        raise ObfuscationError(f"header must be {HEADER_SIZE} bytes, got {len(header)}")
    if header[0] == 0xEF:
        raise ObfuscationError("header starts with 0xEF")
    if any(header.startswith(p) for p in FORBIDDEN_PREFIXES):
        raise ObfuscationError("header carries a recognizable protocol signature")

    # Направление клиент -> сервер: ключ читается напрямую.
    dec_key, dec_iv = header[8:40], header[40:56]
    # Направление сервер -> клиент: те же байты, развёрнутые задом наперёд.
    reversed_block = header[8:56][::-1]
    enc_key, enc_iv = reversed_block[:32], reversed_block[32:48]

    decryptor = _ctr(dec_key, dec_iv)
    encryptor = _ctr(enc_key, enc_iv)

    # Клиент пропустил через свой шифратор все 64 байта заголовка,
    # значит его гамма сдвинута на 64. Сдвигаем свою так же, но сначала
    # используем расшифровку заголовка для проверки тега протокола.
    decrypted_header = decryptor.update(header)
    protocol = decrypted_header[56:60]
    if protocol != ABRIDGED_TAG:
        raise ObfuscationError(f"unsupported protocol tag {protocol.hex()}")

    dc_id = int.from_bytes(decrypted_header[60:62], "little", signed=True)

    return ObfuscatedStream(
        dc_id=dc_id,
        protocol=protocol,
        _decryptor=decryptor,
        _encryptor=encryptor,
    )


def make_client_header(dc_id: int = 2, protocol: bytes = ABRIDGED_TAG):
    """Клиентская сторона — нужна для тестов и для режима прокси.

    :return: (header_to_send, encryptor, decryptor)
    """
    while True:
        buf = bytearray(os.urandom(HEADER_SIZE))
        if buf[0] == 0xEF:
            continue
        if any(bytes(buf).startswith(p) for p in FORBIDDEN_PREFIXES):
            continue
        if buf[4:8] == b"\x00\x00\x00\x00":
            continue
        break

    buf[56:60] = protocol
    buf[60:62] = dc_id.to_bytes(2, "little", signed=True)

    enc_key, enc_iv = bytes(buf[8:40]), bytes(buf[40:56])
    reversed_block = bytes(buf[8:56])[::-1]
    dec_key, dec_iv = reversed_block[:32], reversed_block[32:48]

    encryptor = _ctr(enc_key, enc_iv)
    decryptor = _ctr(dec_key, dec_iv)

    encrypted = encryptor.update(bytes(buf))
    header = bytes(buf[:56]) + encrypted[56:64]
    # Отдаём функции, а не объекты Cipher: так вызывающему коду
    # не нужно знать про .update().
    return header, encryptor.update, decryptor.update


# ---------------------------------------------------------------- framing

MAX_FRAME_WORDS = 4 * 1024 * 1024   # 16 МБ — защита от OOM


def frame(payload: bytes) -> bytes:
    """abridged framing: длина в 4-байтных словах."""
    if len(payload) % 4:
        raise ValueError("payload must be 4-byte aligned")
    words = len(payload) // 4
    if words < 0x7F:
        return bytes([words]) + payload
    if words > MAX_FRAME_WORDS:
        raise ValueError("payload too large")
    return b"\x7f" + words.to_bytes(3, "little") + payload
