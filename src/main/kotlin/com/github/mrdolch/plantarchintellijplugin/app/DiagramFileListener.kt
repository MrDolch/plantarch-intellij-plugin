package com.github.mrdolch.plantarchintellijplugin.app

import com.github.mrdolch.plantarchintellijplugin.diagram.getProjectByName
import com.github.mrdolch.plantarchintellijplugin.panel.PanelDiagramOptions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiClassOwner
import kotlinx.serialization.json.Json
import tech.dolch.plantarch.cmd.IdeaRenderJob

const val FILE_PREFIX_DEPENDENCY_DIAGRAM = "dependency-diagram-"


class DiagramFileListener(
  private val optionsPanel: PanelDiagramOptions
) : FileEditorManagerListener {

  override fun selectionChanged(event: FileEditorManagerEvent) {
    val newFile: VirtualFile? = event.newFile
    when {
      newFile == null -> {}
      isDiagramFile(newFile) -> readOptionsFromDiagramFile(newFile)
      isJavaFile(newFile, event.manager.project) -> optionsPanel.createOptionsFromFile(newFile, event.manager.project)
    }
  }

  private fun isJavaFile(file: VirtualFile, project: Project): Boolean =
    file.findPsiFile(project) is PsiClassOwner

  private fun isDiagramFile(file: VirtualFile): Boolean =
    file.name.startsWith(FILE_PREFIX_DEPENDENCY_DIAGRAM) && file.extension == "puml"

  private fun readOptionsFromDiagramFile(file: VirtualFile) {
    // Beispiel: Lese Inhalt oder Metadaten und setze Felder im Panel
    val content = String(file.contentsToByteArray())
    content.lines().getOrNull(1)?.drop(1)?.let { json ->
      val jobParams = Json.decodeFromString<IdeaRenderJob>(json)
      optionsPanel.updatePanel(jobParams)
      registerSelectionListenerOnPlantUmlView(getProjectByName(jobParams.projectName), optionsPanel)
    }
  }


  private fun registerSelectionListenerOnPlantUmlView(project: Project, optionsPanel: PanelDiagramOptions) {
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
    optionsPanel: PanelDiagramOptions
  ) {
    val selectedText = e.newRange?.let { range ->
      editor.document.getText(range)
    }
    if (selectedText != null) optionsPanel.toggleEntryFromDiagram(selectedText)
  }
}

