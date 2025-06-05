package com.github.mrdolch.plantarchintellijplugin.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBList
import tech.dolch.plantarch.cmd.IdeaRenderJob
import tech.dolch.plantarch.cmd.RenderJob
import java.awt.*
import javax.swing.*
import javax.swing.border.TitledBorder

class PanelDiagramOptions : JPanel(BorderLayout()) {

    private val list1 = JBList<String>()
    private val list2 = JBList<String>()
    private val list3 = JBList<String>()
    private val umlOptionsPanel = UmlOptionsPanel()
    private var jobParams: IdeaRenderJob? = null


    init {
        layout = GridLayout(1, 4)

        add(JScrollPane(umlOptionsPanel).apply { border = TitledBorder("Optionen") })
        umlOptionsPanel.showMethodNames.addItemListener { updateDiagram() }

        addList(list1, "Classes in Focus")
        addList(list2, "Visible Containers")
        addList(list3, "Hidden Classes")
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
            it.classesToAnalyze = getSelectedFromList1()
            it.containersToHide = getUnselectedFromList2()
            it.classesToHide = getSelectedFromList3()
            it.showUseByMethodNames = isShowMethodNames()
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

    fun isShowMethodNames(): Boolean = umlOptionsPanel.showMethodNames.isSelected
    fun getSelectedFromList1(): List<String> = list1.selectedValuesList
    fun getUnselectedFromList2(): List<String> {
        val all = (0 until list2.model.size).map { list2.model.getElementAt(it) }
        return all - list2.selectedValuesList
    }

    fun getSelectedFromList3(): List<String> = list3.selectedValuesList

    fun updatePanel(jobParams: IdeaRenderJob, plantuml: String) {
        this.jobParams = jobParams
        updateList1und3(plantuml, jobParams.renderJob)
        updateList2(plantuml, jobParams.renderJob)
        umlOptionsPanel.showMethodNames.let {
            it.itemListeners.forEach { listener -> it.removeItemListener(listener) }
            it.isSelected = jobParams.renderJob.classDiagrams.showUseByMethodNames
            it.addItemListener { updateDiagram() }
        }
    }

    private fun updateList1und3(plantuml: String, renderJob: RenderJob) {
        val selectedItems = renderJob.classDiagrams.classesToAnalyze
        val hiddenItems = renderJob.classDiagrams.classesToHide
        val allItems = plantuml.lineSequence()
            .filter {
                it.startsWith("enum ") || it.startsWith("class ")
                        || it.startsWith("abstract ") || it.startsWith("interface ")
            }.map { it.split(' ')[1] }
            .plus(selectedItems)
            .plus(hiddenItems)
            .distinct().sorted().toList()

        setItems(list1, allItems, selectedItems)
        setItems(list3, allItems, hiddenItems)
    }

    private fun updateList2(plantuml: String, renderJob: RenderJob) {
        val items = plantuml.lines()
            .filter { it.startsWith("object ") }
            .map { it.split('"')[1] }
            .plus(renderJob.classDiagrams.containersToHide)
            .distinct()
        val selectedItems = items - renderJob.classDiagrams.containersToHide.toSet()
        setItems(list2, items, selectedItems)
    }

    fun toggleListEntry(text: String) {
        for (i in 0 until list1.model.size)
            if (list1.model.getElementAt(i).endsWith(".$text")) {
                val selection = list1.selectedIndices.toMutableSet()
                if (selection.contains(i)) selection.remove(i)
                else selection.add(i)
                list1.selectedIndices = selection.sorted().toIntArray()
                updateDiagram()
                return
            }
    }
}


class UmlOptionsPanel : JPanel(BorderLayout()) {

    val titleField = JTextField()
    val descriptionArea = JTextArea(5, 20)
    val showMethodNames = JCheckBox("show method names")
    val checkbox2 = JCheckBox("Assoziationen anzeigen")
    val checkbox3 = JCheckBox("Nur öffentliche Member")

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
        checkboxPanel.add(showMethodNames)
        checkboxPanel.add(checkbox2)
        checkboxPanel.add(checkbox3)

        // Layout kombinieren
        this.add(formPanel, BorderLayout.NORTH)
        this.add(checkboxPanel, BorderLayout.CENTER)
    }
}
