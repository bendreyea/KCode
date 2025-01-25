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
    private var root: RopeNode
)  {

    /**
     * Returns the total length of the rope (in bytes).
     */
    fun length(): Long = root.length().toLong()

    /**
     * Returns a copy of all bytes in this rope (in-order traversal).
     */
    fun toByteArray(): ByteArray {
        val out = ByteArrayOutputStream(root.length().coerceAtLeast(16))
        appendRopeNode(root, out)
        return out.toByteArray()
    }

    /**
     * Concatenate another rope to this rope (modifies this rope).
     *
     * For very large ropes, we should re-balance periodically.
     */
    fun concat(other: Rope): Rope {
        root = concatNodes(root, other.root)
        return this
    }

    /**
     * Returns a new Rope that is a substring of this rope from [startIndex] (inclusive)
     * to [endIndex] (exclusive).
     */
    fun substring(startIndex: Int, endIndex: Int): Rope {
        require(startIndex in 0..endIndex) { "Invalid substring range" }
        require(endIndex <= length()) { "endIndex must be <= rope length" }

        val newRoot = substringNode(root, startIndex, endIndex)
        return Rope(newRoot)
    }

    /**
     * Insert the given bytes into this rope at the given [index].
     */
    fun insert(index: Int, bytes: ByteArray): Rope {
        require(index in 0..length()) { "Insert index out of bounds" }
        // Split at index, build new leaf node for these bytes, then rejoin
        val (leftPart, rightPart) = split(root, index)
        val middle = buildRopeFromByteArray(bytes).root
        val tmp = concatNodes(leftPart, middle)
        root = concatNodes(tmp, rightPart)
        return this
    }

    /**
     * Delete the portion of the rope between [startIndex, endIndex).
     */
    fun delete(startIndex: Int, endIndex: Int): Rope {
        require(startIndex in 0..endIndex) { "Invalid delete range" }
        require(endIndex <= length()) { "endIndex must be <= rope length" }

        val (leftPart, tmpRight) = split(root, startIndex)
        val (_, rightPart) = split(tmpRight, endIndex - startIndex)
        root = concatNodes(leftPart, rightPart)
        return this
    }

    /**
     * Optional: for debugging, show the rope as a hex string or ASCII, etc.
     * Here we just convert to a standard string assuming UTF-8, but be aware
     * that data may not be valid UTF-8 if truly binary.
     */
    override fun toString(): String {
        val bytes = toByteArray()
        return String(bytes)
    }

    companion object {

        /**
         * Build a Rope from a ByteArray, possibly splitting into chunks
         * for more balanced leaf nodes.
         */
        fun buildRopeFromByteArray(bytes: ByteArray, chunkSize: Int = 256): Rope {
            if (bytes.size <= chunkSize) {
                return Rope(LeafNode(bytes.copyOf()))
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
            return Rope(root)
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
            if (left.length() == 0)
                return right

            if (right.length() == 0)
                return left

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
                        concatNodes(leftPart, rightPart)
                    }
                }
            }
        }
    }
}
