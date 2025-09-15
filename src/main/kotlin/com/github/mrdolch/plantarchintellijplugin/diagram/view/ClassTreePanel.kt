package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.github.mrdolch.plantarchintellijplugin.asm.Result
import com.intellij.openapi.Disposable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil.getPathForLocation
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.tree.*

class ClassTreePanel(
    val optionPanelState: OptionPanelState,
    treeProvider: (DefaultTreeModel) -> JTree = { treeModel -> Tree(treeModel) },
    val onChange: () -> Unit,
) : JPanel(), Disposable {
  var containerEntries: List<ContainerEntry> = listOf()
  val treeModel: DefaultTreeModel = DefaultTreeModel(DefaultMutableTreeNode())
  val tree: JTree
  var allQualifiedClassNames: List<String> = emptyList()

  init {
    layout = BorderLayout()
    tree =
        treeProvider(treeModel).apply {
          isRootVisible = false
          cellRenderer = CellRenderer
          addMouseListener(SelectionListener(this) { onChange() })
        }
    add(JScrollPane(tree).apply { border = TitledBorder("Class Tree") })
    initClassTree(optionPanelState)
  }

  private fun getContainerEntries(optionPanelState: OptionPanelState): List<ContainerEntry> {
    val classpathEntries: List<ClassEntry> =
        allQualifiedClassNames.sorted().distinct().map {
          val inFocus = optionPanelState.classesToAnalyze
          val hidden = optionPanelState.classesToHide
          ClassEntry(
              name = it,
              visibility =
                  when {
                    inFocus.contains(it) -> VisibilityStatus.IN_FOCUS
                    hidden.contains(it) -> VisibilityStatus.HIDDEN
                    else -> VisibilityStatus.IN_CLASSPATH
                  },
          )
        }
    val packageEntries =
        classpathEntries
            .groupBy { it.getPackageName() }
            .map {
              PackageEntry(
                  it.key,
                  when {
                    //
                    // jobParams.renderJob.classDiagrams.packagesToAnalyze.contains(it.key) ->
                    //                 VisibilityStatus.IN_FOCUS
                    else -> VisibilityStatus.MAYBE
                  },
                  it.value,
              )
            }

    val containerEntries =
        optionPanelState.librariesToHide.sorted().distinct().map {
          ContainerEntry(
              it,
              when {
                optionPanelState.librariesToHide.contains(it) -> VisibilityStatus.HIDDEN
                else -> VisibilityStatus.MAYBE
              },
              emptyList(),
          )
        }
    return listOf(ContainerEntry("Source Classes", packages = packageEntries)) + containerEntries
  }

  private fun getNewContainerEntriesForLibraries(result: Result): List<ContainerEntry> =
      result.libraryPaths
          .filter { it.endsWith(".jar") }
          .filter { containerEntries.none { entry -> entry.name == it } }
          .sorted()
          .map {
            ContainerEntry(
                it,
                VisibilityStatus.MAYBE,
                emptyList(),
            )
          }

  fun buildTreeModel(entries: List<ContainerEntry>) =
      DefaultMutableTreeNode("Root").withChildrenOf(entries) { ce ->
        DefaultMutableTreeNode(ce).withChildrenOf(ce.packages) { pe ->
          DefaultMutableTreeNode(pe).withChildrenOf(pe.classes) { cl -> DefaultMutableTreeNode(cl) }
        }
      }

  private fun <T> DefaultMutableTreeNode.withChildrenOf(
      items: Iterable<T>,
      build: (T) -> DefaultMutableTreeNode,
  ): DefaultMutableTreeNode = apply { items.forEach { add(build(it)) } }

  fun expandSelectedBranches(tree: JTree) {
    val model = tree.model as DefaultTreeModel
    val root = model.root as TreeNode

    fun shouldExpand(node: TreeNode): Boolean {
      if (node is DefaultMutableTreeNode) {
        val userObj = node.userObject
        if (userObj !is Entry || userObj.visibility == VisibilityStatus.IN_FOCUS) {
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

  fun initClassTree(jobParams: OptionPanelState) {
    containerEntries = getContainerEntries(jobParams)
    treeModel.setRoot(buildTreeModel(containerEntries))
    expandSelectedBranches(tree)
  }

  fun addNewLibraryEntries(result: Result) {
    val newContainerEntriesForLibraries = getNewContainerEntriesForLibraries(result)
    containerEntries += newContainerEntriesForLibraries
    newContainerEntriesForLibraries.forEach {
      (treeModel.root as DefaultMutableTreeNode).add(DefaultMutableTreeNode(it))
    }
    tree.invalidate()
    tree.updateUI()
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
            if (it.visibility != VisibilityStatus.IN_FOCUS) VisibilityStatus.IN_FOCUS
            else VisibilityStatus.MAYBE
      }
      expandSelectedBranches(tree)
      tree.invalidate()
      tree.updateUI()
      onChange()
    }
  }

  fun getClassesToAnalyze() =
      (containerEntries
              .flatMap { it.packages }
              .filter { it.visibility != VisibilityStatus.HIDDEN }
              .flatMap { it.classes }
              .filter { it.visibility == VisibilityStatus.IN_FOCUS }
              .map { it.name } +
              containerEntries
                  .flatMap { it.packages }
                  .filter { it.visibility == VisibilityStatus.IN_FOCUS }
                  .flatMap { it.classes }
                  .filter { it.visibility != VisibilityStatus.HIDDEN }
                  .map { it.name })
          .distinct()

  fun getPackagesToAnalyze() =
      containerEntries
          .flatMap { it.packages }
          .filter { it.visibility == VisibilityStatus.IN_FOCUS }
          .map { it.name }

  fun getClassesToHide() =
      (containerEntries
              .flatMap { it.packages }
              .flatMap { it.classes }
              .filter { it.visibility == VisibilityStatus.HIDDEN } +
              containerEntries
                  .flatMap { it.packages }
                  .filter { it.visibility == VisibilityStatus.HIDDEN }
                  .flatMap { it.classes })
          .distinct()
          .map { it.name }

  fun getContainersToHide(): Set<String> =
      containerEntries
          .filter { it.visibility == VisibilityStatus.HIDDEN }
          .filter { it.name.endsWith(".jar") }
          .map { it.name }
          .toSet()

  override fun dispose() {}

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
        if (entry is ClassEntry || entry is PackageEntry && isInCheckBox) {
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
        } else if (entry is ContainerEntry) {
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

@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
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
