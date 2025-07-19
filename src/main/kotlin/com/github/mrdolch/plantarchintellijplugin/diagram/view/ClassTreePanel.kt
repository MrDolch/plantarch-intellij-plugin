package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.intellij.ui.components.JBList
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import tech.dolch.plantarch.cmd.IdeaRenderJob
import java.awt.Component
import java.awt.GridLayout
import javax.swing.*
import javax.swing.border.TitledBorder

class ClassTreePanel(jobParams: IdeaRenderJob, val onChange: () -> Unit) : JPanel() {

  private val containersBox = JBList<String>()
  val classesTable: ListTableModel<ClassEntry>


  init {
    layout = GridLayout(2, 1)

    classesTable = ListTableModel<ClassEntry>(
      object : ColumnInfo<ClassEntry, String>("Klasse") {
        override fun valueOf(item: ClassEntry) = item.name
        override fun isCellEditable(item: ClassEntry) = false
      },
      object : ColumnInfo<ClassEntry, Boolean>("Analyse") {
        override fun getColumnClass(): Class<*> = Boolean::class.java
        override fun valueOf(item: ClassEntry) = item.isAnalyzed
        override fun isCellEditable(item: ClassEntry) = true
        override fun setValue(item: ClassEntry, value: Boolean) {
          item.isAnalyzed = value
          onChange()
        }
      },
      object : ColumnInfo<ClassEntry, Boolean>("Sichtbar") {
        override fun getColumnClass(): Class<*> = Boolean::class.java
        override fun valueOf(item: ClassEntry) = item.isVisible
        override fun isCellEditable(item: ClassEntry) = true
        override fun setValue(item: ClassEntry, value: Boolean) {
          item.isVisible = value
          onChange()
        }
      }
    )
    classesTable.items = mutableListOf()

    val table = TableView(classesTable).apply {
      setAutoCreateRowSorter(true)
      columnModel.getColumn(1).minWidth = 40
      columnModel.getColumn(1).maxWidth = 40
      columnModel.getColumn(1).preferredWidth = 40
      columnModel.getColumn(2).minWidth = 40
      columnModel.getColumn(2).maxWidth = 40
      columnModel.getColumn(2).preferredWidth = 40
    }
    add(JScrollPane(table).apply { border = TitledBorder("gefundene Klassen") })
    addList(containersBox)
    updatePanel(jobParams)
  }

  private fun addList(jbList: JBList<String>) {
    add(JScrollPane(jbList).apply { border = TitledBorder("Visible Containers") })
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
        onChange()
      }
    })

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
    classesTable.items = jobParams.optionPanelState.classesInFocus
      .map {
        ClassEntry(
          name = it,
          isAnalyzed = jobParams.optionPanelState.classesInFocusSelected.contains(it),
          isVisible = !jobParams.optionPanelState.hiddenClassesSelected.contains(it)
        )
      }
    classesTable.fireTableDataChanged()
    setItems(
      containersBox,
      jobParams.optionPanelState.hiddenContainers,
      jobParams.optionPanelState.hiddenContainers - jobParams.optionPanelState.hiddenContainersSelected.toSet()
    )
  }


  fun toggleEntryFromDiagram(text: String) {
    var hasChanged = false
    // toggle Class in Focus
    classesTable.items.filter { it.name.endsWith(".$text") }
      .forEach {
        it.isAnalyzed = !it.isAnalyzed
        hasChanged = true
      }

    // toggle Container
    for (i in 0 until containersBox.model.size)
      if (containersBox.model.getElementAt(i) == text) {
        val selection = containersBox.selectedIndices.toMutableSet()
        if (selection.contains(i)) selection.remove(i)
        else selection.add(i)
        containersBox.selectedIndices = selection.sorted().toIntArray()
        hasChanged = true
      }
    if (hasChanged) onChange()
  }

  fun getClassesToAnalyze() = classesTable.items.filter { c -> c.isAnalyzed }.map { c -> c.name }
  fun getContainersToHide() = (0 until containersBox.model.size)
    .map { i -> containersBox.model.getElementAt(i) }
    .minus(containersBox.selectedValuesList.toSet())

  fun getClassesToHide() = classesTable.items.filter { c -> !c.isVisible }.map { c -> c.name }
}