package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.github.mrdolch.plantarchintellijplugin.asm.ShowPackages
import com.github.mrdolch.plantarchintellijplugin.asm.UseByMethodNames
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.border.TitledBorder

class DiagramEditor(private val diagramFile: VirtualFile) : UserDataHolderBase(), FileEditor {
  companion object {
    const val INITIAL_PUML =
        "@startuml\ntitle Compiling... Analyzing... Rendering... Interacting... \ncaption Please wait...\n@enduml\n"
  }

  private val panel = JPanel(BorderLayout())
  private val optionPanelState: OptionPanelState
  private val optionPanel: OptionPanel
  private val classTreePanel: ClassTreePanel
  private val pngViewerPanel: PngViewerPanel
  private val disposable = Disposer.newDisposable()
  private val project: Project

  init {
    val diagramContent = String(diagramFile.contentsToByteArray())
    optionPanelState = getOptionPanelState(diagramContent)
    project = getProjectByName(optionPanelState.projectName)
    optionPanel = OptionPanel(optionPanelState) { renderDiagram(true) }
    classTreePanel =
        ClassTreePanel(optionPanelState) {
          if (optionPanel.autoRenderDiagram.isSelected) renderDiagram(true)
        }
    pngViewerPanel =
        PngViewerPanel(diagramFile.readText(), project, optionPanel, classTreePanel) {
          renderDiagram(true)
        }
    loadDataAsync(optionPanelState, project, this, classTreePanel)

    if (diagramContent.startsWith(INITIAL_PUML)) {
      renderDiagram(false)
    } else {
      pngViewerPanel.updatePanel(
          diagramContent.substringBefore(OptionPanelState.MARKER_STARTCONFIG)
      )
    }

    panel.add(
        DragScrollPane(
                JPanel(BorderLayout()).apply<JPanel> {
                  add(optionPanel, BorderLayout.NORTH)
                  add(classTreePanel, BorderLayout.CENTER)
                }
            )
            .apply { horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER },
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

  fun getProjectByName(projectName: String) =
      ProjectManager.getInstance().openProjects.firstOrNull { it.name == projectName }
          ?: ProjectManager.getInstance().defaultProject

  private fun loadDataAsync(
      jobParams: OptionPanelState,
      project: Project,
      disposable: Disposable,
      classTreePanel: ClassTreePanel,
  ) {
    ReadAction.nonBlocking<List<String>> { getAllQualifiedClassNames(project) }
        .inSmartMode(project)
        .expireWith(disposable)
        .finishOnUiThread(ModalityState.any()) { classNames ->
          classTreePanel.allQualifiedClassNames = classNames
          classTreePanel.initClassTree(jobParams)
        }
        .submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun getAllQualifiedClassNames(project: Project): List<String> =
      DumbService.getInstance(project)
          .runReadActionInSmartMode(
              Computable {
                val index = ProjectFileIndex.getInstance(project)
                AllClassesSearch.search(GlobalSearchScope.projectScope(project), project)
                    .filter { psiClass -> psiClass.containingClass == null }
                    .filter { psiClass ->
                      psiClass.containingFile?.virtualFile?.let {
                        !index.isInTestSourceContent(it)
                      } == true
                    }
                    .mapNotNull { it.qualifiedName }
                    .sorted()
              }
          )

  private fun getOptionPanelState(diagramContent: String): OptionPanelState =
      OptionPanelState.fromYaml(
          diagramContent
              .substringAfter(OptionPanelState.MARKER_STARTCONFIG)
              .substringBefore(OptionPanelState.MARKER_ENDCONFIG)
      )

  private fun runOnEdt(modality: ModalityState = ModalityState.any(), action: () -> Unit) {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) action() else app.invokeLater(action, modality)
  }

  fun renderDiagram(isUpdate: Boolean) {
    if (isUpdate) {
      // collect States from Panels
      optionPanelState.showPackages = optionPanel.showPackagesDropdown.selectedItem as ShowPackages
      optionPanelState.showUseByMethodNames =
          optionPanel.showMethodNamesDropdown.selectedItem as UseByMethodNames
      optionPanelState.title = optionPanel.titleField.text
      optionPanelState.description = optionPanel.descriptionArea.text
      optionPanelState.plamtumlInlineOptions = optionPanel.plamtumlInlineOptionsArea.text
      optionPanelState.markerClasses = optionPanel.markerClassesArea.text.split("\n")

      optionPanelState.classesToAnalyze = classTreePanel.getClassesToAnalyze()
      optionPanelState.classesToHide = classTreePanel.getClassesToHide()
      optionPanelState.librariesToHide = classTreePanel.getContainersToHide()
    }
    ExecPlantArch.runAnalyzerBackgroundTask(project, optionPanelState, isUpdate) { result ->
      // populate States to Panels
      optionPanelState.librariesToHide += result.containersInDiagram.toList()
      runOnEdt {
        pngViewerPanel.updatePanel(result.plantUml)
        //      umlOptionsPanel.updateFields(optionPanelState)
        classTreePanel.addNewLibraryEntries(result)
      }
    }
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
