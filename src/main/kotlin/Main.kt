package org.editor

import org.editor.presentation.components.mainframe.MainFrame
import java.awt.EventQueue

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    EventQueue.invokeLater {
        MainFrame().isVisible = true
    }
}
