package com.github.mrdolch.plantarchintellijplugin.toolWindow

import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import tech.dolch.plantarch.cmd.IdeaRenderJob

class DiagramFileListener(
    private val optionsPanel: PanelDiagramOptions
) : FileEditorManagerListener {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val newFile: VirtualFile? = event.newFile

        if (newFile != null && isDiagramFile(newFile)) {
            updateOptionsFromFile(newFile)
        }
    }

    private fun isDiagramFile(file: VirtualFile): Boolean =
        file.name.startsWith("class-diagram") && file.extension == "puml"

    private fun updateOptionsFromFile(file: VirtualFile) {
        // Beispiel: Lese Inhalt oder Metadaten und setze Felder im Panel
        val content = String(file.contentsToByteArray())
        content.lines().getOrNull(1)?.drop(1)?.let { json ->
            val jobParams = Json.decodeFromString<IdeaRenderJob>(json)
            optionsPanel.updatePanel(jobParams)
            registerSelectionListener(getProjectByName(jobParams.projectName), optionsPanel)
        }
    }

    fun registerSelectionListener(project: Project, optionsPanel: PanelDiagramOptions) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val selectionModel = editor.selectionModel

        selectionModel.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                val selectedText = e.newRange?.let { range ->
                    editor.document.getText(range)
                }
                if (selectedText != null) optionsPanel.toggleListEntry(selectedText)
            }
        })
    }
}
