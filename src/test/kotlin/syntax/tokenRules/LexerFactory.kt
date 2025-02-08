package syntax.tokenRules

import org.editor.syntax.Lexer

fun createLexer(input: String): Lexer = Lexer(input)