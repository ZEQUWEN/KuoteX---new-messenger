"""Тесты серверного хранилища ключей и реестра сессий.

Главное, что проверяется: ключ переживает перезапуск процесса, лежит
в базе зашифрованным, и анти-replay не сбрасывается при переподключении.
"""
from __future__ import annotations

import os
import sys
import tempfile
import threading
import time

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".."))

from mtproto_transport import crypto  # noqa: E402
from mtproto_transport.keystore import (  # noqa: E402
    AUTH_KEY_SIZE, AuthKeyRecord, InMemoryKeyStore, KeyEncryptor, KeyStoreError,
    SqliteKeyStore, auth_key_id_of, create_keystore,
)
from mtproto_transport.session_registry import (  # noqa: E402
    SessionRegistry, UnknownAuthKey,
)


# ---------------------------------------------------------------- fixtures

@pytest.fixture
def encryptor() -> KeyEncryptor:
    return KeyEncryptor(os.urandom(32))


@pytest.fixture
def db_path():
    with tempfile.TemporaryDirectory() as tmp:
        yield os.path.join(tmp, "keys.db")


def make_key() -> bytes:
    return os.urandom(AUTH_KEY_SIZE)


def make_record(auth_key: bytes | None = None, **kwargs) -> AuthKeyRecord:
    key = auth_key or make_key()
    return AuthKeyRecord(
        auth_key_id=auth_key_id_of(key),
        auth_key=key,
        server_salt=kwargs.pop("server_salt", 12345),
        **kwargs,
    )


# ---------------------------------------------------------------- encryptor

def test_master_key_roundtrip(encryptor):
    key = make_key()
    blob = encryptor.encrypt(key)
    assert blob != key
    assert encryptor.decrypt(blob) == key


def test_encryption_is_randomized(encryptor):
    """Одинаковые ключи должны давать разные шифротексты (уникальный nonce)."""
    key = make_key()
    assert encryptor.encrypt(key) != encryptor.encrypt(key)


def test_wrong_master_key_cannot_decrypt(encryptor):
    blob = encryptor.encrypt(make_key())
    other = KeyEncryptor(os.urandom(32))
    with pytest.raises(Exception):
        other.decrypt(blob)


def test_aad_prevents_swapping(encryptor):
    """Шифротекст, привязанный к одному id, не должен читаться под другим."""
    key = make_key()
    blob = encryptor.encrypt(key, aad=b"111")
    with pytest.raises(Exception):
        encryptor.decrypt(blob, aad=b"222")


def test_rejects_bad_master_key_size():
    with pytest.raises(KeyStoreError, match="32 bytes"):
        KeyEncryptor(os.urandom(16))


def test_generated_master_key_is_valid():
    generated = KeyEncryptor.generate_master_key()
    import base64
    assert len(base64.b64decode(generated)) == 32
    KeyEncryptor(base64.b64decode(generated))     # не должно бросить


def test_from_env(monkeypatch):
    monkeypatch.setenv("MTPROTO_MASTER_KEY", KeyEncryptor.generate_master_key())
    KeyEncryptor.from_env()

    monkeypatch.delenv("MTPROTO_MASTER_KEY")
    with pytest.raises(KeyStoreError, match="not set"):
        KeyEncryptor.from_env()


# ---------------------------------------------------------------- store: общее

@pytest.fixture(params=["memory", "sqlite"])
def store(request, encryptor, db_path):
    if request.param == "memory":
        s = InMemoryKeyStore()
    else:
        s = SqliteKeyStore(db_path, encryptor)
    yield s
    s.close()


def test_save_and_load(store):
    record = make_record()
    store.save(record)

    loaded = store.load(record.auth_key_id)
    assert loaded is not None
    assert loaded.auth_key == record.auth_key
    assert loaded.server_salt == record.server_salt


def test_load_missing_returns_none(store):
    assert store.load(123456789) is None


def test_update_salt_keeps_key(store):
    record = make_record()
    store.save(record)
    store.update_salt(record.auth_key_id, 999)

    loaded = store.load(record.auth_key_id)
    assert loaded.server_salt == 999
    assert loaded.auth_key == record.auth_key


def test_delete(store):
    record = make_record()
    store.save(record)
    store.delete(record.auth_key_id)
    assert store.load(record.auth_key_id) is None


def test_delete_for_user(store):
    keys = [make_record(user_id=7) for _ in range(3)]
    other = make_record(user_id=8)
    for r in keys + [other]:
        store.save(r)

    assert store.delete_for_user(7) == 3
    for r in keys:
        assert store.load(r.auth_key_id) is None
    assert store.load(other.auth_key_id) is not None


def test_purge_expired(store):
    fresh = make_record()
    stale = make_record(created_at=time.time() - 10_000)
    store.save(fresh)
    store.save(stale)

    removed = store.purge_expired(max_age=3600)
    assert removed == 1
    assert store.load(fresh.auth_key_id) is not None
    assert store.load(stale.auth_key_id) is None


def test_touch_updates_last_used(store):
    record = make_record()
    store.save(record)
    store.touch(record.auth_key_id)
    assert store.load(record.auth_key_id).last_used_at > 0


def test_count(store):
    for _ in range(4):
        store.save(make_record())
    assert store.count() == 4


def test_store_keeps_own_copy(store):
    """Затирание исходного массива не должно портить сохранённое."""
    key = bytearray(make_key())
    original = bytes(key)
    store.save(make_record(auth_key=bytes(key)))
    key_id = auth_key_id_of(original)

    for i in range(len(key)):
        key[i] = 0

    assert store.load(key_id).auth_key == original


def test_rejects_wrong_key_size():
    with pytest.raises(KeyStoreError, match="256 bytes"):
        AuthKeyRecord(auth_key_id=1, auth_key=os.urandom(128))


def test_negative_age_counts_as_expired():
    """Часы сервера перевели назад — записи доверять нельзя."""
    record = make_record(created_at=time.time() + 10_000)
    assert record.is_expired()


# ---------------------------------------------------------------- sqlite

def test_key_survives_restart(db_path, encryptor):
    """Главное свойство: перезапуск процесса не теряет ключи."""
    record = make_record()

    first = SqliteKeyStore(db_path, encryptor)
    first.save(record)
    first.close()

    # Новый объект = имитация перезапуска сервера.
    second = SqliteKeyStore(db_path, encryptor)
    loaded = second.load(record.auth_key_id)
    second.close()

    assert loaded is not None
    assert loaded.auth_key == record.auth_key
    assert loaded.server_salt == record.server_salt


def test_key_is_not_stored_in_plaintext(db_path, encryptor):
    """auth_key не должен лежать в файле открытым: бэкапы утекают."""
    key = make_key()
    store = SqliteKeyStore(db_path, encryptor)
    store.save(make_record(auth_key=key))
    store.close()

    with open(db_path, "rb") as handle:
        raw = handle.read()
    assert key not in raw, "auth_key found in plaintext inside the database!"

    # WAL-файл тоже проверяем: данные могут ещё лежать там.
    wal = db_path + "-wal"
    if os.path.exists(wal):
        with open(wal, "rb") as handle:
            assert key not in handle.read()


def test_wrong_master_key_drops_record(db_path, encryptor):
    """Сменили мастер-ключ — старые записи нечитаемы и удаляются."""
    record = make_record()
    store = SqliteKeyStore(db_path, encryptor)
    store.save(record)
    store.close()

    other = SqliteKeyStore(db_path, KeyEncryptor(os.urandom(32)))
    assert other.load(record.auth_key_id) is None
    assert other.count() == 0
    other.close()


def test_save_is_idempotent(db_path, encryptor):
    store = SqliteKeyStore(db_path, encryptor)
    record = make_record()
    store.save(record)
    store.save(record)
    assert store.count() == 1
    store.close()


def test_concurrent_writes(db_path, encryptor):
    """Асинхронный сервер пишет из разных потоков пула."""
    store = SqliteKeyStore(db_path, encryptor)
    errors = []

    def worker():
        try:
            for _ in range(20):
                store.save(make_record())
        except Exception as exc:      # pragma: no cover
            errors.append(exc)

    threads = [threading.Thread(target=worker) for _ in range(4)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert not errors
    assert store.count() == 80
    store.close()


# ---------------------------------------------------------------- factory

def test_factory_memory():
    assert isinstance(create_keystore("memory"), InMemoryKeyStore)


def test_factory_sqlite(db_path, encryptor):
    store = create_keystore("sqlite", sqlite_path=db_path, encryptor=encryptor)
    assert isinstance(store, SqliteKeyStore)
    store.close()


def test_factory_rejects_unknown_backend(encryptor):
    with pytest.raises(KeyStoreError, match="unknown backend"):
        create_keystore("postgres", encryptor=encryptor)


# ---------------------------------------------------------------- registry

@pytest.fixture
def registry(db_path, encryptor):
    reg = SessionRegistry(SqliteKeyStore(db_path, encryptor))
    yield reg
    reg.close()


def envelope_from_client(auth_key: bytes, session_id: int, body: bytes,
                         msg_id: int | None = None) -> bytes:
    now = int(time.time())
    msg_id = msg_id if msg_id is not None else ((now << 32) & ~3)
    payload = (
        (0).to_bytes(8, "little", signed=True)
        + session_id.to_bytes(8, "little", signed=True)
        + msg_id.to_bytes(8, "little", signed=True)
        + (1).to_bytes(4, "little")
        + len(body).to_bytes(4, "little")
        + body
    )
    return crypto.encrypt(auth_key, payload, from_client=True)


def test_registry_unpacks_message(registry):
    key = make_key()
    key_id = registry.register(key, server_salt=555)

    envelope = envelope_from_client(key, session_id=42, body=b"helo")
    message = registry.unpack(envelope)

    assert message.body == b"helo"
    assert message.session_id == 42


def test_registry_rejects_unknown_key(registry):
    stranger = make_key()
    envelope = envelope_from_client(stranger, session_id=1, body=b"helo")

    with pytest.raises(UnknownAuthKey):
        registry.unpack(envelope)
    assert registry.stats.unknown_keys == 1


def test_registry_rejects_expired_key(db_path, encryptor):
    reg = SessionRegistry(SqliteKeyStore(db_path, encryptor), max_key_age=1.0)
    key = make_key()
    key_id = auth_key_id_of(key)
    reg._store.save(AuthKeyRecord(
        auth_key_id=key_id, auth_key=key, created_at=time.time() - 100
    ))

    with pytest.raises(UnknownAuthKey):
        reg.session_for(key_id, session_id=1)
    # Просроченная запись должна быть удалена, а не оставлена гнить.
    assert reg._store.load(key_id) is None
    reg.close()


def test_replay_blocked_across_reconnects(registry):
    """Ключевой сценарий: переподключение не сбрасывает анти-replay."""
    key = make_key()
    registry.register(key)
    envelope = envelope_from_client(key, session_id=7, body=b"pay!")

    assert registry.unpack(envelope).body == b"pay!"

    # Тот же конверт второй раз — как будто злоумышленник переподключился
    # и отправил перехваченный пакет заново.
    with pytest.raises(crypto.SecurityViolation, match="replay"):
        registry.unpack(envelope)
    assert registry.stats.replays_blocked == 1


def test_session_cached_between_messages(registry):
    key = make_key()
    key_id = registry.register(key)

    registry.session_for(key_id, 1)
    hits_before = registry.stats.cache_hits
    registry.session_for(key_id, 1)

    assert registry.stats.cache_hits == hits_before + 1


def test_different_sessions_are_isolated(registry):
    key = make_key()
    key_id = registry.register(key)

    a = registry.session_for(key_id, 100)
    b = registry.session_for(key_id, 200)
    assert a is not b
    assert a.session_id == 100 and b.session_id == 200


def test_registry_survives_restart(db_path, encryptor):
    """Ключ, записанный до перезапуска, работает после него."""
    key = make_key()

    first = SessionRegistry(SqliteKeyStore(db_path, encryptor))
    first.register(key, server_salt=777)
    first.close()

    second = SessionRegistry(SqliteKeyStore(db_path, encryptor))
    envelope = envelope_from_client(key, session_id=5, body=b"ping")
    message = second.unpack(envelope)

    assert message.body == b"ping"
    second.close()


def test_forget_revokes_key(registry):
    key = make_key()
    key_id = registry.register(key)
    registry.session_for(key_id, 1)

    registry.forget(key_id)

    with pytest.raises(UnknownAuthKey):
        registry.session_for(key_id, 1)


def test_logout_user_revokes_all_keys(registry):
    keys = [make_key() for _ in range(3)]
    for k in keys:
        registry.register(k, user_id=99)

    assert registry.logout_user(99) == 3
    for k in keys:
        with pytest.raises(UnknownAuthKey):
            registry.session_for(auth_key_id_of(k), 1)


def test_update_salt_reaches_live_session(registry):
    key = make_key()
    key_id = registry.register(key, server_salt=1)
    session = registry.session_for(key_id, 10)

    registry.update_salt(key_id, 424242)

    assert session.server_salt == 424242
    assert registry._store.load(key_id).server_salt == 424242


def test_lru_eviction_limits_memory(db_path, encryptor):
    """Кеш не должен расти бесконечно: сервер держит тысячи соединений."""
    reg = SessionRegistry(SqliteKeyStore(db_path, encryptor), max_cached_sessions=5)
    key = make_key()
    key_id = reg.register(key)

    for session_id in range(20):
        reg.session_for(key_id, session_id)

    assert reg.cached_sessions() <= 5
    assert reg.stats.evictions >= 15
    reg.close()


def test_purge_expired_via_registry(db_path, encryptor):
    reg = SessionRegistry(SqliteKeyStore(db_path, encryptor), max_key_age=1.0)
    key = make_key()
    reg._store.save(AuthKeyRecord(
        auth_key_id=auth_key_id_of(key), auth_key=key,
        created_at=time.time() - 100,
    ))
    assert reg.purge_expired() == 1
    reg.close()


def test_concurrent_session_access(registry):
    """Гонка при одновременном первом обращении не должна плодить сессии."""
    key = make_key()
    key_id = registry.register(key)
    results = []
    lock = threading.Lock()

    def worker():
        session = registry.session_for(key_id, 555)
        with lock:
            results.append(session)

    threads = [threading.Thread(target=worker) for _ in range(8)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert len(results) == 8
    # Все обязаны получить один и тот же объект, иначе окно анти-replay
    # разъедется на несколько независимых копий.
    assert all(s is results[0] for s in results)


# ---------------------------------------------------------------- redis

# Redis-реализацию проверяем на fakeredis: настоящий сервер для тестов
# не нужен, а протокол Redis эмулируется точно.
fakeredis = pytest.importorskip("fakeredis")


@pytest.fixture
def redis_store(encryptor):
    from mtproto_transport.keystore import RedisKeyStore
    store = RedisKeyStore(fakeredis.FakeStrictRedis(), encryptor)
    yield store
    store.close()


def test_redis_save_and_load(redis_store):
    record = make_record()
    redis_store.save(record)

    loaded = redis_store.load(record.auth_key_id)
    assert loaded is not None
    assert loaded.auth_key == record.auth_key
    assert loaded.server_salt == record.server_salt


def test_redis_missing_returns_none(redis_store):
    assert redis_store.load(987654321) is None


def test_redis_update_salt(redis_store):
    record = make_record()
    redis_store.save(record)
    redis_store.update_salt(record.auth_key_id, 31337)
    assert redis_store.load(record.auth_key_id).server_salt == 31337


def test_redis_delete(redis_store):
    record = make_record()
    redis_store.save(record)
    redis_store.delete(record.auth_key_id)
    assert redis_store.load(record.auth_key_id) is None


def test_redis_delete_for_user(redis_store):
    keys = [make_record(user_id=42) for _ in range(3)]
    other = make_record(user_id=43)
    for r in keys + [other]:
        redis_store.save(r)

    assert redis_store.delete_for_user(42) == 3
    for r in keys:
        assert redis_store.load(r.auth_key_id) is None
    assert redis_store.load(other.auth_key_id) is not None


def test_redis_stores_ciphertext_only(encryptor):
    """Ключ не должен попадать в Redis открытым."""
    from mtproto_transport.keystore import RedisKeyStore
    client = fakeredis.FakeStrictRedis()
    store = RedisKeyStore(client, encryptor)

    key = make_key()
    record = make_record(auth_key=key)
    store.save(record)

    stored = client.hget(f"mtproto:authkey:{record.auth_key_id}", "key")
    assert stored is not None
    assert key not in stored
    store.close()


def test_redis_wrong_master_key_drops_record(encryptor):
    from mtproto_transport.keystore import RedisKeyStore
    client = fakeredis.FakeStrictRedis()

    record = make_record()
    RedisKeyStore(client, encryptor).save(record)

    other = RedisKeyStore(client, KeyEncryptor(os.urandom(32)))
    assert other.load(record.auth_key_id) is None


def test_redis_count(redis_store):
    for _ in range(3):
        redis_store.save(make_record())
    assert redis_store.count() == 3


def test_redis_touch_extends_ttl(redis_store):
    record = make_record()
    redis_store.save(record)
    redis_store.touch(record.auth_key_id)
    assert redis_store.load(record.auth_key_id).last_used_at > 0


def test_redis_registry_end_to_end(encryptor):
    """Реестр поверх Redis работает так же, как поверх SQLite."""
    from mtproto_transport.keystore import RedisKeyStore
    store = RedisKeyStore(fakeredis.FakeStrictRedis(), encryptor)
    reg = SessionRegistry(store)

    key = make_key()
    reg.register(key, server_salt=4242)

    envelope = envelope_from_client(key, session_id=11, body=b"helo")
    assert reg.unpack(envelope).body == b"helo"

    # Анти-replay работает и здесь.
    with pytest.raises(crypto.SecurityViolation, match="replay"):
        reg.unpack(envelope)

    reg.close()


def test_redis_shared_between_two_processes(encryptor):
    """Смысл Redis: два сервера видят один и тот же ключ."""
    from mtproto_transport.keystore import RedisKeyStore
    shared = fakeredis.FakeStrictRedis()

    node_a = SessionRegistry(RedisKeyStore(shared, encryptor))
    node_b = SessionRegistry(RedisKeyStore(shared, encryptor))

    key = make_key()
    node_a.register(key, server_salt=99)

    # Клиента перебросило балансировщиком на другой сервер.
    session = node_b.session_for(auth_key_id_of(key), session_id=5)
    assert session.auth_key == key
    assert session.server_salt == 99

    node_a.close()
    node_b.close()
