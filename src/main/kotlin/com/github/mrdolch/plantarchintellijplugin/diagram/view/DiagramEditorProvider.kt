package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class DiagramEditorProvider : FileEditorProvider {
  override fun accept(project: Project, file: VirtualFile) =
    file.extension == "puml" && file.name.startsWith("dependency-diagram-")

  override fun createEditor(project: Project, file: VirtualFile): DiagramEditor {
    val diagramEditor = DiagramEditor(file)
    registerSelectionListenerOnPlantUmlView(project, diagramEditor)
    return diagramEditor
  }

  override fun getEditorTypeId(): String = "plantarch-diagram-editor"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR

  private fun registerSelectionListenerOnPlantUmlView(project: Project, optionsPanel: DiagramEditor) {
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    val selectionModel = editor.selectionModel

    selectionModel.addSelectionListener(object : SelectionListener {
      override fun selectionChanged(e: SelectionEvent) {
        handleSelectionChanged(e, editor, optionsPanel)
      }
    })
  }

  private fun handleSelectionChanged(
    e: SelectionEvent,
    editor: Editor,
    optionsPanel: DiagramEditor
  ) {
    val selectedText = e.newRange?.let { editor.document.getText(it) }
    if (selectedText != null) optionsPanel.toggleEntryFromDiagram(selectedText)
  }
}