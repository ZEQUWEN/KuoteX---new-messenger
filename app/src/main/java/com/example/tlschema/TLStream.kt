package com.example.tlschema

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Класс для сериализации и десериализации примитивов Type Language (TL).
 * Все данные передаются в формате little-endian.
 */
class TLStream {

    companion object {
        fun writeInt(out: OutputStream, value: Int) {
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(value)
            out.write(buffer.array())
        }

        fun readInt(input: InputStream): Int {
            val bytes = ByteArray(4)
            input.read(bytes)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return buffer.int
        }

        fun writeLong(out: OutputStream, value: Long) {
            val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putLong(value)
            out.write(buffer.array())
        }

        fun readLong(input: InputStream): Long {
            val bytes = ByteArray(8)
            input.read(bytes)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return buffer.long
        }

        fun writeDouble(out: OutputStream, value: Double) {
            val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putDouble(value)
            out.write(buffer.array())
        }

        fun readDouble(input: InputStream): Double {
            val bytes = ByteArray(8)
            input.read(bytes)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return buffer.double
        }

        /**
         * Сериализация строки или байтового массива в TL.
         * Если длина <= 253 байт, используется 1 байт длины.
         * Если длина > 253 байт, используется код 254 и 3 байта длины.
         * Длина всегда выравнивается по границе 4 байт (padding).
         */
        fun writeByteArray(out: OutputStream, data: ByteArray) {
            val length = data.size
            if (length <= 253) {
                out.write(length)
                out.write(data)
                // Padding
                val padding = (4 - (length + 1) % 4) % 4
                for (i in 0 until padding) {
                    out.write(0)
                }
            } else {
                out.write(254)
                out.write(length and 0xFF)
                out.write((length shr 8) and 0xFF)
                out.write((length shr 16) and 0xFF)
                out.write(data)
                // Padding
                val padding = (4 - (length + 4) % 4) % 4
                for (i in 0 until padding) {
                    out.write(0)
                }
            }
        }

        fun readByteArray(input: InputStream): ByteArray {
            var length = input.read()
            var padding = 0
            if (length <= 253) {
                padding = (4 - (length + 1) % 4) % 4
            } else if (length == 254) {
                val len1 = input.read()
                val len2 = input.read()
                val len3 = input.read()
                length = len1 or (len2 shl 8) or (len3 shl 16)
                padding = (4 - (length + 4) % 4) % 4
            } else {
                throw IllegalStateException("Invalid string/byte array length indicator: $length")
            }

            val data = ByteArray(length)
            input.read(data)
            
            // Skip padding
            for (i in 0 until padding) {
                input.read()
            }
            return data
        }

        fun writeString(out: OutputStream, value: String) {
            writeByteArray(out, value.toByteArray(Charsets.UTF_8))
        }

        fun readString(input: InputStream): String {
            val data = readByteArray(input)
            return String(data, Charsets.UTF_8)
        }
    }
}
