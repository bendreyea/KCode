package org.editor.core

import kotlin.math.min

/**
 * Represents a piece table that holds the bytes of a file.
 * PieceTable data structure that references:
 *  - The appended edits (via [appendBuffer])
 */
class PieceTable private constructor(
    initial: Piece?
) : TextBuffer {

    private constructor(
        pieces: List<Piece>,
        prefixSums: List<Long>,
        totalLength: Long,
        appendBuffer: ByteArrayBuffer
    ) : this(null) {
        this.appendBuffer = appendBuffer
        this.pieces.addAll(pieces)
        this.prefixSums.clear()
        this.prefixSums.addAll(prefixSums)
        this.totalLength = totalLength
    }

    /** Where newly appended data goes. */
    private var appendBuffer: ByteArrayBuffer = ByteArrayBuffer.create()

    /**
     * The ordered list of all pieces. Each piece knows:
     *   - the start offset in that buffer
     *   - the length of the piece
     */
    private val pieces = mutableListOf<Piece>()


    // Tracks cumulative lengths
    private val prefixSums = mutableListOf(0L)


    /** Total byte length of this piece table. */
    private var totalLength: Long = 0

    init {
        initial?.let {
            pieces.add(it)
            prefixSums.add(it.length)
            totalLength = it.length
        }
    }

    override fun insert(pos: Long, bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return
        require(pos in 0..totalLength)

        val newPiece = Piece(appendBuffer, appendBuffer.length(), bytes.size.toLong())
        appendBuffer.append(bytes)

        if (pieces.isEmpty()) {
            addNewPiece(newPiece)
            totalLength = bytes.size.toLong()
            return
        }

        val index = findPieceIndex(pos)
        val (startInPiece, pieceIndex) = if (index != -1) {
            val start = prefixSums[index]
            (pos - start) to index
        } else {
            0L to pieces.size
        }

        if (startInPiece == 0L) {
            insertPieceAt(pieceIndex, newPiece)
        } else {
            splitAndInsert(pieceIndex, startInPiece, newPiece)
        }

        totalLength += bytes.size
        mergeIfPossible(pieceIndex)
    }

    override fun withInsert(pos: Long, bytes: ByteArray): TextBuffer {
        if (bytes.isEmpty())
            return this

        require(pos in 0..totalLength) {
            "Insert pos=$pos out of range (0..$totalLength)"
        }

        // Snapshot old buffer, then append new data
        val newAppendBuffer = appendBuffer.snapshot()
        val offsetInNewBuffer = newAppendBuffer.length()
        newAppendBuffer.append(bytes)

        val newPieces = pieces.toMutableList()
        val newPrefixSums = prefixSums.toMutableList()
        var newTotalLength = totalLength

        val newPiece = Piece(newAppendBuffer, offsetInNewBuffer, bytes.size.toLong())

        val index = findPieceIndex(pos)
        if (index == -1) {
            // That typically means: table is empty or pos is beyond the last piece’s prefix
            newPieces.add(newPiece)
            newPrefixSums.add(newPrefixSums.last() + bytes.size)
            newTotalLength += bytes.size
        } else {
            val startInPiece = pos - newPrefixSums[index]
            if (startInPiece == 0L) {
                // Insert at the start of piece `index`
                newPieces.add(index, newPiece)
                newTotalLength += bytes.size
                rebuildPrefixSums(newPieces, newPrefixSums, index)
            } else {
                // We must split the piece
                val oldPiece = newPieces[index]
                val (left, right) = oldPiece.split(startInPiece)
                newPieces[index] = left
                newPieces.add(index + 1, newPiece)
                newPieces.add(index + 2, right)
                newTotalLength += bytes.size
                rebuildPrefixSums(newPieces, newPrefixSums, index)
            }

            mergeIfPossible(index)
        }

        return PieceTable(
            pieces = newPieces,
            prefixSums = newPrefixSums,
            totalLength = newTotalLength,
            appendBuffer = newAppendBuffer
        )
    }

    override fun delete(pos: Long, len: Int) {
        if (len <= 0) return
        val endPos = pos + len
        require(pos in 0 until totalLength && endPos <= totalLength) {
            "Deletion range [$pos, $endPos) out of bounds (length=$totalLength)"
        }

        if (totalLength == 0L) return

        val startIndex = findPieceIndex(pos)
        val startSplitOffset = pos - prefixSums[startIndex]
        val startRight = splitPiece(startIndex, startSplitOffset)

        val newEndIndex = findPieceIndex(endPos - 1)
        val endSplitOffset = endPos - prefixSums[newEndIndex]
        val endRight = splitPiece(newEndIndex, endSplitOffset)

        // Corrected removal range calculation
        val removeFrom = if (startRight != null) startIndex + 1 else startIndex
        val removeTo = newEndIndex + 1 // Always remove up to newEndIndex (inclusive)

        if (removeFrom >= pieces.size || removeTo > pieces.size || removeFrom >= removeTo) {
            rebuildPrefixSumsFrom(0)
            return
        }

        val deletedLength = prefixSums[removeTo] - prefixSums[removeFrom]
        totalLength -= deletedLength

        pieces.subList(removeFrom, removeTo).clear()
        rebuildPrefixSumsFrom(removeFrom.coerceAtLeast(0))

        // Merge adjacent pieces safely
        if (removeFrom > 0) mergeIfPossible(removeFrom - 1)
        if (removeFrom < pieces.size) mergeIfPossible(removeFrom)
    }

    override fun withDelete(pos: Long, len: Int): TextBuffer {
        if (len <= 0)
            return this

        val endPos = pos + len
        require(pos >= 0 && endPos <= totalLength) {
            "Delete range [$pos..$endPos) out of bounds (length=$totalLength)"
        }

        // Copy-on-write
        val newPieces = pieces.toMutableList()
        val newPrefixSums = prefixSums.toMutableList()
        var newTotalLength = totalLength

        val startIndex = findPieceIndex(pos)
        val startOffset = pos - newPrefixSums[startIndex]
        splitPiece(newPieces, newPrefixSums, startIndex, startOffset)

        val endIndex = findPieceIndex(endPos - 1, newPieces, newPrefixSums)
        val endOffset = endPos - newPrefixSums[endIndex]
        splitPiece(newPieces, newPrefixSums, endIndex, endOffset)

        // Now remove all pieces fully within [startIndex+1 .. endIndex]
        // or partially if they are the new splits.
        val removeFrom = if (startOffset > 0) startIndex + 1 else startIndex
        val removeTo   = if (endOffset == 0L) endIndex else endIndex

        if (removeFrom in newPieces.indices && removeTo in newPieces.indices && removeFrom <= removeTo) {
            val removedLength = newPrefixSums[removeTo + 1] - newPrefixSums[removeFrom]
            newPieces.subList(removeFrom, removeTo + 1).clear()
            newTotalLength -= removedLength
            rebuildPrefixSums(newPieces, newPrefixSums, removeFrom.coerceAtLeast(0))
        }

        return PieceTable(newPieces, newPrefixSums, newTotalLength, appendBuffer)
    }

    override fun snapshot(): TextBuffer {
        val snap = PieceTable(
            pieces = pieces.toList(),
            prefixSums = prefixSums.toList(),
            totalLength = totalLength,
            appendBuffer = appendBuffer.snapshot()
        )

        return snap
    }

    private fun mergeIfPossible(index: Int) {
        if (index < 0 || index >= pieces.size) return

        // Merge backward
        var i = index
        while (i > 0 && pieces[i - 1].canMergeWith(pieces[i])) {
            val merged = pieces[i - 1].merge(pieces[i])
            pieces.removeAt(i)
            pieces.removeAt(i - 1)
            pieces.add(i - 1, merged)
            rebuildPrefixSumsFrom(i - 1)
            i--
        }

        // Merge forward
        i = index
        while (i < pieces.size - 1 && pieces[i].canMergeWith(pieces[i + 1])) {
            val merged = pieces[i].merge(pieces[i + 1])
            pieces.removeAt(i + 1)
            pieces.removeAt(i)
            pieces.add(i, merged)
            rebuildPrefixSumsFrom(i)
        }
    }

    private fun splitPiece(index: Int, offset: Long): Piece? {
        if (offset <= 0 || offset >= pieces[index].length) return null

        val original = pieces[index]
        val (left, right) = original.split(offset)

        pieces[index] = left
        pieces.add(index + 1, right)
        rebuildPrefixSumsFrom(index)

        return right
    }

    /**
     * Splits the piece at [index] if [offset] is within that piece.
     */
    private fun splitPiece(
        pieceList: MutableList<Piece>,
        sumList: MutableList<Long>,
        index: Int,
        offset: Long
    ) {
        if (index < 0 || index >= pieceList.size) return
        if (offset <= 0 || offset >= pieceList[index].length) return

        val oldPiece = pieceList[index]
        val (left, right) = oldPiece.split(offset)
        pieceList[index] = left
        pieceList.add(index + 1, right)
        rebuildPrefixSums(pieceList, sumList, index)
    }

    override fun get(pos: Long, len: Int): ByteArray {
        if (len <= 0)
            return ByteArray(0)

        val endPos = pos + len
        val startIndex = findPieceIndex(pos)
        val endIndex = findPieceIndex(endPos - 1)

        if (startIndex == -1 || endIndex == -1) return ByteArray(0)

        val result = ByteArray(len)
        var copied = 0L
        var currentIndex = startIndex
        var offsetInPiece = (pos - prefixSums[startIndex]).toInt()

        while (copied < len && currentIndex <= endIndex) {
            val piece = pieces[currentIndex]
            val toCopy = min(piece.length - offsetInPiece, len - copied).toInt()
            piece.bytes(offsetInPiece, toCopy).copyInto(result, copied.toInt())
            copied += toCopy
            offsetInPiece = 0
            currentIndex++
        }

        return result
    }

    /**
     * Adds a new piece to the end of the pieces list and updates prefix sums.
     */
    private fun addNewPiece(newPiece: Piece) {
        pieces.add(newPiece)
        prefixSums.add(prefixSums.last() + newPiece.length)
    }

    /**
     * Inserts a piece at specific index and updates prefix sums.
     */
    private fun insertPieceAt(index: Int, newPiece: Piece) {
        pieces.add(index, newPiece)
        rebuildPrefixSumsFrom(index)
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

    private fun findPieceIndex(pos: Long): Int {
        var low = 0
        var high = prefixSums.size - 2
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = prefixSums[mid]
            val end = prefixSums[mid + 1]
            when {
                pos < start -> high = mid - 1
                pos >= end -> low = mid + 1
                else -> return mid
            }
        }

        return -1
    }

    private fun findPieceIndex(
        pos: Long,
        pieceList: List<Piece>,
        sumList: List<Long>
    ): Int {
        // Standard binary search over prefix sums
        var low = 0
        var high = pieceList.size - 1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val start = sumList[mid]
            val end = sumList[mid + 1]
            when {
                pos < start -> high = mid - 1
                pos >= end -> low = mid + 1
                else -> return mid
            }
        }
        return pieceList.size - 1
    }

    private fun rebuildPrefixSumsFrom(fromIndex: Int) {
        var current = prefixSums[fromIndex]
        for (i in fromIndex until pieces.size) {
            current += pieces[i].length
            if (i + 1 < prefixSums.size) {
                prefixSums[i + 1] = current
            } else {
                prefixSums.add(current)
            }
        }
        // Trim excess
        while (prefixSums.size > pieces.size + 1) {
            prefixSums.removeLast()
        }
    }

    private fun rebuildPrefixSums(
        pieceList: MutableList<Piece>,
        sumList: MutableList<Long>,
        startIndex: Int
    ) {
        // Guarantee sumList.size == pieceList.size + 1
        // Expand or shrink sumList if needed
        while (sumList.size < pieceList.size + 1)
            sumList.add(sumList.last())

        while (sumList.size > pieceList.size + 1)
            sumList.removeLast()

        val base = if (startIndex == 0) 0L else sumList[startIndex]
        var current = base
        for (i in startIndex until pieceList.size) {
            current += pieceList[i].length
            sumList[i + 1] = current
        }
    }

    private fun splitAndInsert(tableIndex: Int, offset: Long, newPiece: Piece) {
        val original = pieces[tableIndex]
        val (left, right) = original.split(offset)
        pieces.removeAt(tableIndex)
        pieces.addAll(tableIndex, listOf(left, newPiece, right))
        rebuildPrefixSumsFrom(tableIndex)
    }

    private fun Piece.canMergeWith(other: Piece): Boolean =
        this.target === other.target && this.end() == other.bufIndex

    private fun Piece.merge(other: Piece): Piece =
        Piece(this.target, this.bufIndex, this.length + other.length)

    companion object {
        fun create(): PieceTable {
            return PieceTable(null)
        }
    }
}
