package com.github.mrdolch.plantarchintellijplugin.toolWindow

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
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
            isJavaFile(newFile) -> createOptionsFromFile(newFile, event.manager.project)
        }
    }

    private fun isJavaFile(file: VirtualFile): Boolean =
        file.fileType == JavaFileType.INSTANCE

    private fun isDiagramFile(file: VirtualFile): Boolean =
        file.name.startsWith(FILE_PREFIX_DEPENDENCY_DIAGRAM) && file.extension == "puml"

    private fun readOptionsFromDiagramFile(file: VirtualFile) {
        // Beispiel: Lese Inhalt oder Metadaten und setze Felder im Panel
        val content = String(file.contentsToByteArray())
        content.lines().getOrNull(1)?.drop(1)?.let { json ->
            val jobParams = Json.decodeFromString<IdeaRenderJob>(json)
            optionsPanel.updatePanel(jobParams)
            registerSelectionListener(getProjectByName(jobParams.projectName), optionsPanel)
        }
    }
    private fun createOptionsFromFile(file: VirtualFile, project: Project) {
        val psiClassOwner = file.findPsiFile(project) as? com.intellij.psi.PsiClassOwner
        val className = psiClassOwner?.classes?.firstOrNull()?.qualifiedName
        if(className != null) {
            val module = ModuleUtil.findModuleForPsiElement(psiClassOwner)
            val ideaRenderJob = createIdeaRenderJob(project, module!!, className)
            optionsPanel.updatePanel(ideaRenderJob)
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
