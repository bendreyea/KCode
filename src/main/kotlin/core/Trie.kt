package org.editor.core

/**
 * A Trie (prefix tree).
 */
class Trie {

    private val root = TrieNode(null)

    companion object {
        /**
         * Creates a new [Trie] instance and populates it with words from the provided [wordSequence].
         *
         * **Example:**
         * ```
         * val trie = Trie.of("apple, app, application")
         * ```
         *
         * @param wordSequence A comma or whitespace-separated string of words to add to the Trie.
         * @return A new [Trie] instance populated with the specified words.
         */
        fun create(wordSequence: String): Trie {
            val trie = Trie()
            wordSequence.split("[,\\s]+".toRegex()).filter { it.isNotEmpty() }.forEach { trie.put(it) }
            return trie
        }
    }

    /**
     * Inserts the specified [word] into the Trie.
     *
     * @param word The word to insert.
     */
    fun put(word: String) {
        var node = root
        var index = 0
        while (index < word.length) {
            val codePoint = word.codePointAt(index)
            node = node.createIfAbsent(codePoint)
            index += Character.charCount(codePoint)
        }
        node.isEndOfWord = true
    }

    /**
     * Removes the specified [word] from the Trie.
     * If the word does not exist, the method returns without making any changes.
     *
     * @param word The word to remove.
     */
    fun remove(word: String) {
        val node = searchPrefix(word) ?: return
        if (!node.isEndOfWord) return
        node.isEndOfWord = false
        node.removeIfEmpty()
    }

    /**
     * Checks if the Trie contains the exact [word].
     *
     * @param word The word to check.
     * @return `true` if the word exists in the Trie, `false` otherwise.
     */
    fun match(word: String): Boolean {
        val node = searchPrefix(word)
        return node?.isEndOfWord ?: false
    }

    /**
     * Checks if there is any word in the Trie that starts with the given [prefix].
     *
     * @param prefix The prefix to check.
     * @return `true` if there exists any word in the Trie that starts with [prefix], `false` otherwise.
     */
    fun startsWith(prefix: String): Boolean {
        return searchPrefix(prefix) != null
    }

    /**
     * Provides a list of possible next characters (suggestions) based on the current [word].
     *
     * **Example:**
     * ```
     * val trie = Trie.of("apple, app, application")
     * trie.suggestion("app") // Returns ["l", "l", "l"]
     * ```
     *
     * @param word The current word to base suggestions on.
     * @return A list of suggested next characters as strings.
     */
    fun suggestion(word: String): List<String> {
        var node = root
        var index = 0
        while (index < word.length) {
            val codePoint = word.codePointAt(index)
            node = node.get(codePoint) ?: return emptyList()
            index += Character.charCount(codePoint)
        }
        return node.childKeys().map { it.toString() }
    }

    /**
     * Searches for the node corresponding to the given [word].
     *
     * @param word The word to search for.
     * @return The [TrieNode] corresponding to the end of [word], or `null` if [word] is not present.
     */
    private fun searchPrefix(word: String): TrieNode? {
        var node = root
        var index = 0
        while (index < word.length) {
            val codePoint = word.codePointAt(index)
            node = node.get(codePoint) ?: return null
            index += Character.charCount(codePoint)
        }
        return node
    }

    /**
     * Represents a node within the Trie.
     *
     * @property parent The parent [TrieNode]. `null` for the root node.
     */
    private inner class TrieNode(val parent: TrieNode?) {

        private val children: MutableMap<Int, TrieNode> = mutableMapOf()
        var isEndOfWord: Boolean = false

        /**
         * Retrieves the child node corresponding to the given [codePoint].
         * If it does not exist, creates a new [TrieNode].
         *
         * @param codePoint The Unicode code point of the character.
         * @return The existing or newly created child [TrieNode].
         */
        fun createIfAbsent(codePoint: Int): TrieNode {
            return children.getOrPut(codePoint) { TrieNode(this) }
        }

        /**
         * Checks if this node has a child corresponding to the given [codePoint].
         *
         * @param codePoint The Unicode code point of the character.
         * @return `true` if the child exists, `false` otherwise.
         */
        fun contains(codePoint: Int): Boolean {
            return children.containsKey(codePoint)
        }

        /**
         * Retrieves the child node corresponding to the given [codePoint].
         *
         * @param codePoint The Unicode code point of the character.
         * @return The child [TrieNode] if it exists, `null` otherwise.
         */
        fun get(codePoint: Int): TrieNode? {
            return children[codePoint]
        }

        /**
         * Adds or updates the child node for the given [codePoint] with the specified [node].
         *
         * @param codePoint The Unicode code point of the character.
         * @param node The [TrieNode] to associate with [codePoint].
         */
        fun put(codePoint: Int, node: TrieNode) {
            children[codePoint] = node
        }

        /**
         * Removes this node from its parent if it has no children and is not marked as the end of a word.
         * This process continues recursively up the tree.
         */
        fun removeIfEmpty() {
            if (parent == null) return
            if (children.isEmpty() && !isEndOfWord) {
                val key = parent.children.entries.find { it.value === this }?.key
                if (key != null) {
                    parent.children.remove(key)
                    parent.removeIfEmpty()
                }
            }
        }

        /**
         * Retrieves the Unicode code point corresponding to this node by examining its parent.
         *
         * @return The Unicode code point of this node, or `null` if it is the root node.
         */
        private fun key(): Int? {
            return parent?.children?.entries?.find { it.value === this }?.key
        }

        /**
         * Retrieves a list of all child Unicode code points.
         *
         * @return A list of child Unicode code points.
         */
        fun childKeys(): List<Int> {
            return children.keys.toList()
        }
    }
}
