import re

content = """from abc import ABC, abstractmethod
import json
import uuid

class BasePaymentGateway(ABC):
    @abstractmethod
    async def create_payment(self, amount: float, currency: str, description: str, return_url: str) -> dict:
        \"\"\"Создает платежную сессию и возвращает URL для оплаты\"\"\"
        pass
        
    @abstractmethod
    async def handle_webhook(self, payload: dict, signature: str) -> bool:
        \"\"\"Обрабатывает входящий вебхук от провайдера\"\"\"
        pass

class YooKassaGateway(BasePaymentGateway):
    def __init__(self, shop_id: str, secret_key: str):
        self.shop_id = shop_id
        self.secret_key = secret_key
        # В реальном проекте: Configuration.configure(shop_id, secret_key) из yookassa SDK

    async def create_payment(self, amount, currency, description, return_url):
        # Имитация создания платежа через YooKassa API (/v3/payments)
        # В проде использовать Payment.create(...)
        payment_id = f"yoo_{uuid.uuid4().hex[:10]}"
        return {
            "id": payment_id, 
            "url": f"https://yoomoney.ru/checkout/payments/v2/contract?orderId={payment_id}"
        }
        
    async def handle_webhook(self, payload, signature):
        # Обработка события payment.succeeded от ЮKassa
        # Обновление статуса транзакции в bot_transactions и отправка successful_payment боту
        event_type = payload.get("event")
        if event_type == "payment.succeeded":
            payment_obj = payload.get("object", {})
            payment_id = payment_obj.get("id")
            # TODO: Запросить БД, обновить статус транзакции
            print(f"Payment {payment_id} succeeded.")
        return True

class StripeGateway(BasePaymentGateway):
    def __init__(self, api_key: str):
        self.api_key = api_key

    async def create_payment(self, amount, currency, description, return_url):
        # Имплементация Checkout Session (Stripe API)
        return {
            "id": "cs_test_12345", 
            "url": "https://checkout.stripe.com/c/pay/cs_test_12345"
        }
        
    async def handle_webhook(self, payload, signature):
        # Валидация подписи Stripe (stripe.Webhook.construct_event)
        return True

class DonationAlertsGateway(BasePaymentGateway):
    async def create_payment(self, amount, currency, description, return_url):
        return {"url": "https://www.donationalerts.com/r/bot_owner"}
    
    async def handle_webhook(self, payload, signature):
        return True

class KoFiGateway(BasePaymentGateway):
    async def create_payment(self, amount, currency, description, return_url):
        return {"url": "https://ko-fi.com/bot_owner"}
    
    async def handle_webhook(self, payload, signature):
        return True
"""
with open("backend/payments.py", "w") as f:
    f.write(content)
