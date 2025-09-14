package com.github.mrdolch.plantarchintellijplugin.configuration

import com.github.mrdolch.plantarchintellijplugin.asm.ShowPackages
import com.github.mrdolch.plantarchintellijplugin.asm.UseByMethodNames
import com.intellij.openapi.options.BaseConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

internal class ConfigurationPanel(private val project: Project) :
    BaseConfigurable(), SearchableConfigurable {
  private val panel: JPanel = JPanel()
  private val showMethodNamesDropdown = ComboBox(UseByMethodNames.entries.toTypedArray())
  private val showPackagesDropdown = ComboBox(ShowPackages.entries.toTypedArray())
  private val plantumlOptions: JTextArea = JTextArea()
  private val markerClasses: JTextArea = JTextArea()


  override fun getId(): String {
    return "external-java-formatter.settings"
  }

  override fun getDisplayName(): String {
    return "External-Java-Formatter Settings"
  }

  override fun apply() {
    val configuration = project.getService(PersistConfigurationService::class.java).state
    configuration.showMethodNames = showMethodNamesDropdown.selectedItem as UseByMethodNames
    configuration.showPackages = showPackagesDropdown.selectedItem as ShowPackages
    configuration.plantumlOptions = plantumlOptions.text
    configuration.markerClasses = markerClasses.text

  }

  override fun reset() {
    val configuration = project.getService(PersistConfigurationService::class.java).state
    showMethodNamesDropdown.selectedItem = configuration.showMethodNames
    showPackagesDropdown.selectedItem = configuration.showPackages
    plantumlOptions.text = configuration.plantumlOptions
    plantumlOptions.caretPosition = 0
    markerClasses.text = configuration.markerClasses
    markerClasses.caretPosition = 0
  }

  override fun isModified(): Boolean {
    val configuration = project.getService(PersistConfigurationService::class.java).state
    return plantumlOptions.text != configuration.plantumlOptions ||
        markerClasses.text != configuration.markerClasses ||
        showMethodNamesDropdown.selectedItem != configuration.showMethodNames ||
        showPackagesDropdown.selectedItem != configuration.showPackages
  }

  override fun createComponent(): JComponent {
    val monospacedFont = Font(Font.MONOSPACED, Font.PLAIN, plantumlOptions.font.size)
    plantumlOptions.setFont(monospacedFont)
    markerClasses.setFont(monospacedFont)

    val plantumlOptionsPane =
        JBScrollPane(
                plantumlOptions,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS,
            )
            .apply { preferredSize = Dimension(preferredSize.width, 450) }

    val markerClassesPane =
        JBScrollPane(
                markerClasses,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS,
            )
            .apply { preferredSize = Dimension(preferredSize.width, 150) }

    panel.layout = GridLayoutManager(10, 2, JBUI.emptyInsets(), -1, -1)
    var currentRow = 0
    panel.add(JLabel("Default Show Methods"), left(++currentRow))
    panel.add(showMethodNamesDropdown, right(currentRow, Dimension(50, 50)))
    panel.add(JLabel("Default Show Packages"), left(++currentRow))
    panel.add(showPackagesDropdown, right(currentRow, Dimension(50, 50)))
    panel.add(JLabel("Marker-Classes"), left(++currentRow))
    panel.add(markerClassesPane, right(currentRow, Dimension(50, 50)))
    panel.add(JLabel("Plantuml-Inline-Options"), left(++currentRow))
    panel.add(plantumlOptionsPane, right(currentRow, Dimension(50, 50)))
    return panel
  }

  private fun left(row: Int) =
      GridConstraints(
          row,
          0,
          1,
          1,
          GridConstraints.ANCHOR_WEST,
          GridConstraints.FILL_NONE,
          GridConstraints.SIZEPOLICY_FIXED,
          GridConstraints.SIZEPOLICY_FIXED,
          null,
          null,
          null,
          0,
          false,
      )

  private fun right(row: Int, minimumSize: Dimension? = null) =
      GridConstraints(
          row,
          1,
          1,
          1,
          GridConstraints.ANCHOR_WEST,
          GridConstraints.FILL_HORIZONTAL,
          GridConstraints.SIZEPOLICY_CAN_GROW,
          GridConstraints.SIZEPOLICY_CAN_GROW,
          minimumSize,
          null,
          null,
          0,
          false,
      )
}
