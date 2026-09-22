"""Платёжный леджер KuoteX: атомарные операции и раздельные счета.

Решает баг, при котором спам кнопкой «купить звёзды» начислял больше
положенного, и вводит раздельные кошельки под фиат и звёзды.

Три принципа, на которых всё держится:

1. **Двойная запись.** Каждая операция — это две проводки: откуда списали
   и куда зачислили. Сумма всех проводок по системе всегда равна нулю.
   Если баланс «появился из воздуха», это видно сразу по сверке.

2. **Идемпотентность.** У операции есть ключ. Повтор с тем же ключом
   возвращает прежний результат, а не выполняет операцию заново.
   Уникальный индекс в БД — последний рубеж: даже при ошибке в логике
   база физически не даст записать дубль.

3. **Баланс не хранится отдельно от проводок.** Он считается как сумма,
   а закешированное значение проверяется сверкой. Нельзя «подправить»
   баланс, не оставив следа.

Валюты разделены жёстко: перевод между кошельками возможен только
явной операцией обмена, у которой есть курс и своя запись в леджере.
"""
from __future__ import annotations

import contextlib
import enum
import json
import os
import sqlite3
import threading
import time
import uuid
from dataclasses import dataclass, field


class Currency(str, enum.Enum):
    """Раздельные счета. Смешивать их в одной проводке запрещено."""

    STARS = "STARS"      # внутренняя валюта мессенджера
    FIAT_RUB = "RUB"     # фиат, рубли
    FIAT_USD = "USD"
    FIAT_EUR = "EUR"

    @property
    def is_fiat(self) -> bool:
        return self is not Currency.STARS


class EntryType(str, enum.Enum):
    TOPUP = "TOPUP"                  # пополнение извне
    GIFT_PURCHASE = "GIFT_PURCHASE"
    GIFT_UPGRADE = "GIFT_UPGRADE"
    VIP_SUBSCRIPTION = "VIP_SUBSCRIPTION"
    EXCHANGE = "EXCHANGE"            # фиат -> звёзды
    REFUND = "REFUND"
    TRANSFER = "TRANSFER"


class PaymentError(Exception):
    """Базовая ошибка платёжного слоя."""


class InsufficientFunds(PaymentError):
    def __init__(self, account: str, currency: Currency, need: int, have: int):
        super().__init__(
            f"insufficient {currency.value} on {account}: need {need}, have {have}"
        )
        self.need = need
        self.have = have


class DuplicateOperation(PaymentError):
    """Операция с таким ключом уже выполнена."""

    def __init__(self, key: str, result: dict):
        super().__init__(f"operation {key} already processed")
        self.key = key
        self.result = result


class CurrencyMismatch(PaymentError):
    """Попытка смешать валюты в одной проводке."""


# Системные счета. Деньги всегда приходят откуда-то и уходят куда-то —
# это и позволяет сверять систему в ноль.
SYSTEM_GATEWAY = "system:payment_gateway"   # внешний платёжный провайдер
SYSTEM_REVENUE = "system:revenue"           # выручка KuoteX
SYSTEM_EXCHANGE = "system:exchange"         # обменник фиат<->звёзды
SYSTEM_ACCOUNTS = frozenset(
    {SYSTEM_GATEWAY, SYSTEM_REVENUE, SYSTEM_EXCHANGE}
)


@dataclass(slots=True)
class Entry:
    """Одна проводка."""

    entry_id: str
    tx_id: str
    account: str
    currency: Currency
    amount: int          # положительное = зачисление, отрицательное = списание
    entry_type: EntryType
    created_at: float
    metadata: str = ""


@dataclass(slots=True)
class TransactionResult:
    tx_id: str
    idempotency_key: str
    entries: list[Entry] = field(default_factory=list)
    balances: dict[str, int] = field(default_factory=dict)
    replayed: bool = False


class PaymentLedger:
    """Леджер на SQLite. Все операции атомарны и идемпотентны."""

    _SCHEMA = """
    CREATE TABLE IF NOT EXISTS ledger_entries (
        entry_id    TEXT PRIMARY KEY,
        tx_id       TEXT NOT NULL,
        account     TEXT NOT NULL,
        currency    TEXT NOT NULL,
        amount      INTEGER NOT NULL,
        entry_type  TEXT NOT NULL,
        created_at  REAL NOT NULL,
        metadata    TEXT DEFAULT ''
    );
    CREATE INDEX IF NOT EXISTS idx_entries_account
        ON ledger_entries(account, currency);
    CREATE INDEX IF NOT EXISTS idx_entries_tx ON ledger_entries(tx_id);

    -- Уникальный ключ — физическая защита от дублей.
    -- Даже если логика подведёт, база не даст записать операцию дважды.
    CREATE TABLE IF NOT EXISTS idempotency_keys (
        key         TEXT PRIMARY KEY,
        tx_id       TEXT NOT NULL,
        result_json TEXT NOT NULL,
        created_at  REAL NOT NULL
    );

    -- Кеш балансов: ускоряет чтение, но истина — в проводках.
    CREATE TABLE IF NOT EXISTS balances (
        account   TEXT NOT NULL,
        currency  TEXT NOT NULL,
        amount    INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (account, currency)
    );
    """

    def __init__(self, path: str = ":memory:"):
        self._lock = threading.RLock()
        self._conn = sqlite3.connect(path, check_same_thread=False,
                                     isolation_level=None)
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._conn.execute("PRAGMA synchronous=FULL")   # деньги важнее скорости
        self._conn.execute("PRAGMA foreign_keys=ON")
        self._conn.executescript(self._SCHEMA)

    # ------------------------------------------------------------ helpers

    def close(self) -> None:
        with self._lock:
            self._conn.close()

    def balance(self, account: str, currency: Currency) -> int:
        with self._lock:
            row = self._conn.execute(
                "SELECT amount FROM balances WHERE account = ? AND currency = ?",
                (account, currency.value),
            ).fetchone()
        return row[0] if row else 0

    def balances_of(self, account: str) -> dict[str, int]:
        """Все кошельки пользователя разом."""
        with self._lock:
            rows = self._conn.execute(
                "SELECT currency, amount FROM balances WHERE account = ?",
                (account,),
            ).fetchall()
        return {currency: amount for currency, amount in rows}

    def history(self, account: str, limit: int = 50) -> list[Entry]:
        with self._lock:
            rows = self._conn.execute(
                "SELECT entry_id, tx_id, account, currency, amount, entry_type,"
                " created_at, metadata FROM ledger_entries WHERE account = ?"
                " ORDER BY created_at DESC, rowid DESC LIMIT ?",
                (account, limit),
            ).fetchall()
        return [
            Entry(r[0], r[1], r[2], Currency(r[3]), r[4],
                  EntryType(r[5]), r[6], r[7])
            for r in rows
        ]

    # ------------------------------------------------------------ core

    def _post(
        self,
        idempotency_key: str,
        entries: list[tuple[str, Currency, int]],
        entry_type: EntryType,
        metadata: str = "",
        allow_negative: frozenset[str] = SYSTEM_ACCOUNTS,
    ) -> TransactionResult:
        """Записывает набор проводок одной атомарной транзакцией.

        entries: список (счёт, валюта, сумма). Сумма по каждой валюте
        обязана быть нулевой — это и есть двойная запись.
        """
        if not entries:
            raise PaymentError("no entries to post")

        # Проверка баланса по валютам: деньги не должны появляться из воздуха.
        totals: dict[Currency, int] = {}
        for _account, currency, amount in entries:
            totals[currency] = totals.get(currency, 0) + amount
        for currency, total in totals.items():
            if total != 0:
                raise CurrencyMismatch(
                    f"entries for {currency.value} do not balance: {total}"
                )

        now = time.time()
        tx_id = f"tx_{int(now * 1000)}_{uuid.uuid4().hex[:8]}"

        with self._lock:
            # BEGIN IMMEDIATE берёт блокировку записи сразу, а не при первом
            # UPDATE. Без этого два параллельных пополнения успевают оба
            # прочитать старый баланс — ровно тот баг с «дюпом».
            self._conn.execute("BEGIN IMMEDIATE")
            try:
                existing = self._conn.execute(
                    "SELECT tx_id, result_json FROM idempotency_keys WHERE key = ?",
                    (idempotency_key,),
                ).fetchone()
                if existing is not None:
                    self._conn.execute("ROLLBACK")
                    raise DuplicateOperation(
                        idempotency_key, json.loads(existing[1])
                    )

                balances: dict[str, int] = {}
                created: list[Entry] = []

                for account, currency, amount in entries:
                    row = self._conn.execute(
                        "SELECT amount FROM balances WHERE account = ? AND currency = ?",
                        (account, currency.value),
                    ).fetchone()
                    current = row[0] if row else 0
                    updated = current + amount

                    # Уйти в минус могут только системные счета:
                    # для них отрицательный баланс означает «выдано в систему».
                    if updated < 0 and account not in allow_negative:
                        self._conn.execute("ROLLBACK")
                        raise InsufficientFunds(account, currency, -amount, current)

                    self._conn.execute(
                        "INSERT INTO balances(account, currency, amount) VALUES (?, ?, ?)"
                        " ON CONFLICT(account, currency) DO UPDATE SET amount = ?",
                        (account, currency.value, updated, updated),
                    )

                    entry = Entry(
                        entry_id=f"e_{uuid.uuid4().hex[:12]}",
                        tx_id=tx_id,
                        account=account,
                        currency=currency,
                        amount=amount,
                        entry_type=entry_type,
                        created_at=now,
                        metadata=metadata,
                    )
                    self._conn.execute(
                        "INSERT INTO ledger_entries(entry_id, tx_id, account, currency,"
                        " amount, entry_type, created_at, metadata)"
                        " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        (entry.entry_id, tx_id, account, currency.value, amount,
                         entry_type.value, now, metadata),
                    )
                    created.append(entry)
                    balances[f"{account}:{currency.value}"] = updated

                result_payload = {"tx_id": tx_id, "balances": balances}
                self._conn.execute(
                    "INSERT INTO idempotency_keys(key, tx_id, result_json, created_at)"
                    " VALUES (?, ?, ?, ?)",
                    (idempotency_key, tx_id, json.dumps(result_payload), now),
                )

                self._conn.execute("COMMIT")
            except DuplicateOperation:
                raise
            except sqlite3.IntegrityError as exc:
                # Сработал уникальный индекс: два одинаковых ключа
                # пришли одновременно. Это не ошибка — это защита.
                self._conn.execute("ROLLBACK")
                existing = self._conn.execute(
                    "SELECT tx_id, result_json FROM idempotency_keys WHERE key = ?",
                    (idempotency_key,),
                ).fetchone()
                if existing is not None:
                    raise DuplicateOperation(
                        idempotency_key, json.loads(existing[1])
                    ) from exc
                raise PaymentError(f"integrity error: {exc}") from exc
            except Exception:
                with contextlib.suppress(sqlite3.OperationalError):
                    self._conn.execute("ROLLBACK")
                raise

        return TransactionResult(
            tx_id=tx_id,
            idempotency_key=idempotency_key,
            entries=created,
            balances=balances,
        )

    def post(self, *args, **kwargs) -> TransactionResult:
        """То же, что _post, но повтор возвращает прежний результат
        вместо исключения. Удобно для вебхуков: провайдер шлёт их
        по несколько раз, и это нормальное поведение, а не ошибка."""
        try:
            return self._post(*args, **kwargs)
        except DuplicateOperation as exc:
            return TransactionResult(
                tx_id=exc.result["tx_id"],
                idempotency_key=exc.key,
                balances=exc.result["balances"],
                replayed=True,
            )

    # ------------------------------------------------------------ operations

    def topup(
        self,
        user_id: str,
        amount: int,
        currency: Currency,
        provider_tx_id: str,
    ) -> TransactionResult:
        """Пополнение извне. provider_tx_id — ключ идемпотентности.

        Именно здесь был баг: спам кнопкой создавал несколько начислений.
        Теперь повторный вызов с тем же provider_tx_id возвращает прежний
        результат и денег не добавляет.
        """
        if amount <= 0:
            raise PaymentError("top-up amount must be positive")

        return self.post(
            idempotency_key=f"topup:{provider_tx_id}",
            entries=[
                (SYSTEM_GATEWAY, currency, -amount),   # из шлюза
                (user_id, currency, amount),           # пользователю
            ],
            entry_type=EntryType.TOPUP,
            metadata=provider_tx_id,
        )

    def purchase_gift(
        self,
        user_id: str,
        gift_id: str,
        price: int,
        currency: Currency,
        request_id: str,
    ) -> TransactionResult:
        """Покупка подарка. Платить можно и звёздами, и фиатом.

        request_id генерируется клиентом один раз при нажатии кнопки.
        Повторные отправки того же запроса не спишут деньги дважды.
        """
        if price <= 0:
            raise PaymentError("gift price must be positive")

        return self.post(
            idempotency_key=f"gift:{request_id}",
            entries=[
                (user_id, currency, -price),
                (SYSTEM_REVENUE, currency, price),
            ],
            entry_type=EntryType.GIFT_PURCHASE,
            metadata=f"{gift_id}|{request_id}",
        )

    def purchase_vip(
        self,
        user_id: str,
        price: int,
        currency: Currency,
        payment_id: str,
    ) -> TransactionResult:
        if price <= 0:
            raise PaymentError("VIP price must be positive")

        return self.post(
            idempotency_key=f"vip:{payment_id}",
            entries=[
                (user_id, currency, -price),
                (SYSTEM_REVENUE, currency, price),
            ],
            entry_type=EntryType.VIP_SUBSCRIPTION,
            metadata=payment_id,
        )

    def exchange(
        self,
        user_id: str,
        from_currency: Currency,
        from_amount: int,
        to_currency: Currency,
        to_amount: int,
        request_id: str,
    ) -> TransactionResult:
        """Обмен между кошельками — единственный законный способ
        переместить деньги между валютами.

        Две пары проводок: списание в одной валюте и зачисление в другой,
        обе через счёт обменника. Курс фиксируется в metadata, чтобы
        потом можно было разобрать спорную операцию.
        """
        if from_currency is to_currency:
            raise CurrencyMismatch("exchange requires two different currencies")
        if from_amount <= 0 or to_amount <= 0:
            raise PaymentError("exchange amounts must be positive")

        return self.post(
            idempotency_key=f"exchange:{request_id}",
            entries=[
                (user_id, from_currency, -from_amount),
                (SYSTEM_EXCHANGE, from_currency, from_amount),
                (SYSTEM_EXCHANGE, to_currency, -to_amount),
                (user_id, to_currency, to_amount),
            ],
            entry_type=EntryType.EXCHANGE,
            metadata=f"rate:{from_amount}/{to_amount}|{request_id}",
        )

    def refund(
        self,
        user_id: str,
        amount: int,
        currency: Currency,
        original_tx_id: str,
    ) -> TransactionResult:
        """Возврат. Ключ привязан к исходной операции, поэтому
        вернуть деньги дважды за одну покупку нельзя."""
        if amount <= 0:
            raise PaymentError("refund amount must be positive")

        return self.post(
            idempotency_key=f"refund:{original_tx_id}",
            entries=[
                (SYSTEM_REVENUE, currency, -amount),
                (user_id, currency, amount),
            ],
            entry_type=EntryType.REFUND,
            metadata=original_tx_id,
        )

    def transfer(
        self,
        from_user: str,
        to_user: str,
        amount: int,
        currency: Currency,
        request_id: str,
    ) -> TransactionResult:
        """Перевод между пользователями (подарок звёздами)."""
        if from_user == to_user:
            raise PaymentError("cannot transfer to self")
        if amount <= 0:
            raise PaymentError("transfer amount must be positive")

        return self.post(
            idempotency_key=f"transfer:{request_id}",
            entries=[
                (from_user, currency, -amount),
                (to_user, currency, amount),
            ],
            entry_type=EntryType.TRANSFER,
            metadata=request_id,
        )

    # ------------------------------------------------------------ audit

    def verify_integrity(self) -> dict[str, int]:
        """Сверка: сумма всех проводок по каждой валюте должна быть нулём.

        Запускать по расписанию. Ненулевой результат означает, что деньги
        где-то появились или исчезли — то есть в системе дыра.
        """
        with self._lock:
            rows = self._conn.execute(
                "SELECT currency, SUM(amount) FROM ledger_entries GROUP BY currency"
            ).fetchall()
        return {currency: total for currency, total in rows}

    def verify_balances(self) -> list[str]:
        """Сверяет кеш балансов с суммой проводок.

        Возвращает список расхождений. Пустой список = всё сходится.
        """
        problems = []
        with self._lock:
            rows = self._conn.execute(
                "SELECT account, currency, SUM(amount) FROM ledger_entries"
                " GROUP BY account, currency"
            ).fetchall()
            for account, currency, computed in rows:
                cached_row = self._conn.execute(
                    "SELECT amount FROM balances WHERE account = ? AND currency = ?",
                    (account, currency),
                ).fetchone()
                cached = cached_row[0] if cached_row else 0
                if cached != computed:
                    problems.append(
                        f"{account}/{currency}: cached={cached} computed={computed}"
                    )
        return problems
