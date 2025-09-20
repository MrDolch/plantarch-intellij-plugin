package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.github.mrdolch.plantarchintellijplugin.asm.Containers
import com.github.mrdolch.plantarchintellijplugin.asm.ShowPackages
import com.github.mrdolch.plantarchintellijplugin.asm.UseByMethodNames
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.border.TitledBorder

class DiagramEditor(private val diagramFile: VirtualFile) : UserDataHolderBase(), FileEditor {
  companion object {
    const val INITIAL_PUML =
        "@startuml\ntitle Compiling... Analyzing... Rendering... Interacting... \ncaption Please wait...\n@enduml\n"
  }

  private val panel: JPanel
  private val optionPanelState: OptionPanelState
  private val optionPanel: OptionPanel
  private val pngViewerPanel: PngViewerPanel
  private val disposable = Disposer.newDisposable()
  private val project: Project

  private val showMethodNamesDropdown: ComboBox<UseByMethodNames>
  private val showPackagesDropdown: ComboBox<ShowPackages>
  private val showLibraries: JCheckBox

  init {
    val diagramContent = String(diagramFile.contentsToByteArray())
    optionPanelState = getOptionPanelState(diagramContent)
    project = getProjectByName(optionPanelState.projectName)
    optionPanel = OptionPanel(optionPanelState) { renderDiagram(true) }

    val autoRender = JCheckBox("Auto Render", true)
    fun onChange() {
      if (autoRender.isSelected) renderDiagram(true)
    }
    val compileNowButton =
        JButton("Clear Cache").apply { addActionListener {
          Containers.clear()
          renderDiagram(false) }
        }
    val renderNowButton = JButton("Render now").apply { addActionListener { onChange() } }

    showMethodNamesDropdown =
        ComboBox(UseByMethodNames.entries.toTypedArray()).apply {
          selectedItem = optionPanelState.showUseByMethodNames
          addItemListener {
            optionPanelState.showUseByMethodNames = selectedItem as UseByMethodNames
            onChange()
          }
        }
    showPackagesDropdown =
        ComboBox(ShowPackages.entries.toTypedArray()).apply {
          selectedItem = optionPanelState.showPackages
          addItemListener {
            optionPanelState.showPackages = selectedItem as ShowPackages
            onChange()
          }
        }
    showLibraries =
        JCheckBox("Show Libraries", optionPanelState.showLibraries).apply {
          addActionListener {
            optionPanelState.showLibraries = isSelected
            onChange()
          }
        }

    pngViewerPanel =
        PngViewerPanel(diagramFile.readText(), project, optionPanel) { renderDiagram(true) }

    if (diagramContent.startsWith(INITIAL_PUML)) {
      renderDiagram(false)
    } else {
      pngViewerPanel.updatePanel(
          diagramContent.substringBefore(OptionPanelState.MARKER_STARTCONFIG)
      )
    }

    panel =
        JPanel(BorderLayout()).apply {
          val left =
              DragScrollPane(optionPanel).apply {
                isVisible = false
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
              }
          add(left, BorderLayout.WEST)
          add(
              DragScrollPane(
                      JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        border = JBUI.Borders.emptyBottom(4)
                        add(
                            JButton("Side Menu").apply {
                              addActionListener { left.isVisible = !left.isVisible }
                            }
                        )
                        add(compileNowButton)
                        add(renderNowButton)
                        add(autoRender.apply { horizontalTextPosition = SwingConstants.LEFT })
                        add(JLabel("Show methods:"))
                        add(showMethodNamesDropdown)
                        add(JLabel("Show packages:"))
                        add(showPackagesDropdown)
                        add(showLibraries.apply { horizontalTextPosition = SwingConstants.LEFT })
                      }
                  )
                  .apply { verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER },
              BorderLayout.NORTH,
          )
          add(
              DragScrollPane(pngViewerPanel).apply { border = TitledBorder("Dependency Diagram") },
              BorderLayout.CENTER,
          )
        }
  }

  fun getProjectByName(projectName: String) =
      ProjectManager.getInstance().openProjects.firstOrNull { it.name == projectName }
          ?: ProjectManager.getInstance().defaultProject

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
      optionPanelState.title = optionPanel.titleField.text
      optionPanelState.description = optionPanel.captionArea.text
      optionPanelState.plamtumlInlineOptions = optionPanel.stylesArea.text
      optionPanelState.markerClasses = optionPanel.markerClassesArea.text.split("\n").toSet()
    }
    ExecPlantArch.runAnalyzerBackgroundTask(project, optionPanelState, isUpdate) { result ->
      runOnEdt {
        showLibraries.isSelected = optionPanelState.showLibraries
        optionPanelState.librariesDiscovered.addAll(result.containersInDiagram)
        optionPanelState.classesInDiagram.apply {
          clear()
          addAll(result.classesInDiagram)
        }
        showPackagesDropdown.selectedItem = optionPanelState.showPackages
        showMethodNamesDropdown.selectedItem = optionPanelState.showUseByMethodNames

        pngViewerPanel.updatePanel(result.plantUml)
      }
    }
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
