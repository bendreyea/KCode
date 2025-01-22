package org.editor.syntax

data class ASTNode(
    val type: String,
    val value: String? = null,
    val children: MutableList<ASTNode> = mutableListOf()
)

class Parser(private val tokens: List<String>) {
    private var currentIndex = 0

    // Main entry point for the parser
    fun parse(): ASTNode {
        val rootNode = ASTNode("Program")
        while (!isAtEnd()) {
            rootNode.children.add(parseFunctionDeclaration())
        }
        return rootNode
    }

    private fun parseFunctionDeclaration(): ASTNode {
        expect("fun") // Consume 'fun'
        val functionName = consume() // Get function name
        expect("(") // Consume '('
        val argsNode = parseArguments()
        expect(")") // Consume ')'
        expect(":") // Consume ':'
        val returnType = consume() // Get return type
        expect("{") // Consume '{'
        val bodyNode = parseBody()
        expect("}") // Consume '}'

        return ASTNode("FunctionDeclaration", functionName, mutableListOf(argsNode, ASTNode("ReturnType", returnType), bodyNode))
    }

    private fun parseArguments(): ASTNode {
        val argsNode = ASTNode("Arguments")
        while (peek() != ")") {
            val argName = consume() // Argument name
            expect(":") // Consume ':'
            val argType = consume() // Argument type
            argsNode.children.add(ASTNode("Argument", "$argName: $argType"))
            if (peek() == ",") consume() // Consume ',' if present
        }
        return argsNode
    }

    private fun parseBody(): ASTNode {
        val bodyNode = ASTNode("Body")
        while (peek() != "}") {
            bodyNode.children.add(parseExpression())
        }
        return bodyNode
    }

    private fun parseExpression(): ASTNode {
        val left = consume() // Left operand
        val operator = consume() // Operator (e.g., +, -, *, /)
        val right = consume() // Right operand
        return ASTNode("Expression", "$left $operator $right")
    }

    // Utility functions for token handling
    private fun expect(expected: String) {
        if (consume() != expected) throw IllegalArgumentException("Expected $expected but found ${peek()}")
    }

    private fun consume(): String {
        if (isAtEnd()) throw IllegalArgumentException("Unexpected end of input")
        return tokens[currentIndex++]
    }

    private fun peek(): String = if (isAtEnd()) "" else tokens[currentIndex]

    private fun isAtEnd(): Boolean = currentIndex >= tokens.size
}


