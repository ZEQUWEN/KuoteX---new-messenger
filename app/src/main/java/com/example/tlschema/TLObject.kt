package com.example.tlschema

/**
 * Базовый интерфейс для всех объектов схемы Type Language (TL),
 * используемой в Telegram для сериализации данных.
 * Референс: https://core.telegram.org/schema
 */
interface TLObject {
    /**
     * Уникальный идентификатор конструктора (32-битное целое число).
     */
    val constructorId: Int

    /**
     * Сериализация объекта в байтовый массив в соответствии с правилами TL.
     */
    fun serialize(): ByteArray
}

/**
 * Базовый класс для сериализатора/десериализатора.
 */
abstract class TLContext {
    abstract fun deserializeMessage(data: ByteArray): TLObject
}
