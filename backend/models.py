from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy import Column, Integer, String, Boolean, DateTime, Float, ForeignKey
from datetime import datetime

Base = declarative_base()

class Bot(Base):
    __tablename__ = 'bots'
    
    id = Column(Integer, primary_key=True, index=True)
    owner_id = Column(Integer, index=True)
    token_hash = Column(String, unique=True, index=True)
    name = Column(String)
    username = Column(String, unique=True)
    about = Column(String, nullable=True)
    description = Column(String, nullable=True)
    botpic_url = Column(String, nullable=True)
    is_inline = Column(Boolean, default=False)
    webhook_url = Column(String, nullable=True)
    webhook_secret = Column(String, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

class BotCommand(Base):
    __tablename__ = 'bot_commands'
    
    id = Column(Integer, primary_key=True, index=True)
    bot_id = Column(Integer, ForeignKey('bots.id'))
    command = Column(String)
    description = Column(String)
    scope = Column(String, default='default') # default, all_private_chats, all_group_chats, chat_administrators

class BotMiniApp(Base):
    __tablename__ = 'bot_mini_apps'
    
    id = Column(Integer, primary_key=True, index=True)
    bot_id = Column(Integer, ForeignKey('bots.id'))
    short_name = Column(String)
    title = Column(String)
    description = Column(String)
    photo_url = Column(String, nullable=True)
    url = Column(String)

class BotPaymentProvider(Base):
    __tablename__ = 'bot_payment_providers'
    
    id = Column(Integer, primary_key=True, index=True)
    bot_id = Column(Integer, ForeignKey('bots.id'))
    provider_type = Column(String) # yookassa, stripe, donationalerts, kofi
    credentials_json = Column(String) # encrypted credentials
    is_active = Column(Boolean, default=True)

class BotTransaction(Base):
    __tablename__ = 'bot_transactions'
    
    id = Column(Integer, primary_key=True, index=True)
    bot_id = Column(Integer, ForeignKey('bots.id'))
    user_id = Column(Integer)
    amount = Column(Float)
    currency = Column(String)
    provider = Column(String)
    status = Column(String, default='pending') # pending, paid, failed
    external_tx_id = Column(String, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
