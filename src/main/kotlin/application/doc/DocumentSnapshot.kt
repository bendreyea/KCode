package org.editor.application.doc

import org.editor.application.common.LineSeparator
import org.editor.application.RowIndex
import org.editor.core.TextBuffer
import java.nio.charset.Charset

class DocumentSnapshot(pt: TextBuffer,
                       charset: Charset,
                       bom: ByteArray,
                       lineSeparator: LineSeparator,
                       index: RowIndex
) : BaseDocument(pt, charset, bom, lineSeparator, index) {

    companion object {
        fun create(
            pt: TextBuffer,
            index: RowIndex,
            charset: Charset = Charset.forName("UTF-8"),
            bom: ByteArray = ByteArray(0),
            lineSeparator: LineSeparator = LineSeparator.platform
        ): DocumentSnapshot {
            return DocumentSnapshot(pt, charset, bom, lineSeparator, index)
        }
    }
}


fun interface DocumentChangeListener {
    fun onDocumentChange(event: DocumentSnapshot)
}