package org.editor.core

/** A generic read-only Buffer interface for any back-end storage. */
interface Buffer {
    fun get(index: Long): Byte
    fun bytes(from: Long, to: Long): ByteArray
    fun length(): Long
}