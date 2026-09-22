"""MTProto-прокси для KuoteX.

Принимает обфусцированные соединения от клиентов и пробрасывает их на
дата-центр. Смысл прокси — вынести «видимый» IP за пределы блокировок:
клиент подключается к прокси, а тот уже говорит с ДЦ.

Ключевое свойство: прокси **не расшифровывает** сообщения. Он снимает
только транспортную обфускацию, чтобы понять границы пакетов, и
надевает новую в сторону ДЦ. auth_key остаётся неизвестен прокси —
переписка для него непрозрачна.

Режимы:
  * secret-обфускация: клиент авторизуется 16-байтным секретом,
    посторонние соединения отбрасываются;
  * прозрачный проброс на выбранный дата-центр.
"""
from __future__ import annotations

import asyncio
import contextlib
import hashlib
import logging
import os
import socket
import time
from dataclasses import dataclass, field

from .obfuscation import (
    ABRIDGED_TAG, FORBIDDEN_PREFIXES, HEADER_SIZE, ObfuscationError,
    ObfuscatedStream, _ctr, frame, make_client_header,
)

log = logging.getLogger("kuotex.mtproto.proxy")

MAX_FRAME_WORDS = 4 * 1024 * 1024


class ProxyAuthError(ObfuscationError):
    """Клиент не предъявил корректный секрет."""


@dataclass(slots=True)
class ProxyConfig:
    host: str = "0.0.0.0"
    port: int = 8443

    # 16-байтный секрет в hex. Клиенты без него не пройдут.
    # Пустая строка = принимать всех (только для отладки).
    secret: str = ""

    # Адреса дата-центров: dc_id -> (host, port)
    datacenters: dict[int, tuple[str, int]] = field(default_factory=dict)
    default_dc: int = 2

    handshake_timeout: float = 10.0
    idle_timeout: float = 120.0
    connect_timeout: float = 15.0
    max_connections: int = 5_000
    max_connections_per_ip: int = 32
    backlog: int = 512


@dataclass
class ProxyStats:
    accepted: int = 0
    active: int = 0
    rejected_auth: int = 0
    rejected_limit: int = 0
    upstream_failures: int = 0
    bytes_client_to_dc: int = 0
    bytes_dc_to_client: int = 0


def accept_with_secret(header: bytes, secret: bytes | None) -> ObfuscatedStream:
    """Разбор заголовка клиента с проверкой секрета.

    В secret-режиме ключ расшифровки выводится как SHA256(key + secret).
    Клиент, не знающий секрет, получит другой ключ, тег протокола не
    совпадёт — и соединение будет отброшено. Для стороннего наблюдателя
    отказ выглядит как обычный обрыв, а не как отказ в авторизации.
    """
    if len(header) != HEADER_SIZE:
        raise ObfuscationError(f"header must be {HEADER_SIZE} bytes")
    if header[0] == 0xEF:
        raise ObfuscationError("header starts with 0xEF")
    if any(header.startswith(p) for p in FORBIDDEN_PREFIXES):
        raise ObfuscationError("recognizable protocol signature")

    dec_key, dec_iv = header[8:40], header[40:56]
    reversed_block = header[8:56][::-1]
    enc_key, enc_iv = reversed_block[:32], reversed_block[32:48]

    if secret:
        dec_key = hashlib.sha256(dec_key + secret).digest()
        enc_key = hashlib.sha256(enc_key + secret).digest()

    decryptor = _ctr(dec_key, dec_iv)
    encryptor = _ctr(enc_key, enc_iv)

    decrypted = decryptor.update(header)
    protocol = decrypted[56:60]
    if protocol != ABRIDGED_TAG:
        # Секрет неверный либо это чужой трафик — снаружи не различить.
        raise ProxyAuthError("bad protocol tag: wrong secret or foreign traffic")

    dc_id = int.from_bytes(decrypted[60:62], "little", signed=True)

    return ObfuscatedStream(
        dc_id=dc_id, protocol=protocol,
        _decryptor=decryptor, _encryptor=encryptor,
    )


class MTProtoProxy:
    def __init__(self, config: ProxyConfig):
        self.config = config
        self.stats = ProxyStats()
        self._secret = bytes.fromhex(config.secret) if config.secret else None
        if self._secret is not None and len(self._secret) != 16:
            raise ValueError("secret must be exactly 16 bytes (32 hex chars)")
        self._per_ip: dict[str, int] = {}
        self._server: asyncio.AbstractServer | None = None
        self._tasks: set[asyncio.Task] = set()

    # ------------------------------------------------------------ lifecycle

    async def start(self) -> None:
        self._server = await asyncio.start_server(
            self._on_client,
            host=self.config.host,
            port=self.config.port,
            backlog=self.config.backlog,
            reuse_address=True,
        )
        addrs = ", ".join(str(s.getsockname()) for s in self._server.sockets)
        mode = "secret" if self._secret else "open (debug)"
        log.info("MTProto proxy listening on %s, mode=%s", addrs, mode)

    async def serve_forever(self) -> None:
        if self._server is None:
            await self.start()
        async with self._server:
            await self._server.serve_forever()

    async def stop(self) -> None:
        # Сначала снимаем соединения: wait_closed() ждёт обработчики,
        # и при обратном порядке остановка висит до idle_timeout.
        for task in list(self._tasks):
            task.cancel()
        await asyncio.sleep(0)

        if self._server is not None:
            self._server.close()
            with contextlib.suppress(Exception, asyncio.TimeoutError):
                await asyncio.wait_for(self._server.wait_closed(), timeout=5.0)
        self._tasks.clear()
        self._per_ip.clear()
        self.stats.active = 0

    @property
    def port(self) -> int:
        if not self._server or not self._server.sockets:
            raise RuntimeError("proxy is not started")
        return self._server.sockets[0].getsockname()[1]

    # ------------------------------------------------------------ helpers

    @staticmethod
    def _tune(writer: asyncio.StreamWriter) -> None:
        sock = writer.get_extra_info("socket")
        if sock is None:
            return
        with contextlib.suppress(OSError):
            sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_KEEPALIVE, 1)
            if hasattr(socket, "TCP_KEEPIDLE"):
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_KEEPIDLE, 30)
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_KEEPINTVL, 10)
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_KEEPCNT, 3)

    def _resolve_dc(self, dc_id: int) -> tuple[str, int]:
        target = self.config.datacenters.get(abs(dc_id) or self.config.default_dc)
        if target is None:
            target = self.config.datacenters.get(self.config.default_dc)
        if target is None:
            raise ProxyAuthError(f"no route for dc {dc_id}")
        return target

    # ------------------------------------------------------------ accept

    async def _on_client(self, reader, writer) -> None:
        peer = writer.get_extra_info("peername")
        ip = peer[0] if peer else "unknown"

        if self.stats.active >= self.config.max_connections:
            self.stats.rejected_limit += 1
            writer.close()
            return
        if self._per_ip.get(ip, 0) >= self.config.max_connections_per_ip:
            self.stats.rejected_limit += 1
            writer.close()
            return

        self._per_ip[ip] = self._per_ip.get(ip, 0) + 1
        self.stats.accepted += 1
        self.stats.active += 1

        upstream_writer = None
        try:
            self._tune(writer)
            header = await asyncio.wait_for(
                reader.readexactly(HEADER_SIZE),
                timeout=self.config.handshake_timeout,
            )

            try:
                stream = accept_with_secret(header, self._secret)
            except ProxyAuthError as exc:
                self.stats.rejected_auth += 1
                log.debug("auth rejected for %s: %s", ip, exc)
                return
            except ObfuscationError as exc:
                self.stats.rejected_auth += 1
                log.debug("bad header from %s: %s", ip, exc)
                return

            host, port = self._resolve_dc(stream.dc_id)

            # Поднимаем соединение к ДЦ со своей, независимой обфускацией.
            upstream_reader, upstream_writer = await asyncio.wait_for(
                asyncio.open_connection(host, port),
                timeout=self.config.connect_timeout,
            )
            self._tune(upstream_writer)

            up_header, up_encrypt, up_decrypt = make_client_header(stream.dc_id)
            upstream_writer.write(up_header)
            await upstream_writer.drain()

            log.debug("proxying %s -> dc%s (%s:%s)", ip, stream.dc_id, host, port)

            await self._pump(
                reader, writer, stream,
                upstream_reader, upstream_writer, up_encrypt, up_decrypt,
            )

        except asyncio.IncompleteReadError:
            pass
        except asyncio.TimeoutError:
            self.stats.upstream_failures += 1
        except (ConnectionError, OSError):
            self.stats.upstream_failures += 1
        except asyncio.CancelledError:
            raise
        except Exception:
            log.exception("proxy error for %s", ip)
        finally:
            self.stats.active -= 1
            self._per_ip[ip] = self._per_ip.get(ip, 1) - 1
            if self._per_ip.get(ip, 0) <= 0:
                self._per_ip.pop(ip, None)
            for w in (writer, upstream_writer):
                if w is not None:
                    with contextlib.suppress(Exception):
                        w.close()
                        await asyncio.wait_for(w.wait_closed(), timeout=5.0)

    # ------------------------------------------------------------ pumping

    async def _pump(
        self, client_reader, client_writer, stream,
        up_reader, up_writer, up_encrypt, up_decrypt,
    ) -> None:
        """Два независимых потока: клиент->ДЦ и ДЦ->клиент.

        Кадры пересобираются, а не копируются байт в байт: обфускация
        у двух сторон разная, поэтому расшифровать и зашифрать заново
        обязательно.
        """

        async def client_to_dc():
            while True:
                payload = await asyncio.wait_for(
                    _read_frame(client_reader, stream.decrypt),
                    timeout=self.config.idle_timeout,
                )
                self.stats.bytes_client_to_dc += len(payload)
                up_writer.write(up_encrypt(frame(payload)))
                await up_writer.drain()

        async def dc_to_client():
            while True:
                payload = await asyncio.wait_for(
                    _read_frame(up_reader, up_decrypt),
                    timeout=self.config.idle_timeout,
                )
                self.stats.bytes_dc_to_client += len(payload)
                client_writer.write(stream.encrypt(frame(payload)))
                await client_writer.drain()

        tasks = [
            asyncio.ensure_future(client_to_dc()),
            asyncio.ensure_future(dc_to_client()),
        ]
        self._tasks.update(tasks)
        try:
            # Обрыв любой стороны закрывает всё соединение.
            done, pending = await asyncio.wait(
                tasks, return_when=asyncio.FIRST_COMPLETED
            )
            for task in pending:
                task.cancel()
                with contextlib.suppress(asyncio.CancelledError, Exception):
                    await task
            for task in done:
                if task.cancelled():
                    continue
                exc = task.exception()
                if exc is not None and not isinstance(
                    exc, (asyncio.IncompleteReadError, asyncio.TimeoutError,
                          ConnectionError, OSError)
                ):
                    raise exc
        finally:
            for task in tasks:
                self._tasks.discard(task)


async def _read_frame(reader, decrypt) -> bytes:
    """Читает один abridged-кадр и снимает обфускацию."""
    first = decrypt(await reader.readexactly(1))[0]
    if first < 0x7F:
        words = first
    else:
        words = int.from_bytes(decrypt(await reader.readexactly(3)), "little")
    if words <= 0 or words > MAX_FRAME_WORDS:
        raise ObfuscationError(f"bad frame length: {words}")
    return decrypt(await reader.readexactly(words * 4))


# ---------------------------------------------------------------- entrypoint

def generate_secret() -> str:
    """Новый 16-байтный секрет в hex — его выдают клиентам."""
    return os.urandom(16).hex()


async def main() -> None:
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO"),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    secret = os.getenv("PROXY_SECRET", "")
    if not secret:
        secret = generate_secret()
        log.warning("PROXY_SECRET not set, generated: %s", secret)

    config = ProxyConfig(
        host=os.getenv("PROXY_HOST", "0.0.0.0"),
        port=int(os.getenv("PROXY_PORT", "8443")),
        secret=secret,
        datacenters={
            2: (os.getenv("DC2_HOST", "127.0.0.1"), int(os.getenv("DC2_PORT", "9443"))),
        },
    )

    with contextlib.suppress(ImportError):
        import uvloop
        uvloop.install()
        log.info("uvloop enabled")

    proxy = MTProtoProxy(config)
    await proxy.serve_forever()


if __name__ == "__main__":
    with contextlib.suppress(KeyboardInterrupt):
        asyncio.run(main())
