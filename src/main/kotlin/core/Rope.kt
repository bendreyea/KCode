package org.editor.core

sealed class RopeNode {
    abstract fun length(): Int
}

/**
 * A leaf node that directly stores text.
 */
data class LeafNode(val text: String) : RopeNode() {
    override fun length(): Int = text.length
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
class Rope(private var root: RopeNode) {

    /**
     * Returns the total length of the rope.
     */
    fun length(): Int = root.length()

    /**
     * Convert the rope to a single String by in-order traversal.
     */
    override fun toString(): String = buildString {
        appendRopeNode(root, this)
    }

    /**
     * Concatenate another rope to this rope (in place).
     */
    fun concat(other: Rope): Rope {
        root = concatNodes(root, other.root)
        return this
    }

    /**
     * Returns a new Rope that is a substring of this rope from [startIndex] to [endIndex].
     */
    fun substring(startIndex: Int, endIndex: Int): Rope {
        require(startIndex in 0..endIndex) { "Invalid substring range" }
        require(endIndex <= length()) { "endIndex must be <= rope length" }

        val newRoot = substringNode(root, startIndex, endIndex)
        return Rope(newRoot)
    }

    /**
     * Insert [text] into this rope at the given [index].
     * Fixing the type mismatch by getting the underlying node from the Rope object we build.
     */
    fun insert(index: Int, text: String): Rope {
        require(index in 0..length()) { "Insert index out of bounds" }
        val (leftPart, rightPart) = split(root, index)
        // Build the 'middle' as a RopeNode, not a Rope
        val middle = buildRopeFromString(text).root
        root = concatNodes(concatNodes(leftPart, middle), rightPart)
        return this
    }

    fun insertChar(index: Int, ch: Char): Rope {
        insert(index, ch.toString())
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

    companion object {

        /**
         * Build a Rope from a simple String.
         * To improve performance, split the input into chunks.
         * For real-world usage, you might experiment with different chunk sizes.
         */
        fun buildRopeFromString(text: String, chunkSize: Int = 256): Rope {
            // If text is short enough, just return single leaf
            if (text.length <= chunkSize) {
                return Rope(LeafNode(text))
            }

            // Split text into manageable chunks
            val nodes = mutableListOf<RopeNode>()
            var start = 0
            while (start < text.length) {
                val end = (start + chunkSize).coerceAtMost(text.length)
                val chunk = text.substring(start, end)
                nodes.add(LeafNode(chunk))
                start = end
            }

            // Build a balanced(ish) tree from the chunked LeafNodes
            return Rope(buildBalanced(nodes))
        }

        /**
         * Build a balanced rope node from a list of RopeNodes using a simple pairwise combine.
         */
        private fun buildBalanced(nodes: List<RopeNode>): RopeNode {
            if (nodes.isEmpty()) return LeafNode("")
            if (nodes.size == 1) return nodes[0]

            // Combine pairwise
            var currentList = nodes
            while (currentList.size > 1) {
                val nextList = mutableListOf<RopeNode>()
                var i = 0
                while (i < currentList.size) {
                    val left = currentList[i]
                    val right = if (i + 1 < currentList.size) currentList[i + 1] else LeafNode("")
                    nextList.add(InternalNode(left, right, left.length()))
                    i += 2
                }
                currentList = nextList
            }
            return currentList[0]
        }

        /**
         * Split [node] at [index], returning a pair (leftSubtree, rightSubtree).
         */
        private fun split(node: RopeNode, index: Int): Pair<RopeNode, RopeNode> {
            return when (node) {
                is LeafNode -> {
                    val leftText = node.text.substring(0, index)
                    val rightText = node.text.substring(index)
                    Pair(LeafNode(leftText), LeafNode(rightText))
                }
                is InternalNode -> {
                    val leftWeight = node.weight
                    return if (index < leftWeight) {
                        val (splitLeft, splitRight) = split(node.left, index)
                        Pair(splitLeft, concatNodes(splitRight, node.right))
                    } else {
                        val newIndex = index - leftWeight
                        val (splitLeft, splitRight) = split(node.right, newIndex)
                        Pair(concatNodes(node.left, splitLeft), splitRight)
                    }
                }
            }
        }

        /**
         * Concatenate two RopeNodes.
         */
        private fun concatNodes(left: RopeNode, right: RopeNode): RopeNode {
            if (left.length() == 0)
                return right

            if (right.length() == 0)
                return left

            return InternalNode(left, right, left.length())
        }

        /**
         * Return a substring [startIndex, endIndex) of the node.
         */
        private fun substringNode(node: RopeNode, startIndex: Int, endIndex: Int): RopeNode {
            return when (node) {
                is LeafNode -> {
                    LeafNode(node.text.substring(startIndex, endIndex))
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
                        } else LeafNode("")
                        val rightPart = if (endIndex > leftLen) {
                            substringNode(node.right, 0, endIndex - leftLen)
                        } else LeafNode("")

                        concatNodes(leftPart, rightPart)
                    }
                }
            }
        }

        /**
         * Append this node's text to the StringBuilder (in-order traversal).
         */
        private fun appendRopeNode(node: RopeNode, sb: StringBuilder) {
            when (node) {
                is LeafNode -> sb.append(node.text)
                is InternalNode -> {
                    appendRopeNode(node.left, sb)
                    appendRopeNode(node.right, sb)
                }
            }
        }
    }
}
