package com.github.mrdolch.plantarchintellijplugin.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBList
import tech.dolch.plantarch.cmd.IdeaRenderJob
import java.awt.*
import javax.swing.*
import javax.swing.border.TitledBorder

class PanelDiagramOptions : JPanel(BorderLayout()) {

    private val classesInFocusBox = JBList<String>()
    private val containersBox = JBList<String>()
    private val hiddenClassesBox = JBList<String>()
    private val umlOptionsPanel = UmlOptionsPanel()
    private var jobParams: IdeaRenderJob? = null


    init {
        layout = GridLayout(1, 4)

        add(JScrollPane(umlOptionsPanel).apply { border = TitledBorder("Optionen") })
        umlOptionsPanel.showMethodNamesCheckbox.addItemListener { updateDiagram() }

        addList(classesInFocusBox, "Classes in Focus")
        addList(containersBox, "Visible Containers")
        addList(hiddenClassesBox, "Hidden Classes")
    }

    private fun addList(jbList: JBList<String>, title: String) {
        add(JScrollPane(jbList).apply { border = TitledBorder(title) })
        jbList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val rawText = value.toString()
                val renderedText = if (list.isSelectedIndex(index)) {
                    rawText
                } else {
                    "<html><s>$rawText</s></html>"
                }
                return super.getListCellRendererComponent(list, renderedText, index, isSelected, cellHasFocus)
            }
        }
        jbList.setSelectionModel(object : DefaultListSelectionModel() {
            init {
                selectionMode = MULTIPLE_INTERVAL_SELECTION
            }

            override fun setSelectionInterval(index0: Int, index1: Int) {
                if (isSelectedIndex(index0)) {
                    super.removeSelectionInterval(index0, index1)
                } else {
                    super.addSelectionInterval(index0, index1)
                }
                if (jobParams != null) {
                    updateDiagram()
                }
            }
        })

    }

    private fun updateDiagram() {
        jobParams!!.renderJob.classDiagrams.let {
            it.classesToAnalyze = classesInFocusBox.selectedValuesList
            it.containersToHide = (0 until containersBox.model.size)
                .map { i -> containersBox.model.getElementAt(i) }
                .minus(containersBox.selectedValuesList.toSet())
            it.classesToHide = hiddenClassesBox.selectedValuesList
            it.showUseByMethodNames = umlOptionsPanel.showMethodNamesCheckbox.isSelected
            it.title = umlOptionsPanel.titleField.text
            it.description = umlOptionsPanel.descriptionArea.text
        }
        jobParams!!.optionPanelState.let {
            it.showPackages = umlOptionsPanel.showPackagesCheckbox.isSelected
            it.flatPackages = umlOptionsPanel.flatPackagesCheckbox.isSelected
        }
        ApplicationManager.getApplication().invokeLater {
            ExecPlantArch.executePlantArch(jobParams!!)
        }
    }

    private fun setItems(jbList: JBList<String>, items: List<String>, selectedItems: List<String>) {
        val selectedIndices = items.withIndex()
            .filter { it.value in selectedItems }
            .map { it.index }
            .toIntArray()
        jbList.setListData(items.toTypedArray())
        jbList.setSelectedIndices(selectedIndices)
    }

    fun updatePanel(jobParams: IdeaRenderJob) {
        this.jobParams = jobParams
        setItems(
            classesInFocusBox,
            jobParams.optionPanelState.classesInFocus,
            jobParams.optionPanelState.classesInFocusSelected
        )
        setItems(
            hiddenClassesBox,
            jobParams.optionPanelState.hiddenClasses,
            jobParams.optionPanelState.hiddenClassesSelected
        )
        setItems(
            containersBox,
            jobParams.optionPanelState.hiddenContainers,
            jobParams.optionPanelState.hiddenContainers - jobParams.optionPanelState.hiddenContainersSelected.toSet()
        )
        umlOptionsPanel.titleField.text = jobParams.renderJob.classDiagrams.title
        umlOptionsPanel.descriptionArea.text = jobParams.renderJob.classDiagrams.description
        umlOptionsPanel.showMethodNamesCheckbox.let {
            it.itemListeners.forEach { listener -> it.removeItemListener(listener) }
            it.isSelected = jobParams.renderJob.classDiagrams.showUseByMethodNames
            it.addItemListener { updateDiagram() }
        }
        umlOptionsPanel.showPackagesCheckbox.let {
            it.itemListeners.forEach { listener -> it.removeItemListener(listener) }
            it.isSelected = jobParams.optionPanelState.showPackages
            it.addItemListener { updateDiagram() }
        }
        umlOptionsPanel.flatPackagesCheckbox.let {
            it.itemListeners.forEach { listener -> it.removeItemListener(listener) }
            it.isSelected = jobParams.optionPanelState.flatPackages
            it.addItemListener { updateDiagram() }
        }
    }

    fun toggleListEntry(text: String) {
        for (i in 0 until classesInFocusBox.model.size)
            if (classesInFocusBox.model.getElementAt(i).endsWith(".$text")) {
                val selection = classesInFocusBox.selectedIndices.toMutableSet()
                if (selection.contains(i)) selection.remove(i)
                else selection.add(i)
                classesInFocusBox.selectedIndices = selection.sorted().toIntArray()
                updateDiagram()
                return
            }
    }
}


class UmlOptionsPanel : JPanel(BorderLayout()) {

    val titleField = JTextField()
    val descriptionArea = JTextArea(5, 20)
    val showMethodNamesCheckbox = JCheckBox("show method names")
    val showPackagesCheckbox = JCheckBox("show packages")
    val flatPackagesCheckbox = JCheckBox("flat packages")

    init {
        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
        }

        // Titel-Eingabe
        gbc.gridx = 0
        gbc.gridy = 0
        formPanel.add(JLabel("Titel:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(titleField, gbc)

        // Beschreibung (mehrzeilig)
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        formPanel.add(JLabel("Beschreibung:"), gbc)
        gbc.gridx = 1
        val scroll = JScrollPane(descriptionArea)
        formPanel.add(scroll, gbc)

        // Checkboxen
        val checkboxPanel = JPanel(GridLayout(0, 1))
        checkboxPanel.border = BorderFactory.createTitledBorder("Optionen")
        checkboxPanel.add(showMethodNamesCheckbox)
        checkboxPanel.add(showPackagesCheckbox)
        checkboxPanel.add(flatPackagesCheckbox)

        // Layout kombinieren
        this.add(formPanel, BorderLayout.NORTH)
        this.add(checkboxPanel, BorderLayout.CENTER)
    }
}
