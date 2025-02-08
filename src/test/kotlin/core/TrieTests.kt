package core

import org.editor.syntax.Trie
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrieTests {

    @Test
    fun of_withValidWordSequence_createsTrieWithWords() {
        val trie = Trie.create("apple, app, application")
        assertTrue(trie.match("apple"))
        assertTrue(trie.match("app"))
        assertTrue(trie.match("application"))
    }

    @Test
    fun put_insertsWordCorrectly() {
        val trie = Trie()
        trie.put("hello")
        assertTrue(trie.match("hello"))
    }

    @Test
    fun remove_existingWord_removesWord() {
        val trie = Trie()
        trie.put("hello")
        trie.remove("hello")
        assertFalse(trie.match("hello"))
    }

    @Test
    fun remove_nonExistingWord_doesNothing() {
        val trie = Trie()
        trie.put("hello")
        trie.remove("world")
        assertTrue(trie.match("hello"))
    }

    @Test
    fun match_existingWord_returnsTrue() {
        val trie = Trie()
        trie.put("hello")
        assertTrue(trie.match("hello"))
    }

    @Test
    fun match_nonExistingWord_returnsFalse() {
        val trie = Trie()
        trie.put("hello")
        assertFalse(trie.match("world"))
    }

    @Test
    fun startsWith_existingPrefix_returnsTrue() {
        val trie = Trie()
        trie.put("hello")
        assertTrue(trie.startsWith("he"))
    }

    @Test
    fun startsWith_nonExistingPrefix_returnsFalse() {
        val trie = Trie()
        trie.put("hello")
        assertFalse(trie.startsWith("wo"))
    }

    @Test
    fun suggestion_withNonExistingPrefix_returnsEmptyList() {
        val trie = Trie.create("apple, app, application")
        val suggestions = trie.suggestion("xyz")
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun remove_withEmptyString_doesNotAlterTrie() {
        val trie = Trie()
        trie.put("hello")
        trie.remove("")
        assertTrue(trie.match("hello"))
    }

    @Test
    fun match_withEmptyString_returnsFalse() {
        val trie = Trie()
        trie.put("hello")
        assertFalse(trie.match(""))
    }

    @Test
    fun startsWith_withEmptyString_returnsTrue() {
        val trie = Trie()
        trie.put("hello")
        assertTrue(trie.startsWith(""))
    }

    @Test
    fun put_withUnicodeCharacters_insertsCorrectly() {
        val trie = Trie()
        trie.put("こんにちは")
        assertTrue(trie.match("こんにちは"))
    }

    @Test
    fun remove_withUnicodeCharacters_removesCorrectly() {
        val trie = Trie()
        trie.put("こんにちは")
        trie.remove("こんにちは")
        assertFalse(trie.match("こんにちは"))
    }

    @Test
    fun match_withUnicodeCharacters_returnsTrue() {
        val trie = Trie()
        trie.put("こんにちは")
        assertTrue(trie.match("こんにちは"))
    }

    @Test
    fun startsWith_withUnicodeCharacters_returnsTrue() {
        val trie = Trie()
        trie.put("こんにちは")
        assertTrue(trie.startsWith("こん"))
    }
}