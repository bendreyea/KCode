package org.editor.core

/**
 * A simple `Piece` referencing [Buffer].
 */
class Piece(
    val target: Buffer,
    val bufIndex: Long,
    val length: Long
) {
    fun end(): Long = bufIndex + length

    /**
     * Split this piece at [offset] (relative to [bufIndex]).
     */
    fun split(offset: Long): List<Piece> {
        require(offset in 0..length) {
            "Illegal offset[$offset], length[$length]"
        }
        return when (offset) {
            0L -> listOf(this)
            length -> listOf(this)
            else -> listOf(
                Piece(target, bufIndex, offset),
                Piece(target, bufIndex + offset, length - offset)
            )
        }
    }

    /**
     * Extract a subrange of bytes from within this Piece.
     */
    fun bytes(offset: Int, len: Int): ByteArray {
        require(offset >= 0 && (offset + len) <= length.toInt()) {
            "offset[$offset], len[$len] out of range (piece length=$length)"
        }

        return target.bytes(bufIndex + offset, bufIndex + offset + len)
    }

    fun bytes(): ByteArray {
        return target.bytes(bufIndex, bufIndex + length)
    }
}