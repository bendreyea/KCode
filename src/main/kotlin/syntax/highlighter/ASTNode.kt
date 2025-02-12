package org.editor.syntax.highlighter

import org.editor.syntax.lexer.Token
import org.editor.syntax.intervalTree.Interval

sealed interface ASTNode : Interval {
    val children: List<ASTNode>
}

data class BlockNode(
    override val start: Int,
    override val end: Int,
    override val children: List<ASTNode>,
    val bracketType: Char?,  // Type of bracket that started this block
    val nestingLevel: Int    // Depth level of the block
) : ASTNode

data class TokenNode(
    val token: Token,
    override val start: Int,
    override val end: Int
) : ASTNode {
    override val children: List<ASTNode> = emptyList()
}

fun ASTNode?.mergeWith(new: ASTNode): ASTNode {
    if (this == null) return new

    if (this is BlockNode && new is BlockNode &&
        this.bracketType == null && new.bracketType == null &&
        this.nestingLevel == 0 && new.nestingLevel == 0
    ) {
        val mergedChildren = this.children + new.children
        return BlockNode(
            start = this.start,
            end = new.end,
            children = mergedChildren,
            bracketType = null,
            nestingLevel = 0
        )
    }

    if (this::class == new::class && this.start == new.start && this.end == new.end) {
        if (this is BlockNode && new is BlockNode &&
            this.children.size == new.children.size
        ) {
            val mergedChildren = new.children.indices.map { i ->
                this.children[i].mergeWith(new.children[i])
            }
            return new.copy(children = mergedChildren)
        } else if (this is TokenNode && new is TokenNode && this.token == new.token) {
            return this
        }
    }

    val start = minOf(this.start, new.start)
    val end = maxOf(this.end, new.end)
    return BlockNode(start, end, listOf(this, new), bracketType = null, nestingLevel = 0)
}

fun ASTNode.print(indent: String = "", isLast: Boolean = true) {
    val connector = if (isLast) "└── " else "├── "
    val newIndent = indent + if (isLast) "    " else "│   "

    when (this) {
        is BlockNode -> {
            val blockType = this.bracketType?.let { " (Bracket: $it)" } ?: ""
            println("$indent$connector BlockNode$blockType (Level: ${this.nestingLevel})")

            this.children.forEachIndexed { index, child ->
                child.print(newIndent, index == this.children.lastIndex)
            }
        }
        is TokenNode -> {
            println("$indent$connector TokenNode (${this.token.type}) [${this.start}, ${this.end}]")
        }
    }
}
