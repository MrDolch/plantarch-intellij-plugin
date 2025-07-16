package com.github.mrdolch.plantarchintellijplugin.app

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class StartupListener : ProjectActivity {
  override suspend fun execute(project: Project) {
    ApplicationManager.getApplication().invokeLater {
      FileEditorManager.getInstance(project)
        .selectedEditor?.file?.let {
          project.getUserData(PANEL_KEY)?.createOptionsFromFile(it, project)
        }
    }
  }
}