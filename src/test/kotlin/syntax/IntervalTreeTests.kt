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

    data class TestInterval(override val start: Int, override val end: Int) : Comparable<Interval>, Interval {
        fun compareTo(other: TestInterval): Int {
            return compareValuesBy(this, other, { it.start }, { it.end })
        }
    }

    @Test
    fun `queryOverlapping - single exact match`() {
        val tree = IntervalTree<TestInterval>()
        val interval = TestInterval(5, 10)
        tree.insert(interval)

        val result = tree.queryOverlapping(5, 10)
        assertEquals(listOf(interval), result)
    }

    @Test
    fun `queryOverlapping - multiple overlapping intervals`() {
        val tree = IntervalTree<TestInterval>()
        val interval1 = TestInterval(1, 5)
        val interval2 = TestInterval(4, 10)
        val interval3 = TestInterval(6, 15)
        tree.insert(interval1)
        tree.insert(interval2)
        tree.insert(interval3)

        val result = tree.queryOverlapping(3, 7)
        assertTrue(result.containsAll(listOf(interval1, interval2, interval3)))
    }

    @Test
    fun `queryOverlapping - no overlapping intervals`() {
        val tree = IntervalTree<TestInterval>()
        tree.insert(TestInterval(1, 5))
        tree.insert(TestInterval(10, 15))

        val result = tree.queryOverlapping(6, 9)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `queryOverlapping - exact boundary overlap`() {
        val tree = IntervalTree<TestInterval>()
        val interval = TestInterval(5, 10)
        tree.insert(interval)

        val result = tree.queryOverlapping(11, 15)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `queryOverlapping - overlapping at end boundary`() {
        val tree = IntervalTree<TestInterval>()
        val interval = TestInterval(5, 10)
        tree.insert(interval)

        val result = tree.queryOverlapping(8, 12)
        assertEquals(listOf(interval), result)
    }

    @Test
    fun `queryOverlapping - query range fully encloses an interval`() {
        val tree = IntervalTree<TestInterval>()
        val interval = TestInterval(5, 10)
        tree.insert(interval)

        val result = tree.queryOverlapping(0, 15)
        assertEquals(listOf(interval), result)
    }
}