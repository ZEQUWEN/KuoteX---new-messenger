"""TL-примитивы и AES-IGE для серверной стороны MTProto.

Полностью совместимо с TLStream.kt и AesIge.kt на клиенте:
little-endian числа, строки с 1/254-байтной длиной и выравниванием до 4.
"""
from __future__ import annotations

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

# --- Конструкторы -----------------------------------------------------------

VECTOR = 0x1CB5C415

REQ_PQ_MULTI = -1099002127            # 0xbe7e8ef1
RES_PQ = 0x05162463
P_Q_INNER_DATA_DC = -1443537003       # 0xa9f55f95
REQ_DH_PARAMS = -686627650            # 0xd712e4be
SERVER_DH_PARAMS_OK = -790100132      # 0xd0e8075c
SERVER_DH_INNER_DATA = -1249309254    # 0xb5890dba
CLIENT_DH_INNER_DATA = 0x6643B654
SET_CLIENT_DH_PARAMS = -184262881     # 0xf5045f1f
DH_GEN_OK = 0x3BCBF734

MSG_CONTAINER = 0x73F1F8DC
MSGS_ACK = 0x62D6B459
BAD_MSG_NOTIFICATION = -1477445615    # 0xa7eff811
BAD_SERVER_SALT = -307542917          # 0xedab447b
NEW_SESSION_CREATED = -1631450872     # 0x9ec20908
PING = 0x7ABE77EC
PONG = 0x347773C5
RPC_RESULT = -212046591               # 0xf35c6d01
RPC_ERROR = 0x2144CA19


class TLError(ValueError):
    """Некорректные TL-данные."""


class Reader:
    """Последовательное чтение TL. Отслеживает смещение — оно нужно,
    чтобы посчитать точную длину объекта для проверки SHA1."""

    __slots__ = ("_data", "offset")

    def __init__(self, data: bytes):
        self._data = data
        self.offset = 0

    def _take(self, n: int) -> bytes:
        if self.offset + n > len(self._data):
            raise TLError(f"unexpected end of data at {self.offset}, need {n}")
        chunk = self._data[self.offset:self.offset + n]
        self.offset += n
        return chunk

    def raw(self, n: int) -> bytes:
        return self._take(n)

    def int32(self) -> int:
        return int.from_bytes(self._take(4), "little", signed=True)

    def uint32(self) -> int:
        return int.from_bytes(self._take(4), "little", signed=False)

    def int64(self) -> int:
        return int.from_bytes(self._take(8), "little", signed=True)

    def bytes(self) -> bytes:
        first = self._take(1)[0]
        if first <= 253:
            length = first
            padding = (4 - (length + 1) % 4) % 4
        elif first == 254:
            length = int.from_bytes(self._take(3), "little")
            padding = (4 - length % 4) % 4
        else:
            raise TLError(f"invalid length prefix {first}")
        data = self._take(length)
        self._take(padding)
        return data

    def string(self) -> str:
        return self.bytes().decode("utf-8", errors="replace")


class Writer:
    """Последовательная запись TL."""

    __slots__ = ("_parts",)

    def __init__(self):
        self._parts: list[bytes] = []

    def raw(self, data: bytes) -> "Writer":
        self._parts.append(data)
        return self

    def int32(self, value: int) -> "Writer":
        self._parts.append(int(value).to_bytes(4, "little", signed=value < 0))
        return self

    def int64(self, value: int) -> "Writer":
        self._parts.append(int(value).to_bytes(8, "little", signed=value < 0))
        return self

    def bytes(self, data: bytes) -> "Writer":
        length = len(data)
        if length <= 253:
            self._parts.append(bytes([length]))
            self._parts.append(data)
            self._parts.append(b"\x00" * ((4 - (length + 1) % 4) % 4))
        else:
            self._parts.append(b"\xfe" + length.to_bytes(3, "little"))
            self._parts.append(data)
            self._parts.append(b"\x00" * ((4 - length % 4) % 4))
        return self

    def string(self, value: str) -> "Writer":
        return self.bytes(value.encode("utf-8"))

    def data(self) -> bytes:
        return b"".join(self._parts)


# --- AES-IGE ----------------------------------------------------------------

def _ecb(key: bytes):
    return Cipher(algorithms.AES(key), modes.ECB())


def ige_encrypt(data: bytes, key: bytes, iv: bytes) -> bytes:
    """AES-IGE. IV состоит из двух блоков: iv[:16] для шифротекста,
    iv[16:] для открытого текста."""
    if len(data) % 16:
        raise ValueError("data must be a multiple of 16 bytes")
    if len(iv) != 32:
        raise ValueError("iv must be 32 bytes")

    encryptor = _ecb(key).encryptor()
    prev_cipher, prev_plain = iv[:16], iv[16:]
    out = bytearray()

    for i in range(0, len(data), 16):
        block = data[i:i + 16]
        xored = bytes(a ^ b for a, b in zip(block, prev_cipher))
        encrypted = encryptor.update(xored)
        result = bytes(a ^ b for a, b in zip(encrypted, prev_plain))
        out += result
        prev_cipher, prev_plain = result, block

    return bytes(out)


def ige_decrypt(data: bytes, key: bytes, iv: bytes) -> bytes:
    if len(data) % 16:
        raise ValueError("data must be a multiple of 16 bytes")
    if len(iv) != 32:
        raise ValueError("iv must be 32 bytes")

    decryptor = _ecb(key).decryptor()
    prev_cipher, prev_plain = iv[:16], iv[16:]
    out = bytearray()

    for i in range(0, len(data), 16):
        block = data[i:i + 16]
        xored = bytes(a ^ b for a, b in zip(block, prev_plain))
        decrypted = decryptor.update(xored)
        result = bytes(a ^ b for a, b in zip(decrypted, prev_cipher))
        out += result
        prev_cipher, prev_plain = block, result

    return bytes(out)
