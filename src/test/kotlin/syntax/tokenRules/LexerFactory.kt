package syntax.tokenRules

import org.editor.syntax.lexer.Lexer

fun createLexer(input: String): Lexer = Lexer(input)