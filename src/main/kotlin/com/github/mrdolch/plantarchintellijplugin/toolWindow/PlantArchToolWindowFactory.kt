package com.github.mrdolch.plantarchintellijplugin.toolWindow

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
    }
}

val PANEL_KEY = Key.create<PanelDiagramOptions>("plantarch.panel")

