package com.github.mrdolch.plantarchintellijplugin.app

import com.github.mrdolch.plantarchintellijplugin.configuration.Configuration
import com.github.mrdolch.plantarchintellijplugin.configuration.PersistConfigurationService
import com.github.mrdolch.plantarchintellijplugin.diagram.utils.ProjectClassChooser
import com.github.mrdolch.plantarchintellijplugin.diagram.view.DiagramEditor.Companion.INITIAL_PUML
import com.github.mrdolch.plantarchintellijplugin.diagram.view.OptionPanelState
import com.github.mrdolch.plantarchintellijplugin.diagram.view.OptionPanelState.Companion.MARKER_ENDCONFIG
import com.github.mrdolch.plantarchintellijplugin.diagram.view.OptionPanelState.Companion.MARKER_STARTCONFIG
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiDocumentManager
import java.nio.file.Paths

class ActionStartNewDiagram : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      val className =
          tryGetClassName(project) ?: ProjectClassChooser.openProjectClassDialog(project)
      if (className == null) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("PlantArch")
            .createNotification("Cannot start a new Diagram", NotificationType.WARNING)
            .notify(project)
        return
      }

      val configuration = getConfiguration(project)
      val options =
          OptionPanelState.createDefaultOptionPanelState(project, className, configuration)

      writeDiagramAndOpenEditor(
          project,
          "$INITIAL_PUML\n$MARKER_STARTCONFIG\n${options.toYaml()}$MARKER_ENDCONFIG\n",
          options.targetPumlFile,
      )
    }
  }
}

private fun tryGetClassName(project: Project): String? {
  val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
  val psiFile =
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? PsiClassOwner
          ?: return null
  return psiFile.classes.firstOrNull()?.qualifiedName
}

private fun writeDiagramAndOpenEditor(
    project: Project,
    plantuml: String,
    diagramFilename: String,
) {
  val path = Paths.get(diagramFilename)
  val parentPath = path.parent?.toString() ?: return
  val fileName = path.fileName.toString()

  WriteCommandAction.runWriteCommandAction(
      project,
      "Create UML Diagram",
      null,
      Runnable {
        val parentDir =
            VfsUtil.createDirectoryIfMissing(parentPath)
                ?: error("Cannot create/find directory: $parentPath")

        val file = parentDir.createChildData(project, fileName)

        VfsUtil.saveText(file, plantuml)
        FileEditorManager.getInstance(project).openFile(file, true)
      },
  )
}

private fun getConfiguration(project: Project): Configuration =
    project.getService(PersistConfigurationService::class.java).state
