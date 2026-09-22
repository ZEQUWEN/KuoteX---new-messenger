"""Тесты приёма вебхуков и покупки подарков."""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import sys
import threading
import time

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".."))

from payments.ledger import (  # noqa: E402
    SYSTEM_REVENUE, Currency, PaymentLedger,
)
from payments.webhook import (  # noqa: E402
    GiftPurchaseService, PaymentWebhookProcessor, SignatureError, WebhookConfig,
    WebhookError, verify_signature,
)

SECRET = "kuotex_test_secret_key_2026"


@pytest.fixture
def ledger():
    book = PaymentLedger()
    yield book
    book.close()


@pytest.fixture
def granted():
    return {"vip": [], "stars": []}


@pytest.fixture
def processor(ledger, granted):
    return PaymentWebhookProcessor(
        ledger,
        WebhookConfig(secret=SECRET),
        on_vip_granted=lambda uid, exp, votes: granted["vip"].append((uid, exp, votes)),
        on_stars_credited=lambda uid, amount: granted["stars"].append((uid, amount)),
    )


def sign(payload: dict) -> tuple[bytes, str]:
    raw = json.dumps(payload).encode()
    signature = hmac.new(SECRET.encode(), raw, hashlib.sha256).hexdigest()
    return raw, signature


def vip_payload(payment_id: str = "pay-1", user_id: str = "user1", **extra):
    data = {
        "payment_id": payment_id,
        "user_id": user_id,
        "product": "vip",
        "timestamp": time.time(),
    }
    data.update(extra)
    return data


# ---------------------------------------------------------------- signature

def test_valid_signature_accepted():
    raw, signature = sign({"a": 1})
    assert verify_signature(raw, signature, SECRET)


def test_wrong_signature_rejected():
    raw, _ = sign({"a": 1})
    assert not verify_signature(raw, "deadbeef", SECRET)
    assert not verify_signature(raw, "", SECRET)


def test_tampered_payload_rejected(processor):
    raw, signature = sign(vip_payload())
    tampered = raw.replace(b"user1", b"user2")

    with pytest.raises(SignatureError):
        processor.handle(tampered, signature)


def test_unsigned_request_rejected(processor):
    raw = json.dumps(vip_payload()).encode()
    with pytest.raises(SignatureError):
        processor.handle(raw, "")


def test_weak_secret_rejected():
    with pytest.raises(ValueError, match="16 characters"):
        WebhookConfig(secret="short")


# ---------------------------------------------------------------- vip

def test_vip_purchase_succeeds(processor, ledger, granted):
    ledger.topup("user1", 1000, Currency.STARS, "init")
    raw, signature = sign(vip_payload())

    result = processor.handle(raw, signature)

    assert result["status"] == "success"
    assert not result["replayed"]
    assert result["boost_votes_granted"] == 4
    assert ledger.balance("user1", Currency.STARS) == 700
    assert len(granted["vip"]) == 1


def test_repeated_webhook_does_not_charge_twice(processor, ledger, granted):
    """Провайдер шлёт вебхук повторно — это норма, но платить дважды нельзя."""
    ledger.topup("user1", 1000, Currency.STARS, "init")
    raw, signature = sign(vip_payload())

    processor.handle(raw, signature)
    for _ in range(5):
        repeat = processor.handle(raw, signature)
        assert repeat["replayed"]
        assert repeat["boost_votes_granted"] == 0

    assert ledger.balance("user1", Currency.STARS) == 700
    # Товар выдан ровно один раз.
    assert len(granted["vip"]) == 1


def test_concurrent_vip_webhooks(processor, ledger, granted):
    """Одновременные повторы вебхука списывают деньги один раз."""
    ledger.topup("user1", 1000, Currency.STARS, "init")
    raw, signature = sign(vip_payload())
    barrier = threading.Barrier(15)
    errors = []

    def fire():
        try:
            barrier.wait()
            processor.handle(raw, signature)
        except Exception as exc:     # pragma: no cover
            errors.append(exc)

    threads = [threading.Thread(target=fire) for _ in range(15)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert not errors
    assert ledger.balance("user1", Currency.STARS) == 700
    assert len(granted["vip"]) == 1


def test_amount_tampering_rejected(processor, ledger):
    """Попытка купить VIP за 1 звезду вместо 300."""
    ledger.topup("user1", 1000, Currency.STARS, "init")
    raw, signature = sign(vip_payload(amount=1))

    with pytest.raises(WebhookError) as exc:
        processor.handle(raw, signature)
    assert exc.value.code == 409
    assert ledger.balance("user1", Currency.STARS) == 1000


def test_insufficient_funds_blocks_vip(processor, ledger, granted):
    ledger.topup("user1", 100, Currency.STARS, "init")
    raw, signature = sign(vip_payload())

    with pytest.raises(WebhookError) as exc:
        processor.handle(raw, signature)

    assert exc.value.code == 402
    assert ledger.balance("user1", Currency.STARS) == 100
    # Товар не выдан.
    assert granted["vip"] == []


def test_old_webhook_rejected(processor, ledger):
    """Перехваченный вебхук нельзя переиграть спустя час."""
    ledger.topup("user1", 1000, Currency.STARS, "init")
    raw, signature = sign(vip_payload(timestamp=time.time() - 3600))

    with pytest.raises(WebhookError) as exc:
        processor.handle(raw, signature)
    assert exc.value.code == 410


def test_missing_fields_rejected(processor):
    raw, signature = sign({"timestamp": time.time()})
    with pytest.raises(WebhookError, match="payment_id"):
        processor.handle(raw, signature)


def test_invalid_json_rejected(processor):
    raw = b"{not json"
    signature = hmac.new(SECRET.encode(), raw, hashlib.sha256).hexdigest()
    with pytest.raises(WebhookError, match="JSON"):
        processor.handle(raw, signature)


def test_unknown_product_rejected(processor):
    raw, signature = sign(vip_payload(product="free_money"))
    with pytest.raises(WebhookError, match="unknown product"):
        processor.handle(raw, signature)


# ---------------------------------------------------------------- stars

def test_star_pack_credited(processor, ledger, granted):
    raw, signature = sign(vip_payload(product="stars_500", payment_id="pay-stars"))
    result = processor.handle(raw, signature)

    assert result["stars_credited"] == 500
    assert ledger.balance("user1", Currency.STARS) == 500
    assert granted["stars"] == [("user1", 500)]


def test_star_spam_credits_once(processor, ledger, granted):
    """Тот самый баг: спам кнопкой «купить звёзды»."""
    raw, signature = sign(vip_payload(product="stars_1000", payment_id="pay-x"))
    barrier = threading.Barrier(20)

    def press():
        barrier.wait()
        processor.handle(raw, signature)

    threads = [threading.Thread(target=press) for _ in range(20)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert ledger.balance("user1", Currency.STARS) == 1000, "stars duplicated!"
    assert len(granted["stars"]) == 1


def test_star_pack_price_tampering(processor, ledger):
    raw, signature = sign(
        vip_payload(product="stars_1000", payment_id="pay-y", amount=1)
    )
    with pytest.raises(WebhookError) as exc:
        processor.handle(raw, signature)
    assert exc.value.code == 409
    assert ledger.balance("user1", Currency.STARS) == 0


# ---------------------------------------------------------------- gifts

@pytest.fixture
def gift_service(ledger):
    catalog = {
        "rose": {"price_stars": 50, "price_fiat": {"RUB": 49}, "supply": -1},
        "diamond": {"price_stars": 1000, "price_fiat": {"RUB": 899}, "supply": 3},
        "stars_only": {"price_stars": 200, "supply": -1},
    }
    return GiftPurchaseService(ledger, catalog)


def test_gift_paid_with_stars(gift_service, ledger):
    ledger.topup("user1", 500, Currency.STARS, "init")
    result = gift_service.purchase("user1", "rose", Currency.STARS, "req-1")

    assert result["price"] == 50
    assert ledger.balance("user1", Currency.STARS) == 450


def test_gift_paid_with_fiat(gift_service, ledger):
    """Оплата подарка фиатом с отдельного счёта."""
    ledger.topup("user1", 1000, Currency.FIAT_RUB, "init")
    result = gift_service.purchase("user1", "rose", Currency.FIAT_RUB, "req-1")

    assert result["price"] == 49
    assert ledger.balance("user1", Currency.FIAT_RUB) == 951
    assert ledger.balance("user1", Currency.STARS) == 0


def test_gift_without_fiat_price_rejects_fiat(gift_service, ledger):
    ledger.topup("user1", 1000, Currency.FIAT_RUB, "init")
    with pytest.raises(WebhookError) as exc:
        gift_service.purchase("user1", "stars_only", Currency.FIAT_RUB, "req-1")
    assert exc.value.code == 409


def test_gift_double_tap_charges_once(gift_service, ledger):
    """Двойное нажатие «подарить» списывает один раз."""
    ledger.topup("user1", 500, Currency.STARS, "init")

    first = gift_service.purchase("user1", "rose", Currency.STARS, "req-1")
    second = gift_service.purchase("user1", "rose", Currency.STARS, "req-1")

    assert not first["replayed"]
    assert second["replayed"]
    assert ledger.balance("user1", Currency.STARS) == 450


def test_limited_supply_respected(gift_service, ledger):
    """Лимитированный подарок нельзя купить больше, чем есть."""
    ledger.topup("whale", 10_000, Currency.STARS, "init")

    for i in range(3):
        gift_service.purchase("whale", "diamond", Currency.STARS, f"req-{i}")

    with pytest.raises(WebhookError) as exc:
        gift_service.purchase("whale", "diamond", Currency.STARS, "req-4")
    assert exc.value.code == 410


def test_concurrent_gift_purchases_respect_supply(gift_service, ledger):
    """Гонка за последними экземплярами не должна уводить остаток в минус."""
    ledger.topup("whale", 100_000, Currency.STARS, "init")
    barrier = threading.Barrier(10)
    outcomes = []

    def buy(index: int):
        barrier.wait()
        try:
            gift_service.purchase("whale", "diamond", Currency.STARS, f"c-{index}")
            outcomes.append("ok")
        except WebhookError:
            outcomes.append("sold_out")

    threads = [threading.Thread(target=buy, args=(i,)) for i in range(10)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert outcomes.count("ok") == 3
    assert gift_service.catalog["diamond"]["supply"] == 0


def test_insufficient_funds_blocks_gift(gift_service, ledger):
    ledger.topup("user1", 10, Currency.STARS, "init")
    with pytest.raises(WebhookError) as exc:
        gift_service.purchase("user1", "rose", Currency.STARS, "req-1")
    assert exc.value.code == 402
    assert ledger.balance("user1", Currency.STARS) == 10


def test_unknown_gift_rejected(gift_service):
    with pytest.raises(WebhookError) as exc:
        gift_service.purchase("user1", "nonexistent", Currency.STARS, "req-1")
    assert exc.value.code == 404


def test_full_flow_integrity(gift_service, processor, ledger):
    """Сквозной сценарий: покупка звёзд -> подарок -> сверка сходится."""
    raw, signature = sign(vip_payload(product="stars_1000", payment_id="p1"))
    processor.handle(raw, signature)

    ledger.topup("user1", 2000, Currency.FIAT_RUB, "fiat-1")
    gift_service.purchase("user1", "rose", Currency.STARS, "g1")
    gift_service.purchase("user1", "diamond", Currency.FIAT_RUB, "g2")

    assert ledger.balance("user1", Currency.STARS) == 950
    assert ledger.balance("user1", Currency.FIAT_RUB) == 1101
    # Деньги нигде не потерялись и не появились.
    assert ledger.verify_integrity() == {"STARS": 0, "RUB": 0}
    assert ledger.verify_balances() == []
