package org.editor.core

/**
 * A growable byte array that acts like a dynamic Buffer for appended data.
 */
import java.io.ByteArrayOutputStream
import java.io.Serializable

class ByteArrayBuffer private constructor(
    private val outputStream: ByteArrayOutputStream
) : Buffer, Serializable {

    override fun get(index: Long): Byte {
        val byteArray = outputStream.toByteArray()
        require(index in byteArray.indices) { "index[$index], length[${byteArray.size}]" }
        return byteArray[index.toInt()]
    }

    override fun bytes(from: Long, to: Long): ByteArray {
        val byteArray = outputStream.toByteArray()
        val intFrom = from.toInt()
        val intTo = to.toInt()
        require(intFrom in 0..byteArray.size) { "from[$from] out of range" }
        require(intTo in intFrom..byteArray.size) { "to[$to] out of range" }
        return byteArray.copyOfRange(intFrom, intTo)
    }

    fun append(b: ByteArray) {
        outputStream.write(b)
    }

    fun append(b: ByteArrayBuffer) {
        outputStream.write(b.bytes(0, b.length()).copyOf())
    }

    override fun length(): Long = outputStream.size().toLong()

    fun close() {
        outputStream.close()
    }

    companion object {
        private val EMPTY = byteArrayOf()

        /**
         * Creates an empty ByteArrayBufferWrapper.
         */
        fun create(): ByteArrayBuffer {
            return ByteArrayBuffer(ByteArrayOutputStream())
        }

        /**
         * Creates a ByteArrayBufferWrapper initialized with the provided bytes.
         * @param bytes Initial bytes to populate the buffer.
         */
        fun create(bytes: ByteArray): ByteArrayBuffer {
            val buffer = ByteArrayOutputStream(bytes.size)
            buffer.write(bytes, 0, bytes.size)
            return ByteArrayBuffer(buffer)
        }
    }
}