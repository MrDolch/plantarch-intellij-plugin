package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil.getPathForLocation
import tech.dolch.plantarch.cmd.IdeaRenderJob
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.tree.*

class ClassTreePanel(
    jobParams: IdeaRenderJob,
    treeProvider: (DefaultTreeModel) -> JTree = { treeModel -> Tree(treeModel) },
    val onChange: () -> Unit,
) : JPanel() {
  var containerEntries: List<ContainerEntry> = listOf()
  val treeModel: DefaultTreeModel = DefaultTreeModel(DefaultMutableTreeNode())
  val tree: JTree

  init {
    layout = BorderLayout()
    tree =
        treeProvider(treeModel).apply {
          isRootVisible = false
          cellRenderer = CellRenderer
          addMouseListener(SelectionListener(this, onChange))
        }
    add(JScrollPane(tree).apply { border = TitledBorder("Class Tree") })
    updatePanel(jobParams)
  }

  private fun getContainerEntries(jobParams: IdeaRenderJob): List<ContainerEntry> {
    val classpathEntries: List<ClassEntry> =
        jobParams.optionPanelState.classesInFocus.map {
          ClassEntry(
              name = it,
              visibility =
                  when {
                    jobParams.optionPanelState.classesInFocusSelected.contains(it) ->
                        VisibilityStatus.IN_FOCUS
                    jobParams.optionPanelState.hiddenClassesSelected.contains(it) ->
                        VisibilityStatus.HIDDEN
                    else -> VisibilityStatus.MAYBE
                  },
          )
        }
    val packageEntries =
        classpathEntries.groupBy { it.getPackageName() }.map { PackageEntry(it.key, it.value) }

    val containerEntries =
        jobParams.optionPanelState.hiddenContainers.sorted().map {
          ContainerEntry(
              it,
              emptyList(),
              if (jobParams.renderJob.classDiagrams.containersToHide.contains(it))
                  VisibilityStatus.HIDDEN
              else VisibilityStatus.MAYBE,
          )
        }
    return listOf(ContainerEntry("Source Classes", packageEntries)) + containerEntries
  }

  fun buildTreeModel(entries: List<ContainerEntry>): DefaultMutableTreeNode {
    return DefaultMutableTreeNode("Root")
        .withChildren(
            entries.map { containerEntry ->
              DefaultMutableTreeNode(containerEntry)
                  .withChildren(
                      containerEntry.packages.map { packageEntry ->
                        DefaultMutableTreeNode(packageEntry)
                            .withChildren(
                                packageEntry.classes.map { classEntry ->
                                  DefaultMutableTreeNode(classEntry)
                                }
                            )
                      }
                  )
            }
        )
  }

  fun DefaultMutableTreeNode.withChildren(
      newChildren: List<MutableTreeNode>
  ): DefaultMutableTreeNode {
    newChildren.forEach { add(it) }
    return this
  }

  fun expandSelectedBranches(tree: JTree) {
    val model = tree.model as DefaultTreeModel
    val root = model.root as TreeNode

    fun shouldExpand(node: TreeNode): Boolean {
      if (node is DefaultMutableTreeNode) {
        val userObj = node.userObject
        if (userObj is ClassEntry && userObj.visibility == VisibilityStatus.IN_FOCUS) {
          return true
        }
        val children = (0 until node.childCount).map { node.getChildAt(it) }
        return children.any { shouldExpand(it) }
      }
      return false
    }

    fun expandRecursively(node: TreeNode, path: TreePath) {
      if (shouldExpand(node)) {
        tree.expandPath(path)
      }
      for (i in 0 until node.childCount) {
        val child = node.getChildAt(i)
        expandRecursively(child, path.pathByAddingChild(child))
      }
    }
    expandRecursively(root, TreePath(root))
  }

  fun updatePanel(jobParams: IdeaRenderJob) {
    containerEntries = getContainerEntries(jobParams)
    treeModel.setRoot(buildTreeModel(containerEntries))
    expandSelectedBranches(tree)
  }

  fun toggleEntryFromDiagram(text: String) {
    // toggle Containers
    val visibleContainers = containerEntries.filter { it.visibility == VisibilityStatus.MAYBE }
    visibleContainers
        .filter { it.name == text }
        .forEach {
          it.visibility = VisibilityStatus.HIDDEN
          tree.invalidate()
          tree.updateUI()
          onChange()
          return
        }

    // toggle Classes
    val toToggle =
        visibleContainers
            .flatMap { it.packages }
            .flatMap { it.classes }
            .filter { it.name.endsWith(".$text") || it.name == text }
    if (toToggle.isNotEmpty()) {
      toToggle.forEach {
        it.visibility =
            if (it.visibility == VisibilityStatus.MAYBE) VisibilityStatus.IN_FOCUS
            else VisibilityStatus.MAYBE
      }
      tree.invalidate()
      tree.updateUI()
      onChange()
    }
  }

  fun getClassesToAnalyze() =
      containerEntries
          .flatMap { it.packages }
          .flatMap { it.classes }
          .filter { it.visibility == VisibilityStatus.IN_FOCUS }
          .map { it.name }

  fun getClassesToHide() =
      containerEntries
          .flatMap { it.packages }
          .flatMap { it.classes }
          .filter { it.visibility == VisibilityStatus.HIDDEN }
          .map { it.name }

  fun getContainersToHide(): List<String> =
      containerEntries.filter { it.visibility == VisibilityStatus.HIDDEN }.map { it.name }
}

class SelectionListener(val tree: JTree, val onChange: () -> Unit) : MouseAdapter() {
  override fun mousePressed(e: MouseEvent) {
    val path: TreePath = getPathForLocation(tree, e.x, e.y) ?: return
    val bounds = tree.getPathBounds(path) ?: return
    val fontHeight = tree.getFontMetrics(tree.font).height
    val relativeX = e.x - bounds.x
    val relativeY = e.y - bounds.y
    val isInCheckBox =
        0 <= relativeX && relativeX <= fontHeight && 0 <= relativeY && relativeY <= fontHeight
    println("Font height: $fontHeight, bounds: $bounds  ($relativeX, $relativeY)")
    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
    if (SwingUtilities.isLeftMouseButton(e)) {
      val isCtrlDown = e.isControlDown
      println("Clicked: ${node}, Ctrl: $isCtrlDown")
      val entry = node.userObject
      when (entry) {
        is ClassEntry -> {
          entry.visibility =
              when (entry.visibility) {
                VisibilityStatus.HIDDEN -> VisibilityStatus.MAYBE
                VisibilityStatus.IN_FOCUS ->
                    if (isCtrlDown) VisibilityStatus.HIDDEN else VisibilityStatus.MAYBE
                else -> if (isCtrlDown) VisibilityStatus.HIDDEN else VisibilityStatus.IN_FOCUS
              }
          tree.invalidate()
          tree.updateUI()
          onChange()
        }

        is PackageEntry ->
            if (isInCheckBox) {
              entry.visibility =
                  when (entry.visibility) {
                    VisibilityStatus.HIDDEN -> VisibilityStatus.MAYBE
                    VisibilityStatus.IN_FOCUS ->
                        if (isCtrlDown) VisibilityStatus.HIDDEN else VisibilityStatus.MAYBE
                    else -> if (isCtrlDown) VisibilityStatus.HIDDEN else VisibilityStatus.IN_FOCUS
                  }
              entry.classes.forEach { it.visibility = entry.visibility }
              tree.invalidate()
              tree.updateUI()
              onChange()
            }

        is ContainerEntry -> {
          entry.visibility =
              when (entry.visibility) {
                VisibilityStatus.HIDDEN -> VisibilityStatus.MAYBE
                else -> VisibilityStatus.HIDDEN
              }
          tree.invalidate()
          tree.updateUI()
          onChange()
        }
      }
    }
  }
}

object CellRenderer : DefaultTreeCellRenderer() {
  override fun getTreeCellRendererComponent(
      tree: JTree,
      value: Any,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
  ): Component {
    val node = value as DefaultMutableTreeNode
    val treeEntry = node.userObject

    return when (treeEntry) {
      is ContainerEntry -> {
        JLabel(
            when {
              treeEntry.packages.isNotEmpty() -> treeEntry.name
              treeEntry.visibility == VisibilityStatus.HIDDEN ->
                  "<html>\u2612 <s>${treeEntry.name}</s></html>"
              else -> "\u2610 " + treeEntry.name
            }
        )
      }

      is PackageEntry -> {
        JLabel(
            when (treeEntry.visibility) {
              VisibilityStatus.IN_FOCUS -> "<html>\u2611 <b>${treeEntry.name}</b></html>"
              VisibilityStatus.HIDDEN -> "<html>\u2612 <s>${treeEntry.name}</s></html>"
              else -> "\u2610 " + treeEntry.name
            }
        )
      }

      is ClassEntry -> {
        JLabel(
            when (treeEntry.visibility) {
              VisibilityStatus.IN_FOCUS -> "<html>\u2611 <b>${treeEntry.getSimpleName()}</b></html>"
              VisibilityStatus.HIDDEN -> "<html>\u2612 <s>${treeEntry.getSimpleName()}</s></html>"
              else -> "\u2610 " + treeEntry.getSimpleName()
            }
        )
      }

      else ->
          super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    }
  }
}
