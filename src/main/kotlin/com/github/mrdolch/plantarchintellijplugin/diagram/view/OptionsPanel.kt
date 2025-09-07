package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.github.mrdolch.plantarchintellijplugin.asm.ShowPackages
import com.github.mrdolch.plantarchintellijplugin.asm.UseByMethodNames
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

class OptionsPanel(optionPanelState: OptionPanelState, val onChange: () -> Unit) : JPanel() {

  val titleField =
      JTextField(optionPanelState.title).apply {
        addActionListener {
          if (optionPanelState.title != text) {
            optionPanelState.title = text
            if (autoRenderDiagram.isSelected) onChange()
          }
        }
      }
  val descriptionArea =
      textAreaWithState(optionPanelState.description) { newText ->
        optionPanelState.description = newText
        if (autoRenderDiagram.isSelected) onChange()
      }
  val plamtumlInlineOptionsArea =
      textAreaWithState(optionPanelState.plamtumlInlineOptions) { newText ->
        optionPanelState.plamtumlInlineOptions = newText
        if (autoRenderDiagram.isSelected) onChange()
      }
  val markerClassesArea =
      textAreaWithState(optionPanelState.markerClassesSelected.joinToString("\n")) { newText ->
        optionPanelState.markerClassesSelected = newText.split("\n")
        if (autoRenderDiagram.isSelected) onChange()
      }

  val showMethodNamesDropdown =
      ComboBox(UseByMethodNames.entries.toTypedArray()).apply {
        selectedItem = optionPanelState.showUseByMethodNames
        addItemListener {
          optionPanelState.showUseByMethodNames = selectedItem as UseByMethodNames
          if (autoRenderDiagram.isSelected) onChange()
        }
      }
  val showPackagesDropdown =
      ComboBox(ShowPackages.entries.toTypedArray()).apply {
        selectedItem = optionPanelState.showPackages
        addItemListener {
          optionPanelState.showPackages = selectedItem as ShowPackages
          if (autoRenderDiagram.isSelected) onChange()
        }
      }
  val renderDiagramButton = JButton("Render diagram").apply { addActionListener { onChange() } }
  val autoRenderDiagram = JCheckBox("Auto Render diagram", true)

  init {
    layout = BorderLayout()
    add(
        JScrollPane(
            JPanel(GridBagLayout()).apply {
              border = BorderFactory.createTitledBorder("Options")

              var row = 0

              addRow(JLabel("Title:"), titleField, row++)
              addFullWidth(
                  JPanel(BorderLayout()).apply {
                    border = BorderFactory.createTitledBorder("Description")
                    add(JScrollPane(descriptionArea), BorderLayout.CENTER)
                  },
                  row++,
              )
              addRow(JLabel("Show method names:"), showMethodNamesDropdown, row++)
              addRow(JLabel("Show packages:"), showPackagesDropdown, row++)
              addFullWidth(
                  JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                    add(renderDiagramButton)
                    add(autoRenderDiagram)
                  },
                  row++,
              )
              addFullWidth(
                  JPanel(BorderLayout()).apply {
                    border = BorderFactory.createTitledBorder("Plantuml Inline Options")
                    add(JScrollPane(plamtumlInlineOptionsArea), BorderLayout.CENTER)
                  },
                  row++,
              )
              addFullWidth(
                  JPanel(BorderLayout()).apply {
                    border = BorderFactory.createTitledBorder("Marker-Classes")
                    add(JScrollPane(markerClassesArea), BorderLayout.CENTER)
                  },
                  row++,
              )
            }
        ),
        BorderLayout.CENTER,
    )
  }

  private fun textAreaWithState(initial: String, onChange: (String) -> Unit): JTextArea =
      JTextArea(initial, 5, 20).apply {
        addFocusListener(
            object : java.awt.event.FocusAdapter() {
              override fun focusLost(e: java.awt.event.FocusEvent) {
                if (text != initial) {
                  onChange(text)
                }
              }
            }
        )
      }

  fun JPanel.addRow(label: JComponent, comp: JComponent, row: Int, weightx: Double = 1.0) {
    val gbc =
        GridBagConstraints().apply {
          insets = JBUI.insets(4)
          fill = GridBagConstraints.HORIZONTAL
          gridy = row
        }

    gbc.gridx = 0
    gbc.weightx = 0.0
    add(label, gbc)

    gbc.gridx = 1
    gbc.weightx = weightx
    add(comp, gbc)
  }

  fun JPanel.addFullWidth(comp: JComponent, row: Int, weighty: Double = 0.0) {
    val gbc =
        GridBagConstraints().apply {
          insets = JBUI.insets(4)
          fill = if (weighty > 0.0) GridBagConstraints.BOTH else GridBagConstraints.HORIZONTAL
          gridx = 0
          gridy = row
          gridwidth = 2
          weightx = 1.0
          this.weighty = weighty
        }
    add(comp, gbc)
  }
}
