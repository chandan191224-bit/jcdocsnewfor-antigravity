package com.example.ui

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.example.ui.DocFormatSpan

data class DocEditorSnapshot(
    val title: String,
    val draftContent: String,
    val textFieldValue: TextFieldValue,
    val spans: List<DocFormatSpan>,
    val editorTheme: String,
    val pageMargins: Dp,
    val columnCount: Int,
    val fontSize: TextUnit,
    val isLandscape: Boolean,
    val pageNumberPosition: String?,
    val pageNumberFormat: String,
    val pageNumberStartAt: String
)

class DocUndoRedoManager(private val docId: Int) {
    private val undoStack = mutableListOf<DocEditorSnapshot>()
    private val redoStack = mutableListOf<DocEditorSnapshot>()
    
    var isRestoring = false

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
    
    fun getUndoHistory(): List<DocEditorSnapshot> = undoStack

    fun pushState(state: DocEditorSnapshot) {
        if (isRestoring) return
        
        // Always push state but copy the spans list deeply
        redoStack.clear()
        
        // Prevent pushing exactly the same text/content consecutively to save history if nothing really changed
        if (undoStack.isNotEmpty()) {
            val last = undoStack.last()
            if (last.draftContent == state.draftContent && 
                last.spans == state.spans && 
                // Don't just diff by selection! Because we want to capture formatting changes even if cursor is at the same pos
                // Wait, if ONLY cursor changes, do we want to push to undo? Normally yes, but limit size
                last.title == state.title &&
                last.pageMargins == state.pageMargins &&
                last.columnCount == state.columnCount &&
                last.fontSize == state.fontSize &&
                last.editorTheme == state.editorTheme) {
                return
            }
        }
        
        undoStack.add(state.copy(spans = state.spans.map { it.copy() }))
        if (undoStack.size > 500) {
            undoStack.removeAt(0)
        }
    }

    fun undo(currentState: DocEditorSnapshot): DocEditorSnapshot? {
        if (undoStack.isEmpty()) return null
        
        // Push current state to redo
        redoStack.add(currentState.copy(spans = currentState.spans.map { it.copy() }))
        
        return undoStack.removeLast()
    }

    fun redo(currentState: DocEditorSnapshot): DocEditorSnapshot? {
        if (redoStack.isEmpty()) return null
        
        // Push current state to undo
        undoStack.add(currentState.copy(spans = currentState.spans.map { it.copy() }))
        
        return redoStack.removeLast()
    }
}
