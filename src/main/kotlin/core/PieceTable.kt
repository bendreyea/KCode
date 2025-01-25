package org.editor.core

import java.io.OutputStream
import java.util.*
import kotlin.collections.set
import kotlin.math.min

/**
 * Represents a piece table that holds the bytes of a file.
 * PieceTable data structure that references:
 *  - The appended edits (via [appendBuffer])
 */
class PieceTable private constructor(
    initial: Piece?
) : TextBuffer {

    /** Where newly appended data goes. */
    private val appendBuffer: ByteArrayBuffer = ByteArrayBuffer.create()

    /**
     * The ordered list of all pieces. Each piece knows:
     *   - which buffer (ChannelBuffer or ByteArrayBuffer)
     *   - the start offset in that buffer
     *   - the length of the piece
     */
    private val pieces = mutableListOf<Piece>()

    /**
     * A TreeMap mapping `piecePosition -> PiecePoint`.
     * Instead of clearing the entire tail, we'll do partial updates.
     */
    private val indices = TreeMap<Long, PiecePoint>()

    /** Total byte length of this piece table. */
    private var totalLength: Long = 0

    init {
        if (initial != null) {
            pieces.add(initial)
            totalLength = initial.length
            // Index just that initial piece
            indices[0L] = PiecePoint(0L, 0, initial)
        }
    }

    override fun insert(pos: Long, bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return
        require(pos in 0..totalLength) {
            "Insertion pos[$pos] out of range (length=$totalLength)."
        }

        // The new piece referencing appended data
        val newPiece = Piece(appendBuffer, appendBuffer.length(), bytes.size.toLong())
        appendBuffer.append(bytes)

        // Find the piece that covers 'pos'
        val point = at(pos)
        if (point == null) {
            // pos == totalLength (end) or table is empty
            pieces.add(newPiece)
            // The new piece starts at totalLength
            val tableIndex = pieces.size - 1
            updateIndex(tableIndex) // do a localized re-index
        } else if (point.position == pos) {
            // Exactly on a boundary
            pieces.add(point.tableIndex, newPiece)
            // Remove the old index for the old piecePoint
            removeIndex(point.tableIndex + 1)
            // Insert index for new piece
            updateIndex(point.tableIndex)
            // The piece that was at point.tableIndex is now shifted to index+1, re-index that too
            updateIndex(point.tableIndex + 1)
        } else {
            // Splitting a piece
            val (leftPiece, rightPiece) = point.piece.split(pos - point.position)
            // Remove the original piece from the list
            pieces.removeAt(point.tableIndex)
            removeIndex(point.tableIndex) // remove old index entry

            // Insert left, newPiece, right
            pieces.addAll(point.tableIndex, listOf(leftPiece, newPiece, rightPiece))
            // Re-index just the newly inserted slices
            updateIndex(point.tableIndex)
            updateIndex(point.tableIndex + 1)
            updateIndex(point.tableIndex + 2)
        }

        totalLength += bytes.size
    }

    override fun delete(pos: Long, len: Int) {
        if (len <= 0)
            return

        require(pos in 0 until totalLength) {
            "Deletion pos[$pos] out of range (length=$totalLength)."
        }

        val endPos = pos + len - 1
        val rangePoints = range(pos, endPos)
        if (rangePoints.isEmpty()) return

        val first = rangePoints.first()
        val last  = rangePoints.last()
        val startIdx = first.tableIndex
        val endIdx   = last.tableIndex

        // Remove the pieces in one shot
        pieces.subList(startIdx, endIdx + 1).clear()

        // Remove indices for each piece in that range
        for (pt in rangePoints) {
            indices.remove(pt.position)
        }

        // Possibly re-insert partial leftover from the "last" piece
        val lastPieceEndPos = last.endPosition()
        if (endPos < lastPieceEndPos - 1) {
            val cutPos = pos + len - last.position
            val (_, right) = last.piece.split(cutPos)
            pieces.add(startIdx, right)
        }

        // Possibly re-insert partial leftover from the "first" piece
        if (pos > first.position) {
            val (left, _) = first.piece.split(pos - first.position)
            pieces.add(startIdx, left)
        }

        // Important!!!!!: re-index everything from startIdx onward
        updateIndex(startIdx)

        totalLength -= len
    }


    override fun get(pos: Long, len: Int): ByteArray {
        if (len <= 0)
            return ByteArray(0)

        val bytes = bytes()
        val endPos = pos + len - 1
        val rangePoints = range(pos, endPos)
        if (rangePoints.isEmpty())
            return ByteArray(0)

        val result = ByteArray(len)
        var remains = len
        var destPos = 0

        // Start offset in the first piece
        var offsetInPiece = (pos - rangePoints[0].position).toInt()

        for (pp in rangePoints) {
            val pieceSize = pp.piece.length.toInt()
            val toCopy = min(pieceSize - offsetInPiece, remains)
            val chunk = pp.piece.bytes(offsetInPiece, toCopy)
            chunk.copyInto(result, destinationOffset = destPos)
            remains -= toCopy
            destPos += toCopy
            offsetInPiece = 0
            if (remains <= 0)
                break
        }

        return result
    }

    override fun length(): Long = totalLength

    /**
     * Returns all the bytes in the PieceTable.
     */
    override fun bytes(): ByteArray {
        val all = ByteArrayBuffer.create()
        for (piece in pieces) {
            all.append(piece.bytes())
        }
        return all.bytes(0, all.length())
    }

    fun writeToFile(outputStream: OutputStream) {
        outputStream.use { it.write(bytes()) }
    }

    /**
     * Finds which piece covers [pos].
     * Instead of clearing from `pos` onward, we do partial map lookups,
     * and if we need more, we fill forward in [fillToIndices].
     */
    private fun at(pos: Long): PiecePoint? {
        if (pieces.isEmpty()) return null
        // 1) Look for floor entry
        val floor = indices.floorEntry(pos)
        val piecePoint = floor?.value
        return when {
            piecePoint == null -> {
                // No entry found with key <= pos. Start from the beginning.
                fillToIndices(pos, 0, 0)
            }
            piecePoint.contains(pos) -> {
                piecePoint
            }
            else -> {
                // We do partial fill from piecePoint.tableIndex + 1 onward
                fillToIndices(pos, piecePoint.endPosition(), piecePoint.tableIndex + 1)
            }
        }
    }

    /**
     * Return all pieces covering [startPos-endPos], in a single pass.
     */
    private fun range(startPos: Long, endPos: Long): List<PiecePoint> {
        if (startPos > endPos)
            return emptyList()

        val startPoint = at(startPos) ?: return emptyList()
        if (!startPoint.contains(startPos))
            return emptyList()  // sanity check

        val result = mutableListOf<PiecePoint>()
        var curr = startPoint
        result.add(curr)

        // We'll iterate forward in the pieces array, not calling at(...) repeatedly.
        var i = curr.tableIndex + 1
        while (i < pieces.size) {
            // Make sure index i is in the map, or fill it
            val nextPos = curr.endPosition()
            val maybeNext = indices[nextPos] ?: run {
                fillToIndices(endPos, nextPos, i)
            }
            if (maybeNext == null)
                break

            if (maybeNext.position > endPos)
                break

            result.add(maybeNext)
            curr = maybeNext
            i++
        }

        return result
    }

    /**
     * Moves forward from [tableIndex], building or updating [indices],
     * until we find a piece that contains [pos], or we exhaust the list.
     */
    private fun fillToIndices(pos: Long, piecePosition: Long, tableIndex: Int): PiecePoint? {
        var runningPos = piecePosition
        for (i in tableIndex until pieces.size) {
            val p = pieces[i]
            val pp = PiecePoint(runningPos, i, p)
            indices[pp.position] = pp
            if (pp.contains(pos)) {
                return pp
            }

            runningPos += p.length
        }
        return null
    }

    /**
     * We do a localized re-index starting from [tableIndex] to the end.
     * This is cheaper than clearing from the beginning or from a big tail.
     */
    private fun updateIndex(tableIndex: Int) {
        if (tableIndex < 0 || tableIndex >= pieces.size)
            return

        // remove all existing index entries for tableIndex and beyond
        // but only up to next piece boundary
        val pieceStart = positionOf(tableIndex)
        val tailKeys = indices.tailMap(pieceStart, true).keys.toList()
        for (k in tailKeys) {
            val pt = indices[k] ?: continue
            if (pt.tableIndex >= tableIndex) {
                indices.remove(k)
            }
        }
        // re-build from tableIndex forward until we run out or cover new positions
        fillToIndices(Long.MAX_VALUE, pieceStart, tableIndex)
    }

    /**
     * Remove the index entry for the piece at [tableIndex], if any.
     * We do not blow away the entire tail of the map.
     */
    private fun removeIndex(tableIndex: Int) {
        // We only remove the key that references that piece
        if (tableIndex < 0 || tableIndex >= pieces.size) return
        val startPos = positionOf(tableIndex)
        indices.remove(startPos)
    }

    /**
     * The absolute position of piece at [idx], computed by summing lengths of prior pieces.
     * For performance, we rely on the fact that [updateIndex] eventually corrects or caches it.
     */
    private fun positionOf(idx: Int): Long {
        var sum = 0L
        for (i in 0 until idx) {
            sum += pieces[i].length
        }
        return sum
    }

    /**
     * Combines adjacent pieces in [pieces] if they reference the same buffer and are contiguous.
     * Also clears [indices], so it can be rebuilt fresh.
     */
    fun gc() {
        val merged = mutableListOf<Piece>()
        var prev: Piece? = null
        for (p in pieces) {
            if (prev == null) {
                prev = p
            } else if (prev.target === p.target && prev.end() == p.bufIndex) {
                // Merge them
                prev = Piece(prev.target, prev.bufIndex, prev.length + p.length)
            } else {
                merged.add(prev)
                prev = p
            }
        }
        if (prev != null) merged.add(prev)

        pieces.clear()
        pieces.addAll(merged)
        indices.clear()

        // re-index from scratch
        var runningPos = 0L
        for ((i, piece) in pieces.withIndex()) {
            indices[runningPos] = PiecePoint(runningPos, i, piece)
            runningPos += piece.length
        }
    }

    private data class PiecePoint(
        val position: Long,
        val tableIndex: Int,
        val piece: Piece
    ) {
        fun endPosition(): Long = position + piece.length
        fun contains(pos: Long): Boolean = (pos >= position && pos < endPosition())
    }

    companion object {
        fun create(): PieceTable {
            return PieceTable(null)
        }
    }
}
