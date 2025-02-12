package org.editor.presentation.components.textpane

import org.editor.syntax.lexer.TokenType
import java.awt.Color
import java.awt.Font

data class EditorTheme(
    val backgroundColor: Color = Color(30, 30, 30),
    val canvasBackgroundColor: Color = Color.WHITE,
    val gutterColor: Color = Color.WHITE,
    val gutterTextColor: Color = Color.BLACK,
    val textColor: Color = Color.BLACK,
    val selectionColor: Color = Color(173, 216, 230, 128),
    val caretColor: Color = Color.red,
    val tokenColors: Map<TokenType, Color> = mapOf(
        TokenType.KEYWORD    to Color(200, 123, 255),
        TokenType.IDENTIFIER to Color.BLACK,
        TokenType.NUMBER     to Color(0, 0, 255),
        TokenType.STRING     to Color(42, 150, 65),
        TokenType.COMMENT    to Color(128, 128, 128),
        TokenType.SYMBOL     to Color(0, 0, 0),
        TokenType.WHITESPACE to Color(0, 0, 0),
        TokenType.UNKNOWN    to Color(0, 0, 0),
        TokenType.BRACKET_LEVEL_0 to Color.CYAN,
        TokenType.BRACKET_LEVEL_1 to Color.MAGENTA,
        TokenType.BRACKET_LEVEL_2 to Color.ORANGE,
    ),
    val gutterWidth: Int = 60,
    val horizontalPadding: Int = 10,
    val font: Font = Font("Monospaced", Font.PLAIN, 12)
)
