package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.charleskorn.kaml.Yaml
import com.github.mrdolch.plantarchintellijplugin.app.EditorRegistry
import com.github.mrdolch.plantarchintellijplugin.diagram.ExecPlantArch
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import tech.dolch.plantarch.cmd.IdeaRenderJob
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

class DiagramEditor(private val diagramFile: VirtualFile) : UserDataHolderBase(), FileEditor {

  private val panel = JPanel(BorderLayout())
  private var jobParams: IdeaRenderJob
  private val umlOptionsPanel: UmlOptionsPanel
  private val classTreePanel: ClassTreePanel

  init {
    EditorRegistry.registerEditor(diagramFile, this)
    val diagramContent = String(diagramFile.contentsToByteArray())
    jobParams = getJobParams(diagramContent)

    umlOptionsPanel = UmlOptionsPanel(jobParams) { updateDiagram() }
    classTreePanel = ClassTreePanel(jobParams) { updateDiagram() }
    panel.add(umlOptionsPanel, BorderLayout.NORTH)
    panel.add(classTreePanel, BorderLayout.CENTER)
  }

  private fun getJobParams(diagramContent: String): IdeaRenderJob {
    val content = diagramContent.lines()
    val configYaml = content.subList(content.indexOf(MARKER_STARTCONFIG) + 1, content.indexOf(MARKER_ENDCONFIG))
    return Yaml.default.decodeFromString(IdeaRenderJob.serializer(), configYaml.joinToString("\n"))
  }

  fun updateDiagram() {
    jobParams.renderJob.classDiagrams.let {
      it.classesToAnalyze = classTreePanel.getClassesToAnalyze()
      it.containersToHide = classTreePanel.getContainersToHide()
      it.classesToHide = classTreePanel.getClassesToHide()
      it.showUseByMethodNames = umlOptionsPanel.getShowUseByMethodNames()
      it.title = umlOptionsPanel.getTitle()
      it.description = umlOptionsPanel.getDescription()
    }
    jobParams.optionPanelState.showPackages = umlOptionsPanel.getShowPackages()

    ExecPlantArch.runAnalyzerBackgroundTask(jobParams, false)
  }

  fun updateFields(diagramContent: String) {
    jobParams = getJobParams(diagramContent)
    umlOptionsPanel.updateFields(jobParams)
    classTreePanel.updatePanel(jobParams)
  }

  fun toggleEntryFromDiagram(selectedText: String) {
    classTreePanel.toggleEntryFromDiagram(selectedText)
  }

  override fun getComponent(): JComponent = panel
  override fun getPreferredFocusedComponent(): JComponent? = null
  override fun getName(): String = "PlantArch Editor"
  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true
  override fun addPropertyChangeListener(p0: PropertyChangeListener) {}
  override fun removePropertyChangeListener(p0: PropertyChangeListener) {}
  override fun dispose() {}
  override fun getFile(): VirtualFile = diagramFile
}

