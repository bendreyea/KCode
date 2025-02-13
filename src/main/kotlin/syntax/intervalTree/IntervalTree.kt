package org.editor.syntax.intervalTree

/**
 * A balanced binary-search tree keyed by Interval objects.
 *
 * Note: The tree is implemented as a red–black tree.
 */
class IntervalTree<T : Interval> : Iterable<T> {

    // The nil sentinel node; note that nil.parent/left/right all point to nil.
    private val nil = Node(key = null, isBlack = true).apply {
        parent = this
        left = this
        right = this
    }

    private var root = nil
    private var size = 0

    constructor()

    constructor(interval: T) : this() {
        val newNode = Node(key = interval, isBlack = false)
        newNode.blacken() // root must always be black.
        root = newNode
        size = 1
    }

    /**
     * Returns true if the tree is empty.
     */
    fun isEmpty(): Boolean = (root === nil)

    /**
     * Returns the number of intervals stored in the tree.
     */
    fun size(): Int = size

    /**
     * Clears the entire tree.
     */
    fun clear() {
        root = nil
        size = 0
    }

    /**
     * Returns true if an interval with the same [start,end] as [interval] is in this tree.
     */
    operator fun contains(interval: T): Boolean {
        val node = search(interval)
        return if (node.isNil) false else node.intervals.contains(interval)
    }

    /**
     * Inserts the given interval into the red–black tree.
     * Duplicates (when compareTo returns 0) are stored in the same node.
     *
     * @return true if the interval was newly added; false if it already existed.
     */
    fun insert(interval: T): Boolean {
        var parent = nil
        var current = root

        // Standard BST insert; update maxEnd along the way.
        while (!current.isNil) {
            parent = current
            parent.updateMaxEndWith(interval.end)

            val cmp = interval.compareTo(current.key!!)
            current = when {
                cmp < 0 -> current.left
                cmp > 0 -> current.right
                else -> {
                    // Same key: add to the set of intervals if not already present.
                    val added = current.intervals.add(interval)
                    if (added) size++
                    return added
                }
            }
        }

        // Create a new red node.
        val newNode = Node(key = interval, isBlack = false)

        if (parent.isNil) {
            // Tree was empty.
            root = newNode
        } else {
            if (interval < parent.key!!) {
                parent.left = newNode
            } else {
                parent.right = newNode
            }
            newNode.parent = parent
        }
        size++

        // Fix up any red–black violations.
        fixInsert(newNode)

        // Update maxEnd for all ancestors.
        updateMaxEndUpwards(newNode)

        return true
    }

    /**
     * Deletes the given interval from the tree.
     * If the node contains duplicate intervals, only the matching interval is removed.
     * When the last duplicate is removed, the node is deleted.
     *
     * @return true if the tree was modified.
     */
    fun delete(interval: T): Boolean {
        val node = search(interval)
        if (node.isNil) return false

        val removed = node.intervals.remove(interval)
        if (!removed) return false

        size--

        if (node.intervals.isNotEmpty()) {
            // Only structural change is not needed; recalc maxEnd upward.
            node.recalcMaxEnd()
            updateMaxEndUpwards(node)
            return true
        }

        // Standard red–black delete for node removal.
        rbDelete(node)
        return true
    }

    /**
     * Returns a sorted list of all intervals in ascending order.
     */
    fun getAll(): List<T> = this.toList()

    /**
     * Returns an iterator that traverses intervals in ascending order.
     */
    override fun iterator(): Iterator<T> = TreeIterator(root)

    /**
     * Returns a list of all intervals overlapping the given query range.
     *
     * Two intervals [a, b] and [c, d] overlap if a ≤ d and b ≥ c.
     *
     * This implementation uses an iterative approach (with an explicit stack)
     * to avoid potential stack overflow with deep trees.
     */
    fun queryOverlapping(queryStart: Int, queryEnd: Int): List<T> {
        val result = mutableListOf<T>()
        val stack = ArrayDeque<Node>()
        if (!root.isNil) stack.add(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()

            // Check left subtree if it might contain intervals overlapping the query.
            if (!node.left.isNil && node.left.maxEnd >= queryStart) {
                stack.add(node.left)
            }

            // Check the current node.
            if (node.key!!.start <= queryEnd && node.key!!.end >= queryStart) {
                result.addAll(node.intervals)
            }

            // Check right subtree if the current node's start does not exceed queryEnd.
            if (!node.right.isNil && node.key!!.start <= queryEnd) {
                stack.add(node.right)
            }
        }
        return result
    }

    /**
     * Returns the interval whose end is closest (and less than or equal) to [position].
     */
    fun queryClosestBefore(position: Int): T? {
        var node = root
        var closest: T? = null

        while (!node.isNil) {
            if (node.key!!.end <= position) {
                closest = node.key
                node = node.right
            } else {
                node = node.left
            }
        }
        return closest
    }

    /**
     * Returns the interval whose start is closest (and greater than or equal) to [position].
     */
    fun queryClosestAfter(position: Int): T? {
        var node = root
        var closest: T? = null

        while (!node.isNil) {
            if (node.key!!.start >= position) {
                closest = node.key
                node = node.left
            } else {
                node = node.right
            }
        }
        return closest
    }

    // --------------------------------------------------------------------
    //  Red–Black Node and Helper Methods
    // --------------------------------------------------------------------
    private inner class Node(
        var key: T? = null,
        var isBlack: Boolean = false
    ) {
        var parent: Node = nil
        var left: Node = nil
        var right: Node = nil

        // For interval trees: maxEnd is the maximum end value in this subtree.
        var maxEnd: Int = key?.end ?: Int.MIN_VALUE

        // In case of duplicate intervals, they are stored in this set.
        var intervals: MutableSet<T> = mutableSetOf<T>().apply {
            key?.let { add(it) }
        }

        val isNil: Boolean
            get() = (this === nil)

        fun redden() {
            isBlack = false
        }

        fun blacken() {
            isBlack = true
        }

        /**
         * Update this node's maxEnd if [candidateEnd] is greater.
         */
        fun updateMaxEndWith(candidateEnd: Int) {
            if (candidateEnd > maxEnd) {
                maxEnd = candidateEnd
            }
        }

        /**
         * Recalculate maxEnd based on this node’s key and its children.
         */
        fun recalcMaxEnd() {
            var best = key?.end ?: Int.MIN_VALUE
            if (!left.isNil) best = maxOf(best, left.maxEnd)
            if (!right.isNil) best = maxOf(best, right.maxEnd)
            maxEnd = best
        }
    }

    /**
     * Standard BST search. Returns the node with the matching interval or the nil sentinel.
     */
    private fun search(interval: T): Node {
        var current = root
        while (!current.isNil) {
            val cmp = interval.compareTo(current.key!!)
            current = when {
                cmp < 0 -> current.left
                cmp > 0 -> current.right
                else -> return current // Found it.
            }
        }
        return nil
    }

    /**
     * Performs a left rotation at node [x].
     */
    private fun leftRotate(x: Node) {
        val y = x.right
        x.right = y.left
        if (!y.left.isNil) {
            y.left.parent = x
        }
        y.parent = x.parent
        if (x.parent.isNil) {
            root = y
        } else if (x === x.parent.left) {
            x.parent.left = y
        } else {
            x.parent.right = y
        }
        y.left = x
        x.parent = y

        // Update maxEnd values.
        x.recalcMaxEnd()
        y.recalcMaxEnd()
    }

    /**
     * Performs a right rotation at node [x].
     */
    private fun rightRotate(x: Node) {
        val y = x.left
        x.left = y.right
        if (!y.right.isNil) {
            y.right.parent = x
        }
        y.parent = x.parent
        if (x.parent.isNil) {
            root = y
        } else if (x === x.parent.right) {
            x.parent.right = y
        } else {
            x.parent.left = y
        }
        y.right = x
        x.parent = y

        // Update maxEnd values.
        x.recalcMaxEnd()
        y.recalcMaxEnd()
    }

    /**
     * Fixes up the tree after inserting a red node [z].
     */
    private fun fixInsert(z: Node) {
        var current = z
        while (!current.parent.isNil && !current.parent.isBlack) {
            val parent = current.parent
            val grand = parent.parent
            if (parent === grand.left) {
                val uncle = grand.right
                if (!uncle.isBlack) {
                    // Case 1: Uncle is red.
                    parent.blacken()
                    uncle.blacken()
                    grand.redden()
                    current = grand
                } else {
                    // Case 2 or 3.
                    if (current === parent.right) {
                        current = parent
                        leftRotate(current)
                    }
                    parent.blacken()
                    grand.redden()
                    rightRotate(grand)
                }
            } else {
                // Mirror cases.
                val uncle = grand.left
                if (!uncle.isBlack) {
                    parent.blacken()
                    uncle.blacken()
                    grand.redden()
                    current = grand
                } else {
                    if (current === parent.left) {
                        current = parent
                        rightRotate(current)
                    }
                    parent.blacken()
                    grand.redden()
                    leftRotate(grand)
                }
            }
            if (current === root) break
        }
        root.blacken()
    }

    /**
     * Standard red–black deletion. Removes node [z] and fixes any violations.
     */
    private fun rbDelete(z: Node) {
        var y = z
        var yOriginalColor = y.isBlack
        val x: Node

        if (z.left.isNil) {
            x = z.right
            transplant(z, z.right)
        } else if (z.right.isNil) {
            x = z.left
            transplant(z, z.left)
        } else {
            val successor = treeMinimum(z.right)
            y = successor
            yOriginalColor = y.isBlack
            val tempX = y.right
            if (y.parent === z) {
                tempX.parent = y
            } else {
                transplant(y, y.right)
                y.right = z.right
                y.right.parent = y
            }
            transplant(z, y)
            y.left = z.left
            y.left.parent = y
            y.isBlack = z.isBlack
            y.recalcMaxEnd()
            x = tempX
        }

        if (yOriginalColor) {
            fixDelete(x)
        }
        updateMaxEndUpwards(x)
    }

    /**
     * Fix-up for red–black deletion.
     */
    private fun fixDelete(x: Node) {
        var current = x
        while (current !== root && current.isBlack) {
            if (current === current.parent.left) {
                var sibling = current.parent.right
                if (!sibling.isBlack) {
                    sibling.blacken()
                    current.parent.redden()
                    leftRotate(current.parent)
                    sibling = current.parent.right
                }
                if (sibling.left.isBlack && sibling.right.isBlack) {
                    sibling.redden()
                    current = current.parent
                } else {
                    if (sibling.right.isBlack) {
                        sibling.left.blacken()
                        sibling.redden()
                        rightRotate(sibling)
                        sibling = current.parent.right
                    }
                    sibling.isBlack = current.parent.isBlack
                    current.parent.blacken()
                    sibling.right.blacken()
                    leftRotate(current.parent)
                    current = root
                }
            } else {
                // Mirror case.
                var sibling = current.parent.left
                if (!sibling.isBlack) {
                    sibling.blacken()
                    current.parent.redden()
                    rightRotate(current.parent)
                    sibling = current.parent.left
                }
                if (sibling.right.isBlack && sibling.left.isBlack) {
                    sibling.redden()
                    current = current.parent
                } else {
                    if (sibling.left.isBlack) {
                        sibling.right.blacken()
                        sibling.redden()
                        leftRotate(sibling)
                        sibling = current.parent.left
                    }
                    sibling.isBlack = current.parent.isBlack
                    current.parent.blacken()
                    sibling.left.blacken()
                    rightRotate(current.parent)
                    current = root
                }
            }
        }
        current.blacken()
    }

    /**
     * Replaces the subtree rooted at [u] with the subtree rooted at [v].
     */
    private fun transplant(u: Node, v: Node) {
        if (u.parent.isNil) {
            root = v
        } else if (u === u.parent.left) {
            u.parent.left = v
        } else {
            u.parent.right = v
        }
        v.parent = u.parent
    }

    /**
     * Returns the leftmost node in the subtree rooted at [node].
     */
    private fun treeMinimum(node: Node): Node {
        var current = node
        while (!current.left.isNil) {
            current = current.left
        }
        return current
    }

    /**
     * Walk upward from [startNode] to the root, recalculating maxEnd values.
     */
    private fun updateMaxEndUpwards(startNode: Node) {
        var n = startNode
        while (!n.isNil) {
            n.recalcMaxEnd()
            n = n.parent
        }
    }

    // --------------------------------------------------------------------
    //  In‑Order Iterator
    // --------------------------------------------------------------------
    private inner class TreeIterator(root: Node) : Iterator<T> {
        private val stack = ArrayDeque<Node>()
        private var currentIntervalIter: Iterator<T>? = null

        init {
            pushLeft(root)
        }

        override fun hasNext(): Boolean {
            if (currentIntervalIter?.hasNext() == true) return true

            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                currentIntervalIter = node.intervals.iterator()
                pushLeft(node.right)
                if (currentIntervalIter?.hasNext() == true) {
                    return true
                }
            }
            return false
        }

        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            return currentIntervalIter!!.next()
        }

        private fun pushLeft(node: Node) {
            var cur = node
            while (!cur.isNil) {
                stack.addLast(cur)
                cur = cur.left
            }
        }
    }
}
