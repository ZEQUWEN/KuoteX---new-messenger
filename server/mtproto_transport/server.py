"""Асинхронный MTProto-сервер (обфусцированный TCP) для KuoteX.

Держит тысячи одновременных мобильных соединений на слабом железе.

Ключевые решения, важные для мобильных операторов:
  * жёсткие таймауты на каждой стадии — «мёртвые» соединения не копятся;
  * TCP keepalive с агрессивными настройками — молчаливый обрыв
    (типично для LTE и метро) обнаруживается за ~40 с, а не за 2 часа;
  * ограничение соединений на IP — защита от исчерпания дескрипторов;
  * backpressure через bounded queue — при всплеске память не растёт;
  * корректное закрытие: соединение всегда снимается с учёта.
"""
from __future__ import annotations

import asyncio
import contextlib
import logging
import os
import socket
import time
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Awaitable, Callable

from .obfuscation import ObfuscationError, accept_header, frame

log = logging.getLogger("kuotex.mtproto.server")

HEADER_SIZE = 64
MAX_FRAME_WORDS = 4 * 1024 * 1024


@dataclass(slots=True)
class ServerConfig:
    host: str = "0.0.0.0"
    port: int = 443
    # Клиент обязан прислать заголовок сразу: сканеры этого не делают.
    handshake_timeout: float = 10.0
    # Клиент шлёт ping каждые 30 с, поэтому 75 с — безопасный порог.
    idle_timeout: float = 75.0
    write_timeout: float = 30.0
    max_connections: int = 10_000
    max_connections_per_ip: int = 50
    # Размер очереди исходящих на одно соединение.
    send_queue_size: int = 256
    backlog: int = 512


@dataclass
class ConnectionStats:
    total_accepted: int = 0
    active: int = 0
    rejected_bad_header: int = 0
    rejected_limit: int = 0
    timeouts: int = 0
    bytes_in: int = 0
    bytes_out: int = 0


class Connection:
    """Одно клиентское соединение."""

    __slots__ = ("reader", "writer", "stream", "peer", "queue",
                 "_closed", "created_at", "auth_key_id", "tasks")

    def __init__(self, reader, writer, stream, peer, queue_size: int):
        self.reader = reader
        self.writer = writer
        self.stream = stream
        self.peer = peer
        self.queue: asyncio.Queue[bytes] = asyncio.Queue(maxsize=queue_size)
        self._closed = False
        self.created_at = time.monotonic()
        self.auth_key_id: int | None = None
        # Задачи чтения и записи: нужны, чтобы разбудить их при закрытии,
        # иначе они висят на await до самого idle_timeout.
        self.tasks: list[asyncio.Task] = []

    @property
    def dc_id(self) -> int:
        return self.stream.dc_id

    async def send(self, payload: bytes) -> None:
        """Кладёт пакет в очередь. При переполнении — рвём соединение.

        Молча терять сообщения нельзя: клиент будет думать, что доставлено.
        """
        if self._closed:
            raise ConnectionError("connection is closed")
        try:
            self.queue.put_nowait(payload)
        except asyncio.QueueFull:
            raise ConnectionError("send queue overflow: client is too slow")

    def close(self) -> None:
        if self._closed:
            return
        self._closed = True
        # Снимаем задачи, иначе _write_loop останется висеть на queue.get(),
        # а _read_loop — на readexactly() до истечения таймаута.
        for task in self.tasks:
            if not task.done():
                task.cancel()
        with contextlib.suppress(Exception):
            self.writer.close()

    @property
    def closed(self) -> bool:
        return self._closed


MessageHandler = Callable[[Connection, bytes], Awaitable[None]]


class MTProtoServer:
    def __init__(self, config: ServerConfig, handler: MessageHandler):
        self.config = config
        self.handler = handler
        self.stats = ConnectionStats()
        self._connections: set[Connection] = set()
        self._per_ip: dict[str, int] = defaultdict(int)
        self._server: asyncio.AbstractServer | None = None

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
        log.info("MTProto server listening on %s", addrs)

    async def serve_forever(self) -> None:
        if self._server is None:
            await self.start()
        async with self._server:
            await self._server.serve_forever()

    async def stop(self) -> None:
        # Порядок важен: сначала снимаем соединения, потом ждём сервер.
        # Server.wait_closed() дожидается завершения всех обработчиков,
        # поэтому при обратном порядке он висит до idle_timeout.
        for conn in list(self._connections):
            conn.close()
        await asyncio.sleep(0)

        if self._server is not None:
            self._server.close()
            with contextlib.suppress(Exception, asyncio.TimeoutError):
                await asyncio.wait_for(self._server.wait_closed(), timeout=5.0)
        # Даём отменённым задачам один оборот цикла, чтобы обработчики
        # успели отработать finally и снять соединения с учёта.
        await asyncio.sleep(0)
        self._connections.clear()
        self._per_ip.clear()
        self.stats.active = 0

    @property
    def port(self) -> int:
        """Реальный порт (полезно, когда указан 0 — выбор системой)."""
        if not self._server or not self._server.sockets:
            raise RuntimeError("server is not started")
        return self._server.sockets[0].getsockname()[1]

    # ------------------------------------------------------------ tuning

    @staticmethod
    def _tune_socket(writer: asyncio.StreamWriter) -> None:
        """Настройки, без которых мобильные соединения зависают."""
        sock = writer.get_extra_info("socket")
        if sock is None:
            return
        with contextlib.suppress(OSError):
            sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_KEEPALIVE, 1)
            # Linux: обнаружить мёртвого клиента за ~20+3*5 = 35 секунд.
            if hasattr(socket, "TCP_KEEPIDLE"):
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_KEEPIDLE, 20)
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_KEEPINTVL, 5)
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_KEEPCNT, 3)

    # ------------------------------------------------------------ accept

    async def _on_client(self, reader: asyncio.StreamReader,
                         writer: asyncio.StreamWriter) -> None:
        peer_info = writer.get_extra_info("peername")
        ip = peer_info[0] if peer_info else "unknown"

        # Лимиты проверяем до любой работы — так дешевле отбить флуд.
        if len(self._connections) >= self.config.max_connections:
            self.stats.rejected_limit += 1
            writer.close()
            return
        if self._per_ip[ip] >= self.config.max_connections_per_ip:
            self.stats.rejected_limit += 1
            log.warning("per-IP limit reached for %s", ip)
            writer.close()
            return

        self._per_ip[ip] += 1
        self.stats.total_accepted += 1
        conn: Connection | None = None

        try:
            self._tune_socket(writer)

            # Заголовок должен прийти быстро.
            header = await asyncio.wait_for(
                reader.readexactly(HEADER_SIZE),
                timeout=self.config.handshake_timeout,
            )
            self.stats.bytes_in += HEADER_SIZE

            try:
                stream = accept_header(header)
            except ObfuscationError as exc:
                self.stats.rejected_bad_header += 1
                log.debug("rejected %s: %s", ip, exc)
                return

            conn = Connection(reader, writer, stream, ip, self.config.send_queue_size)
            self._connections.add(conn)
            self.stats.active = len(self._connections)

            # Чтение и запись — отдельные задачи: медленная запись
            # не должна блокировать приём.
            conn.tasks = [
                asyncio.ensure_future(self._read_loop(conn)),
                asyncio.ensure_future(self._write_loop(conn)),
            ]
            # Как только одна из них завершилась, соединение закрывается.
            done, pending_tasks = await asyncio.wait(
                conn.tasks, return_when=asyncio.FIRST_COMPLETED
            )
            conn.close()
            for task in pending_tasks:
                with contextlib.suppress(asyncio.CancelledError):
                    await task
            for task in done:
                if task.cancelled():
                    continue
                exc = task.exception()
                # Обрыв соединения — штатное событие, не ошибка.
                if exc is not None and not isinstance(
                    exc, (asyncio.IncompleteReadError, ConnectionError, OSError)
                ):
                    raise exc

        except asyncio.IncompleteReadError:
            pass                       # клиент отключился, это норма
        except asyncio.TimeoutError:
            self.stats.timeouts += 1
        except (ConnectionError, OSError):
            pass
        except asyncio.CancelledError:
            raise
        except Exception:
            log.exception("unexpected error serving %s", ip)
        finally:
            if conn is not None:
                conn.close()
                self._connections.discard(conn)
                self.stats.active = len(self._connections)
            self._per_ip[ip] -= 1
            if self._per_ip[ip] <= 0:
                self._per_ip.pop(ip, None)
            with contextlib.suppress(Exception):
                writer.close()
                # wait_closed() ждёт, пока сокет закроет и другая сторона.
                # Клиент в мобильной сети может «исчезнуть» и не ответить
                # никогда — без таймаута обработчик повиснет навсегда,
                # удерживая дескриптор.
                await asyncio.wait_for(writer.wait_closed(), timeout=5.0)

    # ------------------------------------------------------------ loops

    async def _read_loop(self, conn: Connection) -> None:
        while not conn.closed:
            try:
                first = await asyncio.wait_for(
                    conn.reader.readexactly(1), timeout=self.config.idle_timeout
                )
            except asyncio.TimeoutError:
                self.stats.timeouts += 1
                log.debug("idle timeout for %s", conn.peer)
                conn.close()
                return

            length_byte = conn.stream.decrypt(first)[0]
            if length_byte < 0x7F:
                words = length_byte
            else:
                ext = conn.stream.decrypt(await conn.reader.readexactly(3))
                words = int.from_bytes(ext, "little")

            if words <= 0 or words > MAX_FRAME_WORDS:
                log.warning("bad frame length %s from %s", words, conn.peer)
                conn.close()
                return

            body = conn.stream.decrypt(await conn.reader.readexactly(words * 4))
            self.stats.bytes_in += words * 4

            try:
                await self.handler(conn, body)
            except ConnectionError:
                conn.close()
                return
            except Exception:
                # Ошибка прикладного уровня не должна ронять соединение.
                log.exception("handler failed for %s", conn.peer)

    async def _write_loop(self, conn: Connection) -> None:
        while not conn.closed:
            payload = await conn.queue.get()
            try:
                data = conn.stream.encrypt(frame(payload))
                conn.writer.write(data)
                await asyncio.wait_for(
                    conn.writer.drain(), timeout=self.config.write_timeout
                )
                self.stats.bytes_out += len(data)
            except (asyncio.TimeoutError, ConnectionError, OSError):
                conn.close()
                return


# ---------------------------------------------------------------- entrypoint

async def echo_handler(conn: Connection, payload: bytes) -> None:
    """Заглушка: возвращает пакет обратно. Замени на роутер KuoteX."""
    await conn.send(payload)


async def main() -> None:
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO"),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    config = ServerConfig(
        host=os.getenv("MTPROTO_HOST", "0.0.0.0"),
        port=int(os.getenv("MTPROTO_PORT", "8443")),
    )
    server = MTProtoServer(config, echo_handler)

    # uvloop ускоряет asyncio в 2-4 раза. Ставится опционально.
    with contextlib.suppress(ImportError):
        import uvloop
        uvloop.install()
        log.info("uvloop enabled")

    await server.serve_forever()


if __name__ == "__main__":
    with contextlib.suppress(KeyboardInterrupt):
        asyncio.run(main())
