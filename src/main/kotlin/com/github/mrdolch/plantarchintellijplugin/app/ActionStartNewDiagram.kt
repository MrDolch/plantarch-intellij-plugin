package com.github.mrdolch.plantarchintellijplugin.app

import com.github.mrdolch.plantarchintellijplugin.configuration.Configuration
import com.github.mrdolch.plantarchintellijplugin.configuration.PersistConfigurationService
import com.github.mrdolch.plantarchintellijplugin.diagram.view.DiagramEditor.Companion.INITIAL_PUML
import com.github.mrdolch.plantarchintellijplugin.diagram.view.OptionPanelState
import com.github.mrdolch.plantarchintellijplugin.diagram.view.OptionPanelState.Companion.MARKER_ENDCONFIG
import com.github.mrdolch.plantarchintellijplugin.diagram.view.OptionPanelState.Companion.MARKER_STARTCONFIG
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiDocumentManager

class ActionStartNewDiagram : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
      val psiFile =
          PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? PsiClassOwner
              ?: return
      val className = psiFile.classes.firstOrNull()?.qualifiedName ?: return
      val module = ModuleUtil.findModuleForPsiElement(psiFile) ?: return

      val configuration = getConfiguration(project)
      val options = OptionPanelState.createDefaultOptionPanelState(module, className, configuration)

      writeDiagramAndOpenEditor(
          project,
          "$INITIAL_PUML\n$MARKER_STARTCONFIG\n${options.toYaml()}$MARKER_ENDCONFIG\n",
          options.targetPumlFile,
      )
    }
  }

  private fun writeDiagramAndOpenEditor(
      project: Project,
      plantuml: String,
      diagramFilename: String,
  ) {
    val application = ApplicationManager.getApplication()
    val virtualFileManager = VirtualFileManager.getInstance()
    val fileEditorManager = FileEditorManager.getInstance(project)
    application.invokeLater {
      virtualFileManager.refreshAndFindFileByUrl("file://$diagramFilename")?.let {
        application.runWriteAction { VfsUtil.saveText(it, plantuml) }
        fileEditorManager.openFile(it, true, true)
      }
    }
  }

  private fun getConfiguration(project: Project): Configuration =
    project.getService(PersistConfigurationService::class.java).state

}
