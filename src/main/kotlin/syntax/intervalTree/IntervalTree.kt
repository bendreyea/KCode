package org.editor.syntax.intervalTree

/**
 * A balanced binary-search tree keyed by Interval objects.
 */
class IntervalTree<T : Interval> : Iterable<T> {

    private val nil = Node(key = null, isBlack = true).apply {
        parent = this
        left = this
        right = this
    }

    // Sentinel node
    private var root = nil
    private var size = 0

    constructor()

    constructor(interval: T) : this() {
        val newNode = Node(key = interval, isBlack = false)
        // newNode's parent, left, right are nil by default
        // let’s say the root is black
        newNode.blacken()
        root = newNode
        size = 1
    }

    /**
     * Whether the tree is empty.
     */
    fun isEmpty(): Boolean = (root == nil)

    /**
     * Number of intervals in the tree (including duplicates).
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
     * Returns true if an interval with the same [start,end] as 'interval'
     * is in this tree.  (If duplicates are stored, this just checks
     * for any matching 'Interval' in that node's set.)
     */
    operator fun contains(interval: T): Boolean {
        val node = search(interval)
        if (node.isNil)
            return false

        return node.intervals.contains(interval)
    }

    /**
     * Insert the given interval into the red-black tree, storing
     * duplicates in the same node if compareTo == 0.
     * @return true if a new interval was added; false if it's already present
     */
    fun insert(interval: T): Boolean {
        var parent = nil
        var current = root

        // Standard BST insert logic
        while (!current.isNil) {
            parent = current

            // On the way down, update the parent's maxEnd
            parent.updateMaxEndWith(interval.end)

            val cmp = interval.compareTo(current.key!!)
            current = when {
                cmp < 0 -> current.left
                cmp > 0 -> current.right
                else -> {
                    // Same key => store in that node's set if not already present
                    val added = current.intervals.add(interval)
                    return if (added) {
                        size++
                        true
                    } else {
                        false
                    }
                }
            }
        }

        // Create new node
        val newNode = Node(
            key = interval,
            isBlack = false, // new nodes are red by default
        )

        if (parent.isNil) {
            // The tree was empty
            root = newNode
        } else {
            if (interval < parent.key!!) {
                parent.left = newNode
            } else {
                parent.right = newNode
            }
        }

        size++

        // Fix up red-black invariants
        fixInsert(newNode)

        // After rotation/fix, ensure all ancestors have correct maxEnd
        updateMaxEndUpwards(newNode)

        return true
    }

    /**
     * Delete 'interval' from the tree. If that node has duplicates,
     * remove just that one from the 'intervals' set; if no duplicates remain,
     * remove the node entirely from the red-black tree.
     * @return true if the tree was modified
     */
    fun delete(interval: T): Boolean {
        val node = search(interval)
        if (node.isNil)
            return false

        val removed = node.intervals.remove(interval)
        if (!removed)
            return false

        size--

        if (node.intervals.isNotEmpty()) {
            // Just recalc maxEnd upward; no structural changes
            node.recalcMaxEnd()
            updateMaxEndUpwards(node)
            return true
        }

        // do the standard red-black delete for node with key
        rbDelete(node)

        return true
    }

    /**
     * Returns a sorted list of all intervals in ascending order.
     */
    fun getAll(): List<T> {
        val result = mutableListOf<T>()
        for (item in this) {
            result.add(item)
        }

        return result
    }

    /**
     * Returns an iterator that traverses intervals in ascending order by (start,end).
     */
    override fun iterator(): Iterator<T> = TreeIterator(root)

    // --------------------------------------------------------------------
    //  Red‑Black Node
    // --------------------------------------------------------------------
    private inner class Node(
        var key: T? = null,
        var isBlack: Boolean = false
    ) {
        var parent: Node = nil
        var left: Node = nil
        var right: Node = nil

        // E.g. for interval trees:
        var maxEnd: Int = key?.end ?: Int.MIN_VALUE
        var intervals: MutableSet<T> = if (key != null) mutableSetOf(key!!) else mutableSetOf()

        val isNil: Boolean
            get() = (this === nil)

        fun redden() {
            isBlack = false
        }

        fun blacken() {
            isBlack = true
        }

        /**
         * Update this node's maxEnd if 'candidateEnd' is greater.
         */
        fun updateMaxEndWith(candidateEnd: Int) {
            if (candidateEnd > maxEnd) {
                maxEnd = candidateEnd
            }
        }

        /**
         * Recalculate maxEnd from its own key, left child, right child.
         */
        fun recalcMaxEnd() {
            var best = key?.end ?: Int.MIN_VALUE
            if (!left.isNil) best = maxOf(best, left.maxEnd)
            if (!right.isNil) best = maxOf(best, right.maxEnd)
            maxEnd = best
        }
    }


    /**
     * Standard BST search. If not found, returns the
     * 'nil' sentinel.
     */
    private fun search(interval: T): Node {
        var current = root
        while (!current.isNil) {
            val cmp = interval.compareTo(current.key!!)
            current = when {
                cmp < 0 -> current.left
                cmp > 0 -> current.right
                else -> return current  // found it
            }
        }
        return nil // not found
    }

    /**
     * Rotate left at node x.
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

        // Recalc maxEnd for x, then for y
        x.recalcMaxEnd()
        y.recalcMaxEnd()
    }

    /**
     * Rotate right at node x.
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

        // Recalc maxEnd for x, then for y
        x.recalcMaxEnd()
        y.recalcMaxEnd()
    }

    /**
     * After inserting a red node, fix possible red–red conflicts up the tree.
     */
    private fun fixInsert(z: Node) {
        var current = z
        while (!current.parent.isNil && !current.parent.isBlack) {
            val parent = current.parent
            val grand = parent.parent
            if (parent === grand.left) {
                val uncle = grand.right
                if (!uncle.isBlack) {
                    // Case 1: Uncle is red
                    parent.blacken()
                    uncle.blacken()
                    grand.redden()
                    current = grand
                } else {
                    // Case 2 or 3
                    if (current === parent.right) {
                        // Case 2: transform to case 3
                        current = parent
                        leftRotate(current)
                    }
                    // Case 3
                    parent.blacken()
                    grand.redden()
                    rightRotate(grand)
                }
            } else {
                // mirror
                val uncle = grand.left
                if (!uncle.isBlack) {
                    // Case 1
                    parent.blacken()
                    uncle.blacken()
                    grand.redden()
                    current = grand
                } else {
                    // Case 2 or 3
                    if (current === parent.left) {
                        current = parent
                        rightRotate(current)
                    }
                    parent.blacken()
                    grand.redden()
                    leftRotate(grand)
                }
            }

            if (current === root)
                break
        }
        root.blacken()
    }

    /**
     * Standard RB-Delete procedure, removing 'z' from the tree,
     * then fix any red-black property violations.
     */
    private fun rbDelete(z: Node) {
        var y = z
        var yOriginalColor = y.isBlack
        var x: Node

        // If z has at most one non-nil child, we remove z in-place.
        // Else we find z's successor, swap the data, and remove successor.

        if (z.left.isNil) {
            x = z.right
            transplant(z, z.right)
        } else if (z.right.isNil) {
            x = z.left
            transplant(z, z.left)
        } else {
            // Two children: find successor
            val successor = treeMinimum(z.right)
            y = successor
            yOriginalColor = y.isBlack

            // x is y's right child
            x = y.right
            if (y.parent === z) {
                x.parent = y
            } else {
                transplant(y, y.right)
                y.right = z.right
                y.right.parent = y
            }
            transplant(z, y)

            y.left = z.left
            y.left.parent = y
            y.isBlack = z.isBlack

            // We also need to copy over the maxEnd, intervals, key if needed
            // But typically we are physically removing z's node
            y.recalcMaxEnd()
        }

        // If the removed node (or replaced node) was black, fix the tree
        if (yOriginalColor) {
            fixDelete(x)
        }

        // Recompute maxEnd up the chain
        updateMaxEndUpwards(x)
    }

    /**
     * Standard RB Delete fix-up, ensuring red-black properties hold.
     */
    private fun fixDelete(x: Node) {
        var current = x
        while (current !== root && current.isBlack) {
            if (current === current.parent.left) {
                var sibling = current.parent.right
                if (!sibling.isBlack) {
                    // Case 1
                    sibling.blacken()
                    current.parent.redden()
                    leftRotate(current.parent)
                    sibling = current.parent.right
                }
                if (sibling.left.isBlack && sibling.right.isBlack) {
                    // Case 2
                    sibling.redden()
                    current = current.parent
                } else {
                    if (sibling.right.isBlack) {
                        // Case 3
                        sibling.left.blacken()
                        sibling.redden()
                        rightRotate(sibling)
                        sibling = current.parent.right
                    }
                    // Case 4
                    sibling.isBlack = current.parent.isBlack
                    current.parent.blacken()
                    sibling.right.blacken()
                    leftRotate(current.parent)
                    current = root
                }
            } else {
                // mirror
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
     * Replaces subtree 'u' with subtree 'v' in the tree. (Used for delete.)
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
     * Returns the leftmost node in subtree 'node'.
     */
    private fun treeMinimum(node: Node): Node {
        var current = node
        while (!current.left.isNil) {
            current = current.left
        }

        return current
    }

    /**
     * Walk upward from 'startNode' to the root, recalculating maxEnd.
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
            // If the current node's intervals are not exhausted, we have a next
            if (currentIntervalIter?.hasNext() == true) {
                return true
            }

            // Otherwise, pop the next node off the stack, get its intervals, pushLeft(node.right)
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