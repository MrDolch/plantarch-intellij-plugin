package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.FormBuilder
import tech.dolch.plantarch.ClassDiagram
import tech.dolch.plantarch.cmd.IdeaRenderJob
import tech.dolch.plantarch.cmd.ShowPackages
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.*

class UmlOptionsPanel(jobParams: IdeaRenderJob, val onChange: () -> Unit) : JPanel() {

  val titleField = JTextField(jobParams.renderJob.classDiagrams.title)
  val descriptionArea = JTextArea(jobParams.renderJob.classDiagrams.description, 5, 20)
  val showMethodNamesDropdown =
      ComboBox(ClassDiagram.UseByMethodNames.entries.toTypedArray()).apply {
        selectedItem = jobParams.renderJob.classDiagrams.showUseByMethodNames
      }
  val showPackagesDropdown =
      ComboBox(ShowPackages.entries.toTypedArray()).apply {
        selectedItem = jobParams.optionPanelState.showPackages
      }
  val autoRenderDiagram = JCheckBox("Auto Render diagram", true)

  init {
    layout = BorderLayout()
    this.add(
        JScrollPane(
            FormBuilder.createFormBuilder()
                .addLabeledComponent("Title:", titleField)
                .addComponent(
                    JPanel(GridLayout(0, 1)).apply {
                      border = BorderFactory.createTitledBorder("Description")
                      add(JScrollPane(descriptionArea))
                    }
                )
                .addComponent(
                    JPanel(GridLayout(1, 2)).apply {
                      val renderDiagramButton =
                          JButton("Render diagram").apply {
                            isEnabled = false
                            addActionListener { onChange() }
                          }
                      autoRenderDiagram.addItemListener {
                        renderDiagramButton.isEnabled = !autoRenderDiagram.isSelected
                      }
                      add(renderDiagramButton)
                      add(autoRenderDiagram)
                    }
                )
                .addComponent(
                    JPanel(GridLayout(2, 1)).apply {
                      border = BorderFactory.createTitledBorder("Options")
                      add(
                          JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                            add(JLabel("Show method names:"))
                            add(showMethodNamesDropdown)
                          }
                      )
                      add(
                          JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                            add(JLabel("Show packages:"))
                            add(showPackagesDropdown)
                            showPackagesDropdown.selectedItem = ShowPackages.NESTED
                          }
                      )
                    }
                )
                .panel
        ),
        BorderLayout.NORTH,
    )
  }

  fun getShowUseByMethodNames() =
      showMethodNamesDropdown.selectedItem as ClassDiagram.UseByMethodNames

  fun getTitle(): String = titleField.text

  fun getDescription(): String = descriptionArea.text

  fun getShowPackages() = showPackagesDropdown.selectedItem as ShowPackages

  fun updateFields(jobParams: IdeaRenderJob) {
    titleField.text = jobParams.renderJob.classDiagrams.title
    descriptionArea.text = jobParams.renderJob.classDiagrams.description
    showMethodNamesDropdown.let {
      it.itemListeners.forEach { listener -> it.removeItemListener(listener) }
      it.selectedItem = jobParams.renderJob.classDiagrams.showUseByMethodNames
      it.addItemListener { if (autoRenderDiagram.isSelected) onChange() }
    }
    showPackagesDropdown.let {
      it.itemListeners.forEach { listener -> it.removeItemListener(listener) }
      it.selectedItem = jobParams.optionPanelState.showPackages
      it.addItemListener { if (autoRenderDiagram.isSelected) onChange() }
    }
  }
}
