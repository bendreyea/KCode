package org.editor.syntax.intervalTree

/**
 * A balanced binary-search tree keyed by Interval objects.
 *
 * This tree does not store exact duplicates, but will store Intervals that
 * have identical coordinates but differ in some other aspect.
 *
 * The underlying data-structure is a red-black tree.
 */
class IntervalSetTree<T : Interval> : Iterable<T> {

    // Define the 'nil' node as a property of the outer class
    private val nil: Node = Node().apply { blacken() }
    private var root: Node = nil
    private var size = 0

    /**
     * Constructs an empty IntervalSetTree.
     */
    constructor()

    /**
     * Constructs an IntervalSetTree with a single node containing the given Interval.
     * @param t - the Interval to add to this IntervalSetTree
     */
    constructor(t: T) {
        val newNode = Node(t, parent = nil, left = nil, right = nil)
        newNode.blacken()
        root = newNode
        size = 1
    }

    /**
     * Checks whether this IntervalSetTree is empty.
     */
    fun isEmpty(): Boolean = root.isNil

    /**
     * Returns the number of intervals stored in this IntervalSetTree.
     */
    fun size(): Int = size

    /**
     * Searches for the Node in this tree that matches the given Interval.
     * @param t - the Interval to search for
     */
    private fun search(t: T): Node = root.search(t)

    /**
     * Checks whether this IntervalSetTree contains the given Interval.
     * @param t - the Interval to search for
     */
    operator fun contains(t: T): Boolean = search(t).intervals.contains(t)

    /**
     * Inserts the given Interval into this IntervalSetTree.
     * @param t - the Interval to place into this tree
     * @return true if the tree was modified, false otherwise
     */
    fun insert(t: T): Boolean {
        var parent = nil
        var current = root

        while (!current.isNil) {
            parent = current
            current.maxEnd = maxOf(current.maxEnd, t.end)

            val cmp = t.compareTo(current.key!!)
            if (cmp == 0) {
                if (current.intervals.add(t)) {
                    size++
                    return true
                }
                return false
            }
            current = if (cmp < 0) current.left else current.right
        }

        val newNode = Node(t, parent = parent, left = nil, right = nil)
        if (parent.isNil) {
            root = newNode
            root.blacken()
        } else {
            if (t < parent.key!!) {
                parent.left = newNode
            } else {
                parent.right = newNode
            }
            newNode.redden()
            newNode.fixInsert(nil)
        }
        size++
        return true
    }

    /**
     * Deletes the given Interval from this IntervalSetTree.
     * @param t - the Interval to delete from the tree
     * @return true if the tree was modified, false otherwise
     */
    fun delete(t: T): Boolean {
        val node = search(t)
        val removed = node.intervals.remove(t)
        if (removed) size--

        if (node.intervals.isEmpty()) {
            node.delete(nil)
        }
        return removed
    }

    /**
     * Iterator to traverse the tree in ascending order.
     */
    override fun iterator(): Iterator<T> = TreeIterator(root)

    /**
     * Node class representing each node in the IntervalSetTree.
     */
    private inner class Node(
        var key: T? = null,
        var parent: Node = nil,
        var left: Node = nil,
        var right: Node = nil,
        var isBlack: Boolean = false,
        var maxEnd: Int = key?.end ?: Int.MIN_VALUE,
        var intervals: MutableSet<T> = if (key != null) mutableSetOf(key) else mutableSetOf()

    ) {

        val isNil: Boolean
            get() = this === nil

        fun redden() {
            isBlack = false
        }

        fun blacken() {
            isBlack = true
        }

        fun search(t: T): Node {
            var current: Node = this
            while (!current.isNil) {
                val cmp = t.compareTo(current.key!!)
                when {
                    cmp < 0 -> current = current.left
                    cmp > 0 -> current = current.right
                    else -> return current
                }
            }
            return current
        }

        fun fixInsert(nilNode: Node) {
            var current = this
            while (!current.parent.isBlack) {
                val parent = current.parent
                val grandparent = parent.parent
                if (parent === grandparent.left) {
                    val uncle = grandparent.right
                    if (!uncle.isBlack) {
                        parent.blacken()
                        uncle.blacken()
                        grandparent.redden()
                        current = grandparent
                    } else {
                        if (current === parent.right) {
                            current = parent
                            current.leftRotate(nilNode)
                        }
                        parent.blacken()
                        grandparent.redden()
                        grandparent.rightRotate(nilNode)
                    }
                } else {
                    val uncle = grandparent.left
                    if (!uncle.isBlack) {
                        parent.blacken()
                        uncle.blacken()
                        grandparent.redden()
                        current = grandparent
                    } else {
                        if (current === parent.left) {
                            current = parent
                            current.rightRotate(nilNode)
                        }
                        parent.blacken()
                        grandparent.redden()
                        grandparent.leftRotate(nilNode)
                    }
                }
                if (current === root) break
            }
            root.blacken()
        }

        // TODO: Implement deletion logic
        fun delete(nilNode: Node) {
            // Implement deletion logic with red-black tree balancing
            // To be implemented
        }

        fun leftRotate(nilNode: Node) {
            val y = this.right
            this.right = y.left
            if (!y.left.isNil) {
                y.left.parent = this
            }
            y.parent = this.parent
            if (this.parent.isNil) {
                // Update root if necessary
                root = y
            } else if (this === this.parent.left) {
                this.parent.left = y
            } else {
                this.parent.right = y
            }
            y.left = this
            this.parent = y

            // Update maxEnd
            this.maxEnd = maxOf(this.key?.end ?: Int.MIN_VALUE, left.maxEnd, right.maxEnd)
            y.maxEnd = maxOf(y.key?.end ?: Int.MIN_VALUE, y.left.maxEnd, y.right.maxEnd)
        }

        fun rightRotate(nilNode: Node) {
            val y = this.left
            this.left = y.right
            if (!y.right.isNil) {
                y.right.parent = this
            }
            y.parent = this.parent
            if (this.parent.isNil) {
                // Update root if necessary
                root = y
            } else if (this === this.parent.right) {
                this.parent.right = y
            } else {
                this.parent.left = y
            }
            y.right = this
            this.parent = y

            // Update maxEnd
            this.maxEnd = maxOf(this.key?.end ?: Int.MIN_VALUE, left.maxEnd, right.maxEnd)
            y.maxEnd = maxOf(y.key?.end ?: Int.MIN_VALUE, y.left.maxEnd, y.right.maxEnd)
        }
    }

    /**
     * Iterator class for in-order traversal.
     */
    private inner class TreeIterator(root: Node) : Iterator<T> {
        private val stack: ArrayDeque<Node> = ArrayDeque()
        private var currentIterator: Iterator<T>? = null

        init {
            pushLeft(root)
        }

        private fun pushLeft(node: Node) {
            var current = node
            while (!current.isNil) {
                stack.addLast(current)
                current = current.left
            }
        }

        override fun hasNext(): Boolean {
            if (currentIterator?.hasNext() == true) return true
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                currentIterator = node.intervals.iterator()
                pushLeft(node.right)
                if (currentIterator?.hasNext() == true) return true
            }
            return false
        }

        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            return currentIterator!!.next()
        }
    }
}

