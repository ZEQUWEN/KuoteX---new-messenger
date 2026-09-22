from fastapi import FastAPI, Depends, HTTPException, Request
from pydantic import BaseModel
import os
import hmac
import hashlib
import json
import httpx
from sqlalchemy.orm import Session
from sqlalchemy.future import select
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker
from models import Bot, BotCommand, Base

# Placeholder for DB Engine setup
# DATABASE_URL = "postgresql+asyncpg://user:password@localhost/dbname"
# engine = create_async_engine(DATABASE_URL, echo=True)
# async_session = sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)

app = FastAPI(title="KuoteX Bot API Gateway")

class BotFatherEngine:
    @staticmethod
    def generate_token(bot_id: int) -> str:
        """Безопасная генерация токена бота"""
        random_str = os.urandom(32).hex()
        return f"bot{bot_id}:{random_str}"
        
    @staticmethod
    async def create_bot(db: AsyncSession, owner_id: int, name: str, username: str) -> dict:
        """Создание нового бота"""
        new_bot = Bot(owner_id=owner_id, name=name, username=username)
        db.add(new_bot)
        await db.flush() # get ID
        
        token = BotFatherEngine.generate_token(new_bot.id)
        # Hash token for storage, e.g. SHA256
        token_hash = hashlib.sha256(token.encode()).hexdigest()
        new_bot.token_hash = token_hash
        
        await db.commit()
        return {"bot_id": new_bot.id, "token": token}

    @staticmethod
    async def revoke_token(db: AsyncSession, bot_id: int, owner_id: int) -> str:
        """Отзыв токена и генерация нового"""
        result = await db.execute(select(Bot).filter(Bot.id == bot_id, Bot.owner_id == owner_id))
        bot = result.scalars().first()
        if not bot:
            raise HTTPException(status_code=404, detail="Bot not found")
            
        new_token = BotFatherEngine.generate_token(bot.id)
        bot.token_hash = hashlib.sha256(new_token.encode()).hexdigest()
        await db.commit()
        return new_token


class BotProfileSchema(BaseModel):
    name: str = None
    description: str = None
    about: str = None
    botpic_url: str = None

@app.post("/bot{token}/setMyProfile")
async def set_my_profile(token: str, profile: BotProfileSchema):
    """Обновление профиля бота (имя, описание, аватар)"""
    # Здесь мы симулируем логгирование для диагностики
    import logging
    logging.info(f"[Diagnostics] Updating profile for bot with token {token}")
    logging.info(f"[Diagnostics] Profile data received: {profile.dict()}")
    # В реальности тут будет проверка токена и запись в БД
    return {"ok": True, "result": True, "description": "Profile updated successfully"}

@app.get("/bot{token}/diagnostics")
async def bot_diagnostics(token: str):
    """Диагностический эндпоинт для проверки синхронизации профиля бота"""
    import logging
    logging.info(f"[Diagnostics] Requesting diagnostics for token {token}")
    # Возвращаем мок данные для диагностики
    return {
        "ok": True,
        "result": {
            "sync_status": "ok",
            "last_sync": "2026-08-09T00:00:00Z",
            "profile_fields_synced": ["name", "description", "botpic_url"]
        }
    }

class WebhookRequest(BaseModel):

    url: str
    secret_token: str = None

@app.post("/bot{token}/setWebhook")
async def set_webhook(token: str, req: WebhookRequest):
    """Регистрация Webhook URL с валидацией HTTPS"""
    if not req.url.startswith("https://"):
        raise HTTPException(status_code=400, detail="HTTPS required for webhook")
    
    # В реальном приложении: проверка токена в БД и сохранение webhook_url/secret
    return {"ok": True, "result": True, "description": "Webhook was set"}

class BotCommandSchema(BaseModel):
    command: str
    description: str

@app.post("/bot{token}/setMyCommands")
async def set_my_commands(token: str, commands: list[BotCommandSchema]):
    """Сохранение списка команд бота"""
    # Валидация токена, удаление старых команд, сохранение новых
    return {"ok": True, "result": True}

@app.get("/bot{token}/getUpdates")
async def get_updates(token: str, offset: int = None, limit: int = 100):
    """Реализация Long Polling через Redis Pub/Sub для ботов без вебхука"""
    # Ожидание обновлений через asyncio/redis pub-sub
    updates = [] # Fetch updates from Redis queue
    return {"ok": True, "result": updates}

async def webhook_dispatcher(bot_id: int, webhook_url: str, secret_token: str, update: dict):
    """
    Асинхронный диспетчер, который пересылает сообщения от KuoteX на webhook_url бота.
    Добавлена проверка подписи HMAC-SHA256 для безопасности (X-KuoteX-Signature).
    """
    payload_json = json.dumps(update)
    headers = {"Content-Type": "application/json"}
    
    if secret_token:
        headers["X-Telegram-Bot-Api-Secret-Token"] = secret_token
        # Дополнительно подпишем полезную нагрузку
        signature = hmac.new(
            secret_token.encode('utf-8'),
            payload_json.encode('utf-8'),
            hashlib.sha256
        ).hexdigest()
        headers["X-KuoteX-Signature"] = signature

    async with httpx.AsyncClient() as client:
        try:
            response = await client.post(webhook_url, content=payload_json, headers=headers, timeout=10.0)
            return response.status_code == 200
        except httpx.RequestError as exc:
            print(f"An error occurred while requesting {exc.request.url!r}.")
            return False
