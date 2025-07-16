package com.github.mrdolch.plantarchintellijplugin.app

import com.github.mrdolch.plantarchintellijplugin.panel.PanelDiagramOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class PlantArchToolWindowFactory : ToolWindowFactory {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val panel = PanelDiagramOptions()
    val content = ContentFactory.getInstance().createContent(panel, "", false)
    toolWindow.contentManager.addContent(content)
    project.putUserData(PANEL_KEY, panel)
    val connection = project.messageBus.connect()
    connection.subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      DiagramFileListener(panel)
    )

    toolWindow.activate {
      if (panel.classesTable.items.isEmpty()) {
        ApplicationManager.getApplication().invokeLater {
          FileEditorManager.getInstance(project)
            .selectedEditor?.file?.let {
              project.getUserData(PANEL_KEY)?.createOptionsFromFile(it, project)
            }
        }
      }
    }
  }
}

val PANEL_KEY = Key.create<PanelDiagramOptions>("plantarch.panel")

