package org.editor.core

import java.io.ByteArrayOutputStream

sealed class RopeNode {
    /** Returns the total number of bytes in this node’s subtree. */
    abstract fun length(): Int
}

/**
 * A leaf node that directly stores bytes.
 */
data class LeafNode(val bytes: ByteArray) : RopeNode() {
    override fun length(): Int = bytes.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LeafNode
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}

/**
 * An internal node that has a left child and a right child.
 * The `weight` is typically the length of the entire left subtree.
 */
data class InternalNode(
    val left: RopeNode,
    val right: RopeNode,
    val weight: Int = left.length()
) : RopeNode() {
    override fun length(): Int = left.length() + right.length()
}

/**
 * A Rope data structure that wraps a RopeNode (root).
 */
class Rope(
    private var root: RopeNode,
    private val minChunkSize: Int = 128,
    private val maxChunkSize: Int = 512
) : TextBuffer {

    override fun bytes(): ByteArray {
        val out = ByteArrayOutputStream(root.length().coerceAtLeast(16))
        appendRopeNode(root, out)
        return out.toByteArray()
    }

    /**
     * Returns the total length of the rope (in bytes).
     */
    override fun length(): Long = root.length().toLong()

    /**
     * Insert the given bytes into this rope at the given [pos].
     */
    override fun insert(pos: Long, bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty())
            return
        require(pos in 0..length()) { "Insert index out of bounds" }

        // 1) Split at 'pos'.
        val (leftPart, rightPart) = split(root, pos.toInt())

        // 2) Build a rope from new bytes (split into chunked leaves).
        val middle = buildRopeFromByteArray(bytes, maxChunkSize).root

        // 3) Re-assemble (left + middle + right).
        val tmp = concatNodes(leftPart, middle)
        root = concatNodes(tmp, rightPart)

        // 4) If the leaf got too big, or the tree is unbalanced, we can fix it:
        //   - Option A: flatten + rebuild
        //   - Option B: incremental rotation
        root = rebalance(root)
    }

    /**
     * Delete the portion of the rope from [pos] (inclusive) of length [len].
     */
    override fun delete(pos: Long, len: Int) {
        require(pos >= 0 && len >= 0 && pos + len <= length()) { "Invalid delete range" }

        val startIndex = pos.toInt()
        val endIndex = (pos + len).toInt()

        val (leftPart, tmpRight) = split(root, startIndex)
        val (_, rightPart) = split(tmpRight, endIndex - startIndex)
        root = concatNodes(leftPart, rightPart)

        root = rebalance(root)
    }

    /**
     * Get the byte array of [len] bytes starting at [pos].
     */
    override fun get(pos: Long, len: Int): ByteArray? {
        if (len <= 0)
            return ByteArray(0)

        return substringNode(root, pos.toInt(), (pos + len).toInt()).let { node ->
            // node might be multiple leaves, so flatten it:
            val out = ByteArrayOutputStream(len.coerceAtLeast(16))
            appendRopeNode(node, out)
            out.toByteArray()
        }
    }

    /**
     * For debugging: interpret bytes as UTF-8 and return a String.
     */
    override fun toString(): String {
        return String(bytes())
    }

    /**
     * Rebalance the rope node. For simplicity, we do:
     *    - Collect all leaves in a list (flatten).
     *    - Rebuild a balanced tree from that list.
     **/
    private fun rebalance(node: RopeNode): RopeNode {
        // 1) Flatten
        val leaves = mutableListOf<ByteArray>()
        flatten(node, leaves)

        // 2) Possibly re-chunk if leaves are bigger than maxChunkSize or smaller than minChunkSize
        val rechunkedLeaves = chunkOrMergeLeaves(leaves, minChunkSize, maxChunkSize)

        // 3) Build balanced
        return buildBalanced(rechunkedLeaves.map { LeafNode(it) })
    }

    /**
     * Flatten the rope by collecting all leaf byte arrays into [acc].
     */
    private fun flatten(node: RopeNode, acc: MutableList<ByteArray>) {
        when (node) {
            is LeafNode -> acc.add(node.bytes)
            is InternalNode -> {
                flatten(node.left, acc)
                flatten(node.right, acc)
            }
        }
    }

    /**
     * Given a list of ByteArrays, ensure none exceed [maxChunkSize] and none are
     * smaller than [minChunkSize] by splitting or merging as needed.
     */
    private fun chunkOrMergeLeaves(
        leaves: List<ByteArray>,
        minChunkSize: Int,
        maxChunkSize: Int
    ): List<ByteArray> {
        val result = mutableListOf<ByteArray>()

        for (leaf in leaves) {
            // If this leaf is too large, split it into multiple chunks
            var start = 0
            while (start < leaf.size) {
                val end = (start + maxChunkSize).coerceAtMost(leaf.size)
                val chunk = leaf.copyOfRange(start, end)
                result.add(chunk)
                start = end
            }
        }

        // Now we can do a pass to merge small chunks:
        val merged = mutableListOf<ByteArray>()
        var currentBuffer = ByteArrayOutputStream()

        for (chunk in result) {
            if (currentBuffer.size() == 0) {
                // Start a new buffer
                currentBuffer.write(chunk)
            } else {
                // If merging this chunk won't exceed maxChunkSize, do it
                if (currentBuffer.size() + chunk.size <= maxChunkSize) {
                    currentBuffer.write(chunk)
                } else {
                    // We finalize current buffer, then start a new one
                    merged.add(currentBuffer.toByteArray())
                    currentBuffer = ByteArrayOutputStream()
                    currentBuffer.write(chunk)
                }
            }
        }
        // Flush last buffer
        if (currentBuffer.size() > 0) {
            merged.add(currentBuffer.toByteArray())
        }

        return merged
    }

    /**
     * Build a Rope from a ByteArray, possibly splitting into chunks
     * for more balanced leaf nodes.
     */
    private fun buildRopeFromByteArray(bytes: ByteArray, chunkSize: Int = 256): Rope {
        if (bytes.size <= chunkSize) {
            return Rope(LeafNode(bytes.copyOf()), minChunkSize, maxChunkSize)
        }

        // Split the array into chunks
        val nodes = mutableListOf<RopeNode>()
        var start = 0
        while (start < bytes.size) {
            val end = (start + chunkSize).coerceAtMost(bytes.size)
            val chunk = bytes.copyOfRange(start, end)
            nodes.add(LeafNode(chunk))
            start = end
        }

        // Build a balanced (ish) tree from the chunked leaf nodes
        val root = buildBalanced(nodes)
        return Rope(root, minChunkSize, maxChunkSize)
    }

    /**
     * Build a balanced rope node from a list of RopeNodes using pairwise combination.
     */
    private fun buildBalanced(nodes: List<RopeNode>): RopeNode {
        if (nodes.isEmpty())
            return LeafNode(ByteArray(0))

        if (nodes.size == 1)
            return nodes[0]

        var currentList = nodes
        while (currentList.size > 1) {
            val nextList = mutableListOf<RopeNode>()
            var i = 0
            while (i < currentList.size) {
                val left = currentList[i]
                val right = if (i + 1 < currentList.size) currentList[i + 1] else LeafNode(ByteArray(0))
                nextList.add(InternalNode(left, right, left.length()))
                i += 2
            }
            currentList = nextList
        }

        return currentList[0]
    }

    /**
     * Append this node's bytes to the output stream (in-order traversal).
     */
    private fun appendRopeNode(node: RopeNode, out: ByteArrayOutputStream) {
        when (node) {
            is LeafNode -> out.write(node.bytes)
            is InternalNode -> {
                appendRopeNode(node.left, out)
                appendRopeNode(node.right, out)
            }
        }
    }

    /**
     * Split a RopeNode at [index], returning (leftSubtree, rightSubtree).
     * (index is an absolute byte offset within node).
     */
    private fun split(node: RopeNode, index: Int): Pair<RopeNode, RopeNode> {
        return when (node) {
            is LeafNode -> {
                val leftBytes = node.bytes.copyOfRange(0, index)
                val rightBytes = node.bytes.copyOfRange(index, node.bytes.size)
                LeafNode(leftBytes) to LeafNode(rightBytes)
            }
            is InternalNode -> {
                val leftWeight = node.weight
                if (index < leftWeight) {
                    val (splitLeft, splitRight) = split(node.left, index)
                    splitLeft to concatNodes(splitRight, node.right)
                } else {
                    val newIndex = index - leftWeight
                    val (splitLeft, splitRight) = split(node.right, newIndex)
                    concatNodes(node.left, splitLeft) to splitRight
                }
            }
        }
    }

    /**
     * Concatenate two RopeNodes. Returns a new node that represents [left + right].
     */
    private fun concatNodes(left: RopeNode, right: RopeNode): RopeNode {
        if (left.length() == 0) return right
        if (right.length() == 0) return left
        return InternalNode(left, right, left.length())
    }

    /**
     * Return a substring [startIndex, endIndex) of the node as a new RopeNode.
     */
    private fun substringNode(node: RopeNode, startIndex: Int, endIndex: Int): RopeNode {
        return when (node) {
            is LeafNode -> {
                val subBytes = node.bytes.copyOfRange(startIndex, endIndex)
                LeafNode(subBytes)
            }
            is InternalNode -> {
                val leftLen = node.left.length()
                val startInLeft = startIndex < leftLen
                val endInLeft = endIndex <= leftLen
                val startInRight = startIndex >= leftLen
                val endInRight = endIndex > leftLen

                if (startInLeft && endInLeft) {
                    // Entirely in left
                    substringNode(node.left, startIndex, endIndex)
                } else if (startInRight && endInRight) {
                    // Entirely in right
                    substringNode(node.right, startIndex - leftLen, endIndex - leftLen)
                } else {
                    // Spans both
                    val leftPart = if (startIndex < leftLen) {
                        substringNode(node.left, startIndex, leftLen)
                    } else {
                        LeafNode(ByteArray(0))
                    }
                    val rightPart = if (endIndex > leftLen) {
                        substringNode(node.right, 0, endIndex - leftLen)
                    } else {
                        LeafNode(ByteArray(0))
                    }

                    return concatNodes(leftPart, rightPart)
                }
            }
        }
    }

    companion object {
        /**
         * Create a new Rope with an empty byte array.
         */
        fun create(
            minChunkSize: Int = 128,
            maxChunkSize: Int = 512
        ): Rope {
            // Create a LeafNode with an empty byte array
            return Rope(LeafNode(ByteArray(0)), minChunkSize, maxChunkSize)
        }

        /**
         * Build a Rope from a single byte.
         */
        fun buildRopeFromByte(b: Byte, minChunkSize: Int = 128, maxChunkSize: Int = 512): Rope {
            // Just wrap the single byte in a 1-element array
            val singleByteArray = byteArrayOf(b)
            return Rope(LeafNode(singleByteArray), minChunkSize, maxChunkSize)
        }

        /**
         * Build a Rope from a ByteArray, possibly splitting into chunks
         * for more balanced leaf nodes.
         */
        fun buildRopeFromByteArray(
            bytes: ByteArray,
            chunkSize: Int = 256,
            minChunkSize: Int = 128,
            maxChunkSize: Int = 512
        ): Rope {
            if (bytes.size <= chunkSize) {
                // Single leaf node is fine
                return Rope(LeafNode(bytes.copyOf()), minChunkSize, maxChunkSize)
            }

            // Otherwise, split into chunked LeafNodes, then build a balanced rope
            val nodes = mutableListOf<RopeNode>()
            var start = 0
            while (start < bytes.size) {
                val end = (start + chunkSize).coerceAtMost(bytes.size)
                val chunk = bytes.copyOfRange(start, end)
                nodes.add(LeafNode(chunk))
                start = end
            }

            val root = buildBalanced(nodes)
            return Rope(root, minChunkSize, maxChunkSize)
        }

        /**
         * Build a balanced rope node from a list of RopeNodes using pairwise combination.
         */
        private fun buildBalanced(nodes: List<RopeNode>): RopeNode {
            if (nodes.isEmpty())
                return LeafNode(ByteArray(0))

            if (nodes.size == 1)
                return nodes[0]

            var currentList = nodes
            while (currentList.size > 1) {
                val nextList = mutableListOf<RopeNode>()
                var i = 0
                while (i < currentList.size) {
                    val left = currentList[i]
                    val right = if (i + 1 < currentList.size) currentList[i + 1] else LeafNode(ByteArray(0))
                    nextList.add(InternalNode(left, right, left.length()))
                    i += 2
                }
                currentList = nextList
            }

            return currentList[0]
        }
    }

}
