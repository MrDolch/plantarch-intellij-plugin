package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.*

class OptionPanel(val optionPanelState: OptionPanelState, val onChange: () -> Unit) : JPanel() {

  val titleField =
      JTextField(optionPanelState.title).apply {
        addActionListener { if (optionPanelState.title != text) onChange() }
      }
  val captionArea = textAreaWithState(optionPanelState.description, onChange)
  val stylesArea =
      textAreaWithState(optionPanelState.plamtumlInlineOptions, onChange, 15)
  val markerClassesArea =
      textAreaWithState(optionPanelState.markerClasses.joinToString("\n"), onChange)

  init {
    layout = BorderLayout()
    add(
        JScrollPane(
            JPanel(GridBagLayout()).apply {
              border = BorderFactory.createTitledBorder("Plantuml-Options")

              var row = 0

              addRow(JLabel("Title"), titleField, row++)
              addFullWidth(
                  JPanel(BorderLayout()).apply {
                    border = BorderFactory.createTitledBorder("Caption")
                    add(JScrollPane(captionArea), BorderLayout.CENTER)
                  },
                  row++,
              )
              addFullWidth(
                  JPanel(FlowLayout(FlowLayout.RIGHT)).apply {},
                  row++,
              )
              addFullWidth(
                JPanel(BorderLayout()).apply {
                  border = BorderFactory.createTitledBorder("Marker-Classes")
                  add(JScrollPane(markerClassesArea), BorderLayout.CENTER)
                },
                row++,
              )
              addFullWidth(
                  JPanel(BorderLayout()).apply {
                    border = BorderFactory.createTitledBorder("Styles")
                    add(JScrollPane(stylesArea), BorderLayout.CENTER)
                  },
                  row++,
              )
            }
        ),
        BorderLayout.NORTH,
    )
  }

  private fun textAreaWithState(initial: String, onChange: () -> Unit, rows: Int = 5): JTextArea =
      JTextArea(initial, rows, 20).apply {
        addFocusListener(
            object : FocusAdapter() {
              override fun focusLost(e: FocusEvent) {
                if (text != initial) onChange()
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
