"""Приём платёжных вебхуков KuoteX VIP bot.

Поверх леджера добавляет то, что нужно именно вебхукам:
  * проверку подписи за постоянное время;
  * защиту от повторной отправки (провайдеры шлют вебхук по несколько раз —
    это нормальное поведение, а не ошибка);
  * защиту от подмены суммы и валюты;
  * выдачу товара (VIP, звёзды) только после успешной проводки.

Важный принцип: сначала деньги, потом товар. Если проводка не прошла,
никакой выдачи не происходит.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import logging
import time
from dataclasses import dataclass
from typing import Any, Callable

from .ledger import (
    Currency, DuplicateOperation, InsufficientFunds, PaymentError,
    PaymentLedger, TransactionResult,
)

log = logging.getLogger("kuotex.payments.webhook")

VIP_PRICE_STARS = 300
VIP_DURATION_DAYS = 30
VIP_DURATION_MS = VIP_DURATION_DAYS * 24 * 60 * 60 * 1000
VIP_BOOST_VOTES = 4

# Максимальный возраст вебхука. Старые отбрасываем: это защита от
# повторной отправки перехваченного запроса спустя время.
MAX_WEBHOOK_AGE_SECONDS = 300


class WebhookError(Exception):
    def __init__(self, message: str, code: int = 400):
        super().__init__(message)
        self.code = code
        self.message = message


class SignatureError(WebhookError):
    def __init__(self, message: str = "invalid signature"):
        super().__init__(message, code=401)


@dataclass(slots=True)
class WebhookConfig:
    secret: str
    # Прайс-лист на сервере. Клиент не может назначить свою цену.
    vip_price_stars: int = VIP_PRICE_STARS
    star_packs: dict[str, tuple[int, int, Currency]] = None  # id -> (звёзды, цена, валюта)
    verify_timestamp: bool = True

    def __post_init__(self):
        if not self.secret or len(self.secret) < 16:
            raise ValueError("webhook secret must be at least 16 characters")
        if self.star_packs is None:
            # Пакеты звёзд: сколько начисляем и сколько стоит.
            self.star_packs = {
                "stars_100": (100, 99, Currency.FIAT_RUB),
                "stars_500": (500, 449, Currency.FIAT_RUB),
                "stars_1000": (1000, 849, Currency.FIAT_RUB),
            }


def verify_signature(payload: bytes, signature: str, secret: str) -> bool:
    """HMAC-SHA256 с постоянным временем сравнения.

    Обычное `==` выходит на первом различии и позволяет подобрать
    подпись по времени ответа.
    """
    if not signature:
        return False
    expected = hmac.new(
        key=secret.encode("utf-8"), msg=payload, digestmod=hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(expected, signature)


class PaymentWebhookProcessor:
    """Обработчик вебхуков от платёжного провайдера."""

    def __init__(
        self,
        ledger: PaymentLedger,
        config: WebhookConfig,
        on_vip_granted: Callable[[str, int, int], Any] | None = None,
        on_stars_credited: Callable[[str, int], Any] | None = None,
    ):
        self.ledger = ledger
        self.config = config
        self._on_vip = on_vip_granted
        self._on_stars = on_stars_credited

    # ------------------------------------------------------------ entry

    def handle(self, raw_payload: bytes, signature: str) -> dict[str, Any]:
        """Точка входа. Возвращает ответ для провайдера."""
        # 1. Подпись — до любого разбора. Невалидный запрос не должен
        #    даже парситься: это лишняя поверхность атаки.
        if not verify_signature(raw_payload, signature, self.config.secret):
            raise SignatureError()

        try:
            data = json.loads(raw_payload.decode("utf-8"))
        except Exception as exc:
            raise WebhookError(f"invalid JSON: {exc}") from exc

        if not isinstance(data, dict):
            raise WebhookError("payload must be a JSON object")

        # 2. Возраст запроса.
        if self.config.verify_timestamp:
            self._check_timestamp(data)

        payment_id = data.get("payment_id") or data.get("provider_payment_charge_id")
        user_id = data.get("user_id")
        if not payment_id or not user_id:
            raise WebhookError("missing payment_id or user_id")

        product = data.get("product") or data.get("invoice_payload") or "vip"

        if product == "vip":
            return self._fulfill_vip(str(user_id), str(payment_id), data)
        if product in self.config.star_packs:
            return self._fulfill_stars(str(user_id), str(payment_id), product, data)

        raise WebhookError(f"unknown product: {product}")

    def _check_timestamp(self, data: dict) -> None:
        raw = data.get("timestamp")
        if raw is None:
            raise WebhookError("missing timestamp")
        try:
            timestamp = float(raw)
        except (TypeError, ValueError) as exc:
            raise WebhookError("invalid timestamp") from exc

        # Провайдеры шлют и в секундах, и в миллисекундах.
        if timestamp > 1e12:
            timestamp /= 1000.0

        age = time.time() - timestamp
        if age > MAX_WEBHOOK_AGE_SECONDS:
            raise WebhookError(f"webhook too old ({int(age)}s)", code=410)
        if age < -MAX_WEBHOOK_AGE_SECONDS:
            raise WebhookError("webhook timestamp is in the future")

    # ------------------------------------------------------------ vip

    def _fulfill_vip(
        self, user_id: str, payment_id: str, data: dict
    ) -> dict[str, Any]:
        """Выдача VIP. Цену берём из конфига, а не из запроса."""
        price = self.config.vip_price_stars
        currency = Currency.STARS

        # Если провайдер прислал сумму, она обязана совпасть с нашей.
        # Иначе это попытка купить VIP за 1 звезду.
        claimed = data.get("amount")
        if claimed is not None and int(claimed) != price:
            raise WebhookError(
                f"amount mismatch: expected {price}, got {claimed}", code=409
            )

        try:
            result = self.ledger.purchase_vip(user_id, price, currency, payment_id)
        except InsufficientFunds as exc:
            raise WebhookError(str(exc), code=402) from exc

        now_ms = int(time.time() * 1000)
        expiration = now_ms + VIP_DURATION_MS

        # Выдаём товар только если проводка была настоящей.
        # На повторе возвращаем прежний ответ, ничего не начисляя.
        if not result.replayed and self._on_vip is not None:
            self._on_vip(user_id, expiration, VIP_BOOST_VOTES)

        return {
            "status": "success",
            "replayed": result.replayed,
            "tx_id": result.tx_id,
            "payment_id": payment_id,
            "user_id": user_id,
            "vip_expiration": expiration,
            "boost_votes_granted": 0 if result.replayed else VIP_BOOST_VOTES,
            "balance_stars": self.ledger.balance(user_id, Currency.STARS),
        }

    # ------------------------------------------------------------ stars

    def _fulfill_stars(
        self, user_id: str, payment_id: str, pack_id: str, data: dict
    ) -> dict[str, Any]:
        """Начисление звёзд после оплаты фиатом.

        Тот самый сценарий с кнопкой «купить звёзды»: сколько бы раз
        провайдер ни прислал вебхук, начисление произойдёт один раз.
        """
        stars, price, price_currency = self.config.star_packs[pack_id]

        claimed = data.get("amount")
        if claimed is not None and int(claimed) != price:
            raise WebhookError(
                f"amount mismatch: expected {price}, got {claimed}", code=409
            )

        result = self.ledger.topup(user_id, stars, Currency.STARS, payment_id)

        if not result.replayed and self._on_stars is not None:
            self._on_stars(user_id, stars)

        return {
            "status": "success",
            "replayed": result.replayed,
            "tx_id": result.tx_id,
            "payment_id": payment_id,
            "user_id": user_id,
            "stars_credited": 0 if result.replayed else stars,
            "balance_stars": self.ledger.balance(user_id, Currency.STARS),
        }


# ---------------------------------------------------------------- gifts

class GiftPurchaseService:
    """Покупка подарков за звёзды или фиат.

    Проверяет наличие товара и списывает деньги одной операцией:
    подарок не может «кончиться» между проверкой и оплатой.
    """

    def __init__(self, ledger: PaymentLedger,
                 catalog: dict[str, dict[str, Any]] | None = None):
        self.ledger = ledger
        # catalog: gift_id -> {"price_stars": int, "price_fiat": {...}, "supply": int}
        self.catalog = catalog or {}
        self._supply_lock = __import__("threading").RLock()

    def purchase(
        self,
        user_id: str,
        gift_id: str,
        currency: Currency,
        request_id: str,
        recipient_id: str | None = None,
    ) -> dict[str, Any]:
        """request_id генерируется клиентом один раз при нажатии кнопки."""
        gift = self.catalog.get(gift_id)
        if gift is None:
            raise WebhookError(f"unknown gift: {gift_id}", code=404)

        price = self._price_of(gift, currency)
        if price is None:
            raise WebhookError(
                f"gift {gift_id} cannot be paid with {currency.value}", code=409
            )

        with self._supply_lock:
            supply = gift.get("supply", -1)
            if supply == 0:
                raise WebhookError(f"gift {gift_id} is sold out", code=410)

            try:
                result = self.ledger.purchase_gift(
                    user_id, gift_id, price, currency, request_id
                )
            except InsufficientFunds as exc:
                raise WebhookError(str(exc), code=402) from exc

            # Уменьшаем остаток только при настоящей покупке.
            if not result.replayed and supply > 0:
                gift["supply"] = supply - 1

        return {
            "status": "success",
            "replayed": result.replayed,
            "tx_id": result.tx_id,
            "gift_id": gift_id,
            "recipient_id": recipient_id or user_id,
            "price": price,
            "currency": currency.value,
            "balance": self.ledger.balance(user_id, currency),
            "remaining_supply": gift.get("supply", -1),
        }

    @staticmethod
    def _price_of(gift: dict, currency: Currency) -> int | None:
        if currency is Currency.STARS:
            return gift.get("price_stars")
        fiat = gift.get("price_fiat") or {}
        return fiat.get(currency.value)
