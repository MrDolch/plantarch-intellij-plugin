package com.github.mrdolch.plantarchintellijplugin.toolWindow

import com.github.mrdolch.plantarchintellijplugin.toolWindow.ExecPlantArch.runAnalyzerBackgroundTask
import com.intellij.ui.components.JBList
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.ListTableModel
import tech.dolch.plantarch.cmd.IdeaRenderJob
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridLayout
import javax.swing.*
import javax.swing.border.TitledBorder

class PanelDiagramOptions : JPanel(BorderLayout()) {

  private val containersBox = JBList<String>()
  private val umlOptionsPanel = UmlOptionsPanel(this)
  private var jobParams: IdeaRenderJob? = null
  private val classesTable: ListTableModel<ClassEntry>


  init {
    layout = GridLayout(1, 4)

    add(JScrollPane(umlOptionsPanel).apply { border = TitledBorder("Optionen") })
    umlOptionsPanel.showMethodNamesCheckbox.addItemListener { updateDiagram() }

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
          updateDiagram()
        }
      },
      object : ColumnInfo<ClassEntry, Boolean>("Sichtbar") {
        override fun getColumnClass(): Class<*> = Boolean::class.java
        override fun valueOf(item: ClassEntry) = item.isVisible
        override fun isCellEditable(item: ClassEntry) = true
        override fun setValue(item: ClassEntry, value: Boolean) {
          item.isVisible = value
          updateDiagram()
        }
      }
    )
    classesTable.items = mutableListOf();

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
    addList(containersBox, "Visible Containers")
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

  fun updateDiagram() {
    if (jobParams == null) return
    jobParams!!.renderJob.classDiagrams.let {
      it.classesToAnalyze = classesTable.items.filter { c -> c.isAnalyzed }.map { c -> c.name }
      it.containersToHide = (0 until containersBox.model.size)
        .map { i -> containersBox.model.getElementAt(i) }
        .minus(containersBox.selectedValuesList.toSet())
      it.classesToHide = classesTable.items.filter { c -> !c.isVisible }.map { c -> c.name }
      it.showUseByMethodNames = umlOptionsPanel.showMethodNamesCheckbox.isSelected
      it.title = umlOptionsPanel.titleField.text
      it.description = umlOptionsPanel.descriptionArea.text
    }
    jobParams!!.optionPanelState.let {
      it.showPackages = umlOptionsPanel.showPackagesCheckbox.isSelected
      it.flatPackages = umlOptionsPanel.flatPackagesCheckbox.isSelected
    }

    runAnalyzerBackgroundTask(jobParams!!, false)
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

    if (hasChanged) updateDiagram()
  }
}


data class ClassEntry(
  val name: String,
  var isAnalyzed: Boolean,
  var isVisible: Boolean
)

class UmlOptionsPanel(private val panelDiagramOptions: PanelDiagramOptions) : JPanel(BorderLayout()) {

  val titleField = JTextField()
  val descriptionArea = JTextArea(5, 20)
  val showMethodNamesCheckbox = JCheckBox("show method names")
  val showPackagesCheckbox = JCheckBox("show packages")
  val flatPackagesCheckbox = JCheckBox("flat packages")

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
            addActionListener { panelDiagramOptions.updateDiagram() }
          })
          .addComponent(JPanel(GridLayout(0, 1)).apply {
            border = BorderFactory.createTitledBorder("Options")
            add(showMethodNamesCheckbox)
            add(showPackagesCheckbox)
            add(flatPackagesCheckbox)
          }).panel
      ), BorderLayout.NORTH
    )
  }
}
