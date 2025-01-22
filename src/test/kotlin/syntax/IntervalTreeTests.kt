package syntax

import org.editor.syntax.intervalTree.Interval
import org.editor.syntax.intervalTree.IntervalTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntervalTreeTests {
    @Test
    fun isEmpty_returnsTrueForEmptyTree() {
        val tree = IntervalTree<Interval>()
        assertTrue(tree.isEmpty())
    }

    @Test
    fun isEmpty_returnsFalseForNonEmptyTree() {
        val interval = object : Interval {
            override val start = 0
            override val end = 1
        }
        val tree = IntervalTree(interval)
        assertFalse(tree.isEmpty())
    }

    @Test
    fun size_returnsZeroForEmptyTree() {
        val tree = IntervalTree<Interval>()
        assertEquals(0, tree.size())
    }

    @Test
    fun size_returnsCorrectSizeForNonEmptyTree() {
        val interval = object : Interval {
            override val start = 0
            override val end = 1
        }
        val tree = IntervalTree(interval)
        assertEquals(1, tree.size())
    }

    @Test
    fun contains_returnsTrueForExistingInterval() {
        val interval = object : Interval {
            override val start = 0
            override val end = 1
        }
        val tree = IntervalTree(interval)
        assertTrue(tree.contains(interval))
    }

    @Test
    fun insert_addsIntervalToTree() {
        val interval = object : Interval {
            override val start = 0
            override val end = 1
        }
        val tree = IntervalTree<Interval>()
        assertTrue(tree.insert(interval))
        assertTrue(tree.contains(interval))
    }

    @Test
    fun insert_doesNotAddDuplicateInterval() {
        val interval = object : Interval {
            override val start = 0
            override val end = 1
        }
        val tree = IntervalTree(interval)
        assertFalse(tree.insert(interval))
    }

    @Test
    fun delete_removesIntervalFromTree() {
        val interval = object : Interval {
            override val start = 0
            override val end = 1
        }
        val tree = IntervalTree(interval)
        assertTrue(tree.delete(interval))
        assertFalse(tree.contains(interval))
    }

    @Test
    fun iterator_traversesTreeInOrder() {
        val interval1 = object : Interval {
            override val start = 0
            override val end = 1
        }
        val interval2 = object : Interval {
            override val start = 1
            override val end = 2
        }
        val tree = IntervalTree<Interval>()
        tree.insert(interval1)
        tree.insert(interval2)
        val iterator = tree.iterator()
        assertTrue(iterator.hasNext())
        assertEquals(interval1, iterator.next())
        assertTrue(iterator.hasNext())
        assertEquals(interval2, iterator.next())
        assertFalse(iterator.hasNext())
    }
}