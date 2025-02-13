package org.editor.application.common

/**
 * An augmented AVL tree (order–statistic tree) implementation that maps between
 * “serial” offsets and row/column positions.
 *
 * @param T the type of data processed (e.g. ByteArray or String).
 * @param Self the concrete type that extends OSTRowIndex.
 * @param newlineToken a token representing the newline.
 * @param splitFunction a lambda to split incoming into row–length increments.
 */
abstract class OSTRowIndex<T, Self : OSTRowIndex<T, Self>>(
    protected val newlineToken: T,
    protected val splitFunction: (T) -> IntArray
) {

    protected data class Node(
        var rowLength: Int,
        var left: Node? = null,
        var right: Node? = null,
        var height: Int = 1,
        var size: Int = 1,             // number of nodes in this subtree
        var sum: Long = rowLength.toLong() // cumulative rowLength for this subtree
    )

    // The root of the AVL tree. We start with one empty row.
    protected var root: Node? = Node(0)

    private fun height(node: Node?): Int = node?.height ?: 0
    private fun size(node: Node?): Int = node?.size ?: 0
    private fun sum(node: Node?): Long = node?.sum ?: 0L

    protected fun update(node: Node) {
        node.height = maxOf(height(node.left), height(node.right)) + 1
        node.size = size(node.left) + size(node.right) + 1
        node.sum = sum(node.left) + sum(node.right) + node.rowLength.toLong()
    }

    protected fun rightRotate(y: Node): Node {
        val x = y.left!!
        y.left = x.right
        x.right = y
        update(y)
        update(x)
        return x
    }

    protected fun leftRotate(x: Node): Node {
        val y = x.right!!
        x.right = y.left
        y.left = x
        update(x)
        update(y)
        return y
    }

    protected fun balance(node: Node): Node {
        update(node)
        val balanceFactor = height(node.left) - height(node.right)
        if (balanceFactor > 1) {
            if (height(node.left?.left) < height(node.left?.right)) {
                node.left = node.left?.let { leftRotate(it) }
            }
            return rightRotate(node)
        }
        if (balanceFactor < -1) {
            if (height(node.right?.right) < height(node.right?.left)) {
                node.right = node.right?.let { rightRotate(it) }
            }
            return leftRotate(node)
        }
        return node
    }

    /**
     * Inserts a new node with the given [value] at in–order position [pos] (0–indexed).
     */
    protected fun insert(root: Node?, pos: Int, value: Int): Node {
        if (root == null) return Node(value)
        val leftSize = size(root.left)
        if (pos <= leftSize) {
            root.left = insert(root.left, pos, value)
        } else {
            root.right = insert(root.right, pos - leftSize - 1, value)
        }
        return balance(root)
    }

    /**
     * Returns the node at the given in–order position [pos].
     */
    protected fun getNode(root: Node?, pos: Int): Node? {
        if (root == null) return null
        val leftSize = size(root.left)
        return when {
            pos < leftSize -> getNode(root.left, pos)
            pos == leftSize -> root
            else -> getNode(root.right, pos - leftSize - 1)
        }
    }

    fun getRowLength(pos: Int): Int {
        return getNode(root, pos)?.rowLength ?: 0
    }

    /**
     * Updates the node at [pos] to have [newValue] as its row length.
     */
    protected fun updateValue(root: Node?, pos: Int, newValue: Int): Node? {
        if (root == null) return null
        val leftSize = size(root.left)
        when {
            pos < leftSize -> root.left = updateValue(root.left, pos, newValue)
            pos > leftSize -> root.right = updateValue(root.right, pos - leftSize - 1, newValue)
            else -> root.rowLength = newValue
        }
        return balance(root)
    }

    /**
     * Removes the node at in–order position [pos] from the subtree rooted at [root].
     */
    protected fun remove(root: Node?, pos: Int): Node? {
        if (root == null) return null
        val leftSize = size(root.left)
        when {
            pos < leftSize -> root.left = remove(root.left, pos)
            pos > leftSize -> root.right = remove(root.right, pos - leftSize - 1)
            else -> {
                if (root.left == null) return root.right
                if (root.right == null) return root.left
                var minNode = root.right
                while (minNode?.left != null) {
                    minNode = minNode.left
                }
                if (minNode != null) {
                    root.rowLength = minNode.rowLength
                    root.right = remove(root.right, 0)
                }
            }
        }

        return balance(root)
    }

    /**
     * Returns the sum of the row lengths for the first [count] nodes.
     */
    protected fun prefixSum(root: Node?, count: Int): Long {
        if (root == null || count <= 0) return 0L
        val leftSize = size(root.left)
        return when {
            count <= leftSize -> prefixSum(root.left, count)
            count == leftSize + 1 -> sum(root.left) + root.rowLength.toLong()
            else -> sum(root.left) + root.rowLength.toLong() +
                    prefixSum(root.right, count - leftSize - 1)
        }
    }

    /**
     * Given a serial offset, returns the corresponding (row, col) position.
     */
    protected fun findBySerialHelper(node: Node?, baseIndex: Int, cumulative: Long, serial: Long): Pair<Int, Int>? {
        if (node == null) return null
        val leftSum = sum(node.left)
        val leftSize = size(node.left)
        if (cumulative + leftSum > serial) {
            return findBySerialHelper(node.left, baseIndex, cumulative, serial)
        }
        val currentIndex = baseIndex + leftSize
        val currentStart = cumulative + leftSum
        if (currentStart + node.rowLength > serial) {
            val col = (serial - currentStart).toInt()
            return Pair(currentIndex, col)
        }
        return findBySerialHelper(node.right, currentIndex + 1, currentStart + node.rowLength, serial)
    }

    // --- Public API ---

    /**
     * Appends [data] at the end. The [splitFunction] turns the data into row–length increments.
     */
    open fun add(data: T) {
        val rows = splitFunction(data)
        if (rows.isEmpty()) return
        // Update the last row.
        val lastIndex = rowSize() - 1
        val lastNode = getNode(root, lastIndex)
            ?: throw IllegalStateException("Tree is unexpectedly empty.")
        root = updateValue(root, lastIndex, lastNode.rowLength + rows[0])
        // Append any additional rows.
        for (i in 1 until rows.size) {
            root = insert(root, rowSize(), rows[i])
        }
    }

    /**
     * Inserts [data] at the given [row] and [col]. If the data spans multiple rows,
     * the current row is split and new rows are inserted.
     */
    open fun insert(row: Int, col: Int, data: T) {
        if (row !in 0 until rowSize())
            throw IllegalArgumentException("Row index out of bounds: $row")
        val node = getNode(root, row) ?: throw IllegalStateException("Row not found.")
        if (col !in 0..node.rowLength)
            throw IllegalArgumentException("Column index out of bounds: $col")
        val rows = splitFunction(data)
        if (rows.isEmpty()) return
        if (rows.size == 1) {
            root = updateValue(root, row, node.rowLength + rows[0])
        } else {
            val head = col + rows[0]
            val tail = (node.rowLength - col) + rows[rows.size - 1]
            root = updateValue(root, row, head)
            for (i in 1 until rows.size - 1) {
                root = insert(root, row + i, rows[i])
            }
            root = insert(root, row + rows.size - 1, tail)
        }
    }

    /**
     * Deletes [len] bytes starting at [row],[col]. If the deletion spans rows,
     * affected rows are merged or removed.
     */
    open fun delete(row: Int, col: Int, len: Int) {
        if (row !in 0 until rowSize())
            throw IllegalArgumentException("Row index out of bounds: $row")
        val node = getNode(root, row) ?: throw IllegalStateException("Row not found.")
        if (col !in 0..node.rowLength)
            throw IllegalArgumentException("Column index out of bounds: $col")
        if (len < 0)
            throw IllegalArgumentException("Length to delete must be non-negative")
        if (len == 0) return

        // If deletion is contained within a single row, update that row.
        if ((node.rowLength - col) > len) {
            root = updateValue(root, row, node.rowLength - len)
        } else {
            // Multi-line deletion: we want to merge the tail of the deletion region into the first row.
            var remain = len - (node.rowLength - col)
            // Set current row to only its first part.
            root = updateValue(root, row, col)
            // Process subsequent rows. (Use a do–while to mimic the original “do…while (remain >= 0)” loop.)
            do {
                if (row + 1 >= rowSize()) break  // No more rows.
                val nextNode = getNode(root, row + 1) ?: break
                remain -= nextNode.rowLength
                if (remain < 0) {
                    // Partial deletion on this row:
                    // We merge the remaining part (i.e. the tail) into the current row.
                    // (-remain) is the number of bytes remaining in the partially consumed row.
                    root = updateValue(root, row, getNode(root, row)!!.rowLength - remain)
                    // Remove the partially affected row.
                    root = remove(root, row + 1)
                    break
                } else {
                    // The entire next row is deleted; remove it.
                    root = remove(root, row + 1)
                }
            } while (remain >= 0)
        }
    }


    /**
     * Returns the cumulative offset (sum of row lengths) up to the given [row].
     */
    open fun get(row: Int): Long {
        if (row !in 0 until rowSize())
            throw IllegalArgumentException("Row index out of bounds: $row")
        return prefixSum(root, row)
    }

    /**
     * Returns the absolute serial offset corresponding to [row],[col].
     */
    open fun serial(row: Int, col: Int): Long {
        val base = get(row)
        val node = getNode(root, row) ?: throw IllegalStateException("Row not found.")
        if (col !in 0..node.rowLength)
            throw IllegalArgumentException("Column index out of bounds: $col")
        return base + col
    }

    /**
     * Returns the (row, col) corresponding to the given serial offset.
     */
    open fun pos(serial: Long): Pair<Int, Int> {
        if (serial < 0)
            throw IllegalArgumentException("Serial position must be non-negative: $serial")
        return findBySerialHelper(root, 0, 0L, serial)
            ?: Pair(rowSize() - 1, getLastRowLength())
    }

    /**
     * Returns the total number of rows.
     */
    open fun rowSize(): Int = size(root)

    /**
     * Returns an array of the current row lengths.
     */
    open fun rowLengths(): IntArray {
        val list = mutableListOf<Int>()
        fun inorder(node: Node?) {
            if (node == null) return
            inorder(node.left)
            list.add(node.rowLength)
            inorder(node.right)
        }
        inorder(root)
        return list.toIntArray()
    }

    /**
     * Returns a deep copy (snapshot) of this index.
     */
    open fun snapshot(): Self {
        val newIndex = createInstance(newlineToken, splitFunction)
        newIndex.root = deepCopy(root)
        return newIndex
    }

    // --- Abstract factory method for snapshot ---
    protected abstract fun createInstance(
        newlineToken: T,
        splitFunction: (T) -> IntArray
    ): Self

    // --- Helpers for snapshot ---
    private fun deepCopy(node: Node?): Node? {
        if (node == null) return null
        val newNode = Node(node.rowLength)
        newNode.left = deepCopy(node.left)
        newNode.right = deepCopy(node.right)
        update(newNode)
        return newNode
    }

    private fun getLastRowLength(): Int {
        val node = getNode(root, rowSize() - 1) ?: return 0
        return node.rowLength
    }
}


