package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.charleskorn.kaml.Yaml
import com.github.mrdolch.plantarchintellijplugin.app.EditorRegistry
import com.github.mrdolch.plantarchintellijplugin.diagram.ExecPlantArch
import com.github.mrdolch.plantarchintellijplugin.diagram.getProjectByName
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.util.concurrency.AppExecutorUtil
import tech.dolch.plantarch.cmd.IdeaRenderJob
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.border.TitledBorder

class DiagramEditor(private val diagramFile: VirtualFile) : UserDataHolderBase(), FileEditor {

  private val panel = JPanel(BorderLayout())
  private var jobParams: IdeaRenderJob
  private val umlOptionsPanel: UmlOptionsPanel
  private val classTreePanel: ClassTreePanel
  private val pngViewerPanel: PngViewerPanel
  private val disposable = Disposer.newDisposable()

  init {
    EditorRegistry.registerEditor(diagramFile, this)
    val diagramContent = String(diagramFile.contentsToByteArray())
    jobParams = getJobParams(diagramContent)
    pngViewerPanel =
        PngViewerPanel(diagramFile.readText(), jobParams.optionPanelState) {
          toggleEntryFromDiagram(it)
        }
    umlOptionsPanel = UmlOptionsPanel(jobParams) { updateDiagram() }
    val project = getProjectByName(jobParams.projectName)
    classTreePanel =
        ClassTreePanel(jobParams) {
          if (umlOptionsPanel.autoRenderDiagram.isSelected) updateDiagram()
        }
    loadDataAsync(jobParams, project, this, classTreePanel)

    val optionsPanel = JPanel(BorderLayout())
    optionsPanel.add(umlOptionsPanel, BorderLayout.NORTH)
    optionsPanel.add(classTreePanel, BorderLayout.CENTER)
    panel.add(
        DragScrollPane(optionsPanel).apply {
          horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        },
        BorderLayout.WEST,
    )
    panel.add(
        DragScrollPane(pngViewerPanel).apply {
          border = TitledBorder("Dependency Diagram")
          verticalScrollBar.unitIncrement = 16
          horizontalScrollBar.unitIncrement = 16
        },
        BorderLayout.CENTER,
    )
  }

  private fun loadDataAsync(
      jobParams: IdeaRenderJob,
      project: Project,
      disposable: Disposable,
      classTreePanel: ClassTreePanel,
  ) {
    ReadAction.nonBlocking<List<String>> { getAllQualifiedClassNames(project) }
        .inSmartMode(project)
        .expireWith(disposable)
        .finishOnUiThread(ModalityState.any()) { classNames ->
          classTreePanel.allQualifiedClassNames = classNames
          classTreePanel.updatePanel(jobParams)
        }
        .submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun getAllQualifiedClassNames(project: Project): List<String> =
      DumbService.getInstance(project)
          .runReadActionInSmartMode(
              Computable {
                AllClassesSearch.search(GlobalSearchScope.projectScope(project), project)
                    .filter { psiClass -> psiClass.containingClass == null }
                    .mapNotNull { it.qualifiedName }
                    .sorted()
              }
          )

  private fun getJobParams(diagramContent: String): IdeaRenderJob {
    val content = diagramContent.lines()
    val configYaml =
        content.subList(content.indexOf(MARKER_STARTCONFIG) + 1, content.indexOf(MARKER_ENDCONFIG))
    return Yaml.default.decodeFromString(IdeaRenderJob.serializer(), configYaml.joinToString("\n"))
  }

  fun updateDiagram() {
    jobParams.renderJob.classDiagrams.let {
      it.classesToAnalyze = classTreePanel.getClassesToAnalyze()
      it.packagesToAnalyze = classTreePanel.getPackagesToAnalyze()
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
    pngViewerPanel.updatePanel(diagramContent, jobParams.optionPanelState)
    umlOptionsPanel.updateFields(jobParams)
    ApplicationManager.getApplication().invokeLater { classTreePanel.updatePanel(jobParams) }
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

  override fun dispose() {
    Disposer.dispose(disposable)
  }

  override fun getFile(): VirtualFile = diagramFile
}
