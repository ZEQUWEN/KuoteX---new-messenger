"""Тесты платёжного леджера.

Главный тест — `test_spam_button_does_not_duplicate_money`: он
воспроизводит ровно тот баг, который Александр нашёл в эмуляторе,
и доказывает, что теперь деньги не дублируются.
"""
from __future__ import annotations

import os
import sys
import tempfile
import threading

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".."))

from payments.ledger import (  # noqa: E402
    SYSTEM_EXCHANGE, SYSTEM_GATEWAY, SYSTEM_REVENUE, Currency,
    CurrencyMismatch, DuplicateOperation, EntryType, InsufficientFunds,
    PaymentError, PaymentLedger,
)


@pytest.fixture
def ledger():
    book = PaymentLedger()
    yield book
    book.close()


@pytest.fixture
def disk_ledger():
    with tempfile.TemporaryDirectory() as tmp:
        book = PaymentLedger(os.path.join(tmp, "ledger.db"))
        yield book
        book.close()


# ---------------------------------------------------------------- topup

def test_topup_credits_balance(ledger):
    result = ledger.topup("user1", 500, Currency.STARS, "provider-tx-1")
    assert not result.replayed
    assert ledger.balance("user1", Currency.STARS) == 500


def test_topup_is_idempotent(ledger):
    """Повтор вебхука не должен начислять деньги заново."""
    ledger.topup("user1", 500, Currency.STARS, "provider-tx-1")
    replay = ledger.topup("user1", 500, Currency.STARS, "provider-tx-1")

    assert replay.replayed
    assert ledger.balance("user1", Currency.STARS) == 500


def test_different_provider_ids_credit_separately(ledger):
    ledger.topup("user1", 100, Currency.STARS, "tx-1")
    ledger.topup("user1", 100, Currency.STARS, "tx-2")
    assert ledger.balance("user1", Currency.STARS) == 200


def test_topup_rejects_non_positive(ledger):
    for bad in (0, -100):
        with pytest.raises(PaymentError, match="positive"):
            ledger.topup("user1", bad, Currency.STARS, f"tx-{bad}")


# ---------------------------------------------------------------- главный тест

def test_spam_button_does_not_duplicate_money(ledger):
    """Воспроизведение бага: спам кнопкой «купить звёзды».

    20 потоков одновременно шлют один и тот же платёж. Раньше несколько
    из них успевали прочитать старый баланс и записать увеличенный —
    пользователь получал больше, чем оплатил.

    Теперь начисление должно произойти ровно один раз.
    """
    results = []
    errors = []
    barrier = threading.Barrier(20)

    def press_button():
        try:
            barrier.wait()          # жмём все одновременно
            results.append(ledger.topup("victim", 1000, Currency.STARS, "payment-42"))
        except Exception as exc:     # pragma: no cover
            errors.append(exc)

    threads = [threading.Thread(target=press_button) for _ in range(20)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert not errors, f"unexpected errors: {errors}"
    assert ledger.balance("victim", Currency.STARS) == 1000, "money was duplicated!"

    # Ровно одна операция была настоящей, остальные — повторы.
    real = [r for r in results if not r.replayed]
    assert len(real) == 1
    assert len(results) == 20

    # И в леджере ровно одна пара проводок.
    assert len(ledger.history("victim")) == 1
    assert ledger.verify_integrity() == {"STARS": 0}


def test_concurrent_purchases_cannot_overdraw(ledger):
    """Нельзя потратить больше, чем есть, даже параллельными запросами."""
    ledger.topup("buyer", 100, Currency.STARS, "tx-init")

    outcomes = []
    barrier = threading.Barrier(10)

    def buy(index: int):
        barrier.wait()
        try:
            ledger.purchase_gift("buyer", "gift-1", 100, Currency.STARS,
                                 request_id=f"req-{index}")
            outcomes.append("ok")
        except InsufficientFunds:
            outcomes.append("rejected")

    threads = [threading.Thread(target=buy, args=(i,)) for i in range(10)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    # Денег хватило ровно на одну покупку.
    assert outcomes.count("ok") == 1
    assert outcomes.count("rejected") == 9
    assert ledger.balance("buyer", Currency.STARS) == 0


# ---------------------------------------------------------------- раздельные счета

def test_wallets_are_separate(ledger):
    """Звёзды и фиат — разные кошельки, не смешиваются."""
    ledger.topup("user1", 500, Currency.STARS, "tx-stars")
    ledger.topup("user1", 300, Currency.FIAT_RUB, "tx-rub")

    assert ledger.balance("user1", Currency.STARS) == 500
    assert ledger.balance("user1", Currency.FIAT_RUB) == 300
    assert ledger.balance("user1", Currency.FIAT_USD) == 0

    assert ledger.balances_of("user1") == {"STARS": 500, "RUB": 300}


def test_stars_cannot_pay_for_fiat_purchase(ledger):
    """Звёзды на счету не помогают оплатить рублёвую покупку."""
    ledger.topup("user1", 10_000, Currency.STARS, "tx-stars")

    with pytest.raises(InsufficientFunds):
        ledger.purchase_gift("user1", "gift-1", 100, Currency.FIAT_RUB,
                             request_id="req-1")


def test_gift_paid_with_stars(ledger):
    ledger.topup("user1", 500, Currency.STARS, "tx-1")
    ledger.purchase_gift("user1", "rose", 150, Currency.STARS, "req-1")

    assert ledger.balance("user1", Currency.STARS) == 350
    assert ledger.balance(SYSTEM_REVENUE, Currency.STARS) == 150


def test_gift_paid_with_fiat(ledger):
    """Покупка подарка фиатом — то, что ты просил."""
    ledger.topup("user1", 1000, Currency.FIAT_RUB, "tx-1")
    ledger.purchase_gift("user1", "diamond", 750, Currency.FIAT_RUB, "req-1")

    assert ledger.balance("user1", Currency.FIAT_RUB) == 250
    assert ledger.balance(SYSTEM_REVENUE, Currency.FIAT_RUB) == 750
    # Звёздный кошелёк не тронут.
    assert ledger.balance("user1", Currency.STARS) == 0


def test_gift_purchase_is_idempotent(ledger):
    """Двойное нажатие «подарить» списывает деньги один раз."""
    ledger.topup("user1", 500, Currency.STARS, "tx-1")

    first = ledger.purchase_gift("user1", "rose", 150, Currency.STARS, "req-1")
    second = ledger.purchase_gift("user1", "rose", 150, Currency.STARS, "req-1")

    assert not first.replayed
    assert second.replayed
    assert ledger.balance("user1", Currency.STARS) == 350


def test_insufficient_funds_blocks_purchase(ledger):
    ledger.topup("user1", 50, Currency.STARS, "tx-1")

    with pytest.raises(InsufficientFunds) as exc:
        ledger.purchase_gift("user1", "rose", 150, Currency.STARS, "req-1")

    assert exc.value.need == 150
    assert exc.value.have == 50
    # Неудачная покупка не должна оставить следов.
    assert ledger.balance("user1", Currency.STARS) == 50
    assert ledger.balance(SYSTEM_REVENUE, Currency.STARS) == 0


# ---------------------------------------------------------------- exchange

def test_exchange_fiat_to_stars(ledger):
    """Обмен — единственный законный способ переместить деньги
    между кошельками."""
    ledger.topup("user1", 1000, Currency.FIAT_RUB, "tx-1")
    ledger.exchange("user1", Currency.FIAT_RUB, 100,
                    Currency.STARS, 500, request_id="ex-1")

    assert ledger.balance("user1", Currency.FIAT_RUB) == 900
    assert ledger.balance("user1", Currency.STARS) == 500
    assert ledger.verify_integrity() == {"RUB": 0, "STARS": 0}


def test_exchange_is_idempotent(ledger):
    ledger.topup("user1", 1000, Currency.FIAT_RUB, "tx-1")
    ledger.exchange("user1", Currency.FIAT_RUB, 100, Currency.STARS, 500, "ex-1")
    replay = ledger.exchange("user1", Currency.FIAT_RUB, 100,
                             Currency.STARS, 500, "ex-1")

    assert replay.replayed
    assert ledger.balance("user1", Currency.STARS) == 500


def test_exchange_requires_funds(ledger):
    with pytest.raises(InsufficientFunds):
        ledger.exchange("user1", Currency.FIAT_RUB, 100,
                        Currency.STARS, 500, "ex-1")


def test_exchange_rejects_same_currency(ledger):
    with pytest.raises(CurrencyMismatch):
        ledger.exchange("user1", Currency.STARS, 10,
                        Currency.STARS, 10, "ex-1")


# ---------------------------------------------------------------- transfer

def test_transfer_between_users(ledger):
    ledger.topup("alice", 500, Currency.STARS, "tx-1")
    ledger.transfer("alice", "bob", 200, Currency.STARS, "tr-1")

    assert ledger.balance("alice", Currency.STARS) == 300
    assert ledger.balance("bob", Currency.STARS) == 200


def test_transfer_is_idempotent(ledger):
    ledger.topup("alice", 500, Currency.STARS, "tx-1")
    ledger.transfer("alice", "bob", 200, Currency.STARS, "tr-1")
    ledger.transfer("alice", "bob", 200, Currency.STARS, "tr-1")

    assert ledger.balance("bob", Currency.STARS) == 200


def test_transfer_to_self_rejected(ledger):
    with pytest.raises(PaymentError, match="self"):
        ledger.transfer("alice", "alice", 100, Currency.STARS, "tr-1")


# ---------------------------------------------------------------- vip / refund

def test_vip_purchase(ledger):
    ledger.topup("user1", 500, Currency.STARS, "tx-1")
    ledger.purchase_vip("user1", 300, Currency.STARS, "pay-1")

    assert ledger.balance("user1", Currency.STARS) == 200
    assert ledger.balance(SYSTEM_REVENUE, Currency.STARS) == 300


def test_vip_purchase_idempotent(ledger):
    """Спам кнопкой «купить VIP» списывает один раз."""
    ledger.topup("user1", 1000, Currency.STARS, "tx-1")
    for _ in range(5):
        ledger.purchase_vip("user1", 300, Currency.STARS, "pay-1")

    assert ledger.balance("user1", Currency.STARS) == 700


def test_refund_returns_money(ledger):
    ledger.topup("user1", 500, Currency.STARS, "tx-1")
    purchase = ledger.purchase_gift("user1", "rose", 150, Currency.STARS, "req-1")
    ledger.refund("user1", 150, Currency.STARS, purchase.tx_id)

    assert ledger.balance("user1", Currency.STARS) == 500
    assert ledger.balance(SYSTEM_REVENUE, Currency.STARS) == 0


def test_double_refund_is_blocked(ledger):
    """Вернуть деньги дважды за одну покупку нельзя."""
    ledger.topup("user1", 500, Currency.STARS, "tx-1")
    purchase = ledger.purchase_gift("user1", "rose", 150, Currency.STARS, "req-1")

    ledger.refund("user1", 150, Currency.STARS, purchase.tx_id)
    ledger.refund("user1", 150, Currency.STARS, purchase.tx_id)

    assert ledger.balance("user1", Currency.STARS) == 500


# ---------------------------------------------------------------- integrity

def test_double_entry_always_balances(ledger):
    ledger.topup("alice", 1000, Currency.STARS, "tx-1")
    ledger.topup("bob", 500, Currency.FIAT_RUB, "tx-2")
    ledger.purchase_gift("alice", "rose", 100, Currency.STARS, "req-1")
    ledger.transfer("alice", "bob", 50, Currency.STARS, "tr-1")
    ledger.exchange("bob", Currency.FIAT_RUB, 100, Currency.STARS, 200, "ex-1")

    # Сумма всех проводок по каждой валюте = 0. Деньги не появились
    # из воздуха и не исчезли.
    assert ledger.verify_integrity() == {"RUB": 0, "STARS": 0}
    assert ledger.verify_balances() == []


def test_cached_balance_matches_entries(ledger):
    for i in range(20):
        ledger.topup("user1", 10, Currency.STARS, f"tx-{i}")
    for i in range(5):
        ledger.purchase_gift("user1", "rose", 10, Currency.STARS, f"req-{i}")

    assert ledger.balance("user1", Currency.STARS) == 150
    assert ledger.verify_balances() == []


def test_history_records_operations(ledger):
    ledger.topup("user1", 500, Currency.STARS, "tx-1")
    ledger.purchase_gift("user1", "rose", 150, Currency.STARS, "req-1")

    history = ledger.history("user1")
    assert len(history) == 2
    assert history[0].entry_type is EntryType.GIFT_PURCHASE
    assert history[0].amount == -150
    assert history[1].entry_type is EntryType.TOPUP
    assert history[1].amount == 500


def test_ledger_survives_restart(disk_ledger):
    """Балансы переживают перезапуск процесса."""
    disk_ledger.topup("user1", 777, Currency.STARS, "tx-1")
    path = disk_ledger._conn.execute("PRAGMA database_list").fetchone()[2]
    disk_ledger.close()

    reopened = PaymentLedger(path)
    assert reopened.balance("user1", Currency.STARS) == 777
    assert reopened.verify_balances() == []
    reopened.close()


def test_mixed_concurrent_load(ledger):
    """Смешанная нагрузка не должна нарушить сведение баланса."""
    ledger.topup("user1", 10_000, Currency.STARS, "init")

    def worker(index: int):
        try:
            ledger.purchase_gift("user1", "rose", 10, Currency.STARS,
                                 request_id=f"buy-{index}")
        except InsufficientFunds:
            pass

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(50)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert ledger.balance("user1", Currency.STARS) == 10_000 - 50 * 10
    assert ledger.verify_integrity() == {"STARS": 0}
    assert ledger.verify_balances() == []


def test_system_accounts_may_go_negative(ledger):
    """Системные счета уходят в минус: это «выдано в систему»."""
    ledger.topup("user1", 100, Currency.STARS, "tx-1")
    assert ledger.balance(SYSTEM_GATEWAY, Currency.STARS) == -100


def test_user_accounts_cannot_go_negative(ledger):
    with pytest.raises(InsufficientFunds):
        ledger.purchase_gift("poor", "rose", 1, Currency.STARS, "req-1")
    assert ledger.balance("poor", Currency.STARS) == 0
