package com.github.mrdolch.plantarchintellijplugin.panel

import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.FormBuilder
import tech.dolch.plantarch.ClassDiagram
import tech.dolch.plantarch.cmd.ShowPackages
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.ActionEvent
import javax.swing.*

class UmlOptionsPanel(onRenderButton: (ActionEvent) -> Unit) : JPanel(BorderLayout()) {

  val titleField = JTextField()
  val descriptionArea = JTextArea(5, 20)
  val showMethodNamesDropdown = ComboBox(ClassDiagram.UseByMethodNames.entries.toTypedArray())
  val showPackagesDropdown = ComboBox(ShowPackages.entries.toTypedArray())
  val projectNameField = JLabel("Project")

  init {
    this.add(
      JScrollPane(
        FormBuilder.createFormBuilder()
          .addLabeledComponent("Title:", titleField)
          .addComponent(JPanel(GridLayout(0, 1)).apply {
            border = BorderFactory.createTitledBorder("Description")
            add(JScrollPane(descriptionArea))
          })
          .addLabeledComponent("", JButton("Render diagram").apply {
            addActionListener(onRenderButton)
          })
          .addComponent(JPanel(GridLayout(0, 1)).apply {
            border = BorderFactory.createTitledBorder("Options")
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
              add(JLabel("Show method names:"))
              add(showMethodNamesDropdown)
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
              add(JLabel("Show packages:"))
              add(showPackagesDropdown)
              showPackagesDropdown.selectedItem = ShowPackages.NESTED
            })
            add(projectNameField)
          }).panel
      ), BorderLayout.NORTH
    )
  }
}