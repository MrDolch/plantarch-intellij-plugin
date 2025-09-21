package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.github.mrdolch.plantarchintellijplugin.asm.ShowPackages
import com.github.mrdolch.plantarchintellijplugin.asm.UseByMethodNames
import com.github.mrdolch.plantarchintellijplugin.diagram.utils.JumpToLibrary
import com.github.mrdolch.plantarchintellijplugin.diagram.utils.JumpToSource.jumpToClassInSources
import com.github.mrdolch.plantarchintellijplugin.diagram.utils.ProjectClassChooser
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.ItemEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO
import javax.swing.ButtonGroup
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import javax.swing.JTextField
import kotlin.math.max
import kotlin.reflect.KMutableProperty1
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader

class PngViewerPanel(
    puml: String,
    val project: Project,
    val optionPanel: OptionPanel,
    val onChange: () -> Unit,
) : JPanel(), Serializable {
  private lateinit var image: BufferedImage
  lateinit var svg: String
  lateinit var puml: String
  private lateinit var classNameBounds: Map<String, Rectangle>
  private var titleBounds: Rectangle? = null
  private var captionBounds: Rectangle? = null
  private var optionPanelState = optionPanel.optionPanelState

  init {
    updatePanel(puml)
    installPopupHandlers()

    addMouseMotionListener(
        object : MouseMotionAdapter() {
          override fun mouseMoved(e: MouseEvent) {
            val overClass = classNameBounds.values.any { it.contains(e.point) }
            cursor =
                if (overClass) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                else Cursor.getDefaultCursor()
          }
        }
    )
    addMouseListener(
        object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            classNameBounds
                .filter { it.value.contains(e.point) }
                .forEach { (simplename, _) ->
                  if (simplename.endsWith(".jar")) {
                    if (optionPanelState.librariesDiscovered.contains(simplename))
                        if (optionPanelState.librariesToHide.add(simplename)) onChange()
                  } else {
                    optionPanelState.classesInDiagram
                        .filter { it.substringAfterLast(".", it) == simplename }
                        .forEach { classname ->
                          if (
                              optionPanelState.classesToAnalyze.remove(classname) ||
                                  optionPanelState.classesToAnalyze.add(classname)
                          )
                              onChange()
                        }
                  }
                }
          }
        }
    )
  }

  fun updatePanel(puml: String) {
    this.puml = puml
    svg = renderSvg(puml)
    if (svg.contains("An error has occur")) {
      NotificationGroupManager.getInstance()
          .getNotificationGroup("PlantArch")
          .createNotification(
              "During image rendering in plantuml: An error as occurred.",
              NotificationType.WARNING,
          )
          .notify(project)
    }
    setPlantumlLimitSize()
    image = renderPng(puml)
    preferredSize = Dimension(image.width, image.height)
    classNameBounds = collectSvgClassBoxes(svg).associate { it.asEntry() }
    val collectSvgTitleAndCaption = collectSvgTitleAndCaption(svg)
    titleBounds =
        collectSvgTitleAndCaption
            .filter { it.kind == SvgLabel.Kind.TITLE }
            .map { it.rect }
            .firstOrNull()
    captionBounds =
        collectSvgTitleAndCaption
            .filter { it.kind == SvgLabel.Kind.CAPTION }
            .map { it.rect }
            .firstOrNull()
    invalidate()
    updateUI()
  }

  private fun setPlantumlLimitSize() {
    val minWidth =
        Regex("""<svg[^>]*\bwidth\s*=\s*["']\s*([0-9]+(?:\.[0-9]+)?)\s*(?:px)?["']""")
            .find(svg)
            ?.groupValues
            ?.get(1)
            ?.toInt() ?: 4096
    val minHeight =
        Regex("""<svg[^>]*\bheight\s*=\s*["']\s*([0-9]+(?:\.[0-9]+)?)\s*(?:px)?["']""")
            .find(svg)
            ?.groupValues
            ?.get(1)
            ?.toInt() ?: 4096
    System.setProperty("PLANTUML_LIMIT_SIZE", (32 + max(minWidth, minHeight)).toString())
  }

  fun renderPng(puml: String): BufferedImage =
      ImageIO.read(renderDiagram(puml, FileFormat.PNG).inputStream())

  fun renderSvg(puml: String): String = String(renderDiagram(puml, FileFormat.SVG))

  private fun renderDiagram(puml: String, format: FileFormat): ByteArray {
    val outputStream = ByteArrayOutputStream()
    SourceStringReader(puml).outputImage(outputStream, 0, FileFormatOption(format))
    return outputStream.toByteArray()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    g.drawImage(image, 0, 0, this)
  }

  private fun installPopupHandlers() {
    val listener =
        object : MouseAdapter() {
          private fun maybeShowPopup(e: MouseEvent) {
            if (!e.isPopupTrigger) return
            val classUnderMouse =
                classNameBounds.entries.firstOrNull { it.value.contains(e.point) }?.key
            val titleUnderMouse = titleBounds?.contains(e.point) ?: false
            val captionUnderMouse = captionBounds?.contains(e.point) ?: false
            val popup =
                when {
                  titleUnderMouse ->
                      buildTextAreaPopup(
                          "Edit Title",
                          optionPanel.titleField.text,
                      ) { newText ->
                        optionPanel.titleField.text = newText
                        onChange()
                      }

                  captionUnderMouse ->
                      buildTextAreaPopup(
                          "Edit Caption",
                          optionPanel.captionArea.text,
                      ) { newText ->
                        optionPanel.captionArea.text = newText
                        onChange()
                      }

                  classUnderMouse == null -> buildGeneralPopup()
                  classUnderMouse.endsWith(".jar") -> buildLibraryPopup(classUnderMouse)
                  else -> buildClassPopup(classUnderMouse)
                }
            popup.show(e.component, e.x, e.y)
          }

          override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)

          override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)
        }
    addMouseListener(listener)
  }

  private fun buildGeneralPopup(): JPopupMenu =
      JPopupMenu().apply {
        add(
            JMenuItem("Copy PNG Image").apply {
              addActionListener {
                CopyPasteManager.getInstance().setContents(ImageTransferable(image))
              }
            }
        )
        add(
            JMenuItem("Copy PlantUml Source").apply {
              addActionListener { CopyPasteManager.copyTextToClipboard(puml) }
            }
        )
        addSeparator()
        add(
            JMenuItem("Copy SVG Image").apply {
              addActionListener { CopyPasteManager.getInstance().setContents(SvgTransferable(svg)) }
            }
        )
        add(
            JMenuItem("Copy SVG XML").apply {
              addActionListener { CopyPasteManager.copyTextToClipboard(svg) }
            }
        )
        addSeparator()
        add(
            JMenu("Show Packages").apply {
              val group = ButtonGroup()
              ShowPackages.entries.forEach { value ->
                val item =
                    JRadioButtonMenuItem(value.name).apply {
                      isSelected = (value == optionPanelState.showPackages)
                      addActionListener {
                        optionPanelState.showPackages = value
                        onChange()
                      }
                    }
                group.add(item)
                add(item)
              }
            }
        )
        add(
            JMenu("Show Methods").apply {
              val group = ButtonGroup()
              UseByMethodNames.entries.forEach { value ->
                val item =
                    JRadioButtonMenuItem(value.name).apply {
                      isSelected = (value == optionPanelState.showUseByMethodNames)
                      addActionListener {
                        optionPanelState.showUseByMethodNames = value
                        onChange()
                      }
                    }
                group.add(item)
                add(item)
              }
            }
        )
        add(
            buildCheckboxMenu(
                "Visible Arrows",
                optionPanelState.showDependencies,
                listOf(
                    "Class inheritance" to Dependency::classInheritance,
                    "Class generic type" to Dependency::classGenericType,
                    "Class annotation" to Dependency::classAnnotation,
                    "Method call" to Dependency::methodCall,
                    "Method parameter type" to Dependency::methodParameterType,
                    "Method return type" to Dependency::methodReturnType,
                    "Field type" to Dependency::fieldType,
                ),
            ) {
              onChange()
            }
        )
        addSeparator()
        if (optionPanelState.classesToHide.isNotEmpty())
            add(
                JMenu("Unhide Class").apply {
                  optionPanelState.classesToHide.forEach { className ->
                    add(
                        JMenuItem(className).apply {
                          addActionListener {
                            if (optionPanelState.classesToHide.remove(className)) onChange()
                          }
                        }
                    )
                  }
                }
            )
        if (optionPanelState.showLibraries && optionPanelState.librariesToHide.isNotEmpty())
            add(
                JMenu("Unhide Library").apply {
                  optionPanelState.librariesToHide.forEach { libName ->
                    add(
                        JMenuItem(libName).apply {
                          addActionListener {
                            if (optionPanelState.librariesToHide.remove(libName)) onChange()
                          }
                        }
                    )
                  }
                }
            )
        if (!optionPanelState.showLibraries)
            add(
                JMenuItem("Show Libraries").apply {
                  addActionListener {
                    optionPanelState.showLibraries = true
                    onChange()
                  }
                }
            )
        addSeparator()
        add(
            JMenuItem("Add Class from Project").apply {
              addActionListener {
                ProjectClassChooser.openProjectClassDialog(project)?.let { classname ->
                  if (optionPanelState.classesToAnalyze.add(classname)) onChange()
                }
              }
            }
        )
      }

  // Generischer Helfer für Checkbox-Menüs, die auf Boolean-Properties binden
  private fun <T> buildCheckboxMenu(
      title: String,
      target: T,
      items: List<Pair<String, KMutableProperty1<T, Boolean>>>,
      onChange: () -> Unit,
  ): JMenu =
      JMenu(title).apply {
        items.forEach { (label, prop) ->
          val item =
              JCheckBoxMenuItem(label, prop.get(target)).apply {
                addItemListener { e ->
                  val selected = (e.stateChange == ItemEvent.SELECTED)
                  prop.set(target, selected)
                  onChange()
                }
              }
          add(item)
        }
      }

  private class EditDialog(dialogTitle: String, content: String) : DialogWrapper(true) {
    private val textArea =
        JBTextArea(content, 10, 50).apply {
          lineWrap = true
          wrapStyleWord = true
          emptyText.text = ""
        }

    init {
      title = dialogTitle
      init()
    }

    override fun createCenterPanel(): JComponent = JBScrollPane(textArea)

    fun result(): String = textArea.text.trimEnd()
  }

  fun buildTextAreaPopup(
      dialogTitle: String,
      content: String,
      onTitleChanged: (String) -> Unit,
  ): JPopupMenu =
      JPopupMenu().apply {
        add(
            JMenuItem(dialogTitle).apply {
              addActionListener {
                val dialog = EditDialog(dialogTitle, content)
                if (dialog.showAndGet()) {
                  onTitleChanged(dialog.result())
                }
              }
            }
        )
      }

  fun buildListPopup(
      dialogTitle: String,
      content: String,
      onTextChanged: (String) -> Unit,
  ): JPopupMenu =
      JPopupMenu().apply {
        add(
            JMenuItem("edit").apply {
              addActionListener {
                val textField = JTextField(content)
                Messages.showTextAreaDialog(textField, dialogTitle, "")
                if (textField.text != content) onTextChanged(textField.text.trim())
              }
            }
        )
      }

  private fun buildClassPopup(className: String): JPopupMenu =
      JPopupMenu().apply {
        add(
            JMenuItem("Focus Class").apply {
              addActionListener {
                optionPanelState.classesToAnalyze.clear()
                optionPanelState.classesToAnalyze.add(className)
                optionPanelState.classesToHide.remove(className)
                onChange()
              }
            }
        )
        addSeparator()
        add(
            JMenuItem("Jump to Source").apply {
              addActionListener { jumpToClassInSources(project, className) }
            }
        )
        addSeparator()
        add(
            JMenuItem("Make to Marker").apply {
              addActionListener { if (optionPanelState.markerClasses.add(className)) onChange() }
            }
        )
        add(
            JMenuItem("Hide Class").apply {
              addActionListener {
                val toHide =
                    optionPanelState.classesInDiagram
                        .filter { it.substringAfterLast('.', it) == className }
                        .filter { !optionPanelState.classesToHide.contains(it) }
                // TODO: Chooser einbauen
                if (toHide.isNotEmpty()) {
                  optionPanelState.classesToAnalyze.removeAll(toHide)
                  optionPanelState.classesToHide.addAll(toHide)
                  onChange()
                }
              }
            }
        )
      }

  private fun buildLibraryPopup(libraryName: String): JPopupMenu =
      JPopupMenu().apply {
        add(
            JMenuItem("Hide Library").apply {
              addActionListener {
                if (optionPanelState.librariesToHide.add(libraryName)) onChange()
              }
            }
        )
        addSeparator()
        add(
            JMenuItem("Jump to Project view").apply {
              addActionListener {
                optionPanelState.libraryPaths
                    .firstOrNull { it.replace('\\', '/').endsWith("/$libraryName") }
                    ?.let { libraryPath ->
                      JumpToLibrary.openLibraryInProjectView(project, libraryPath)
                    }
              }
            }
        )
      }
}

class ImageTransferable(val image: Image) : Transferable {
  override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)

  override fun isDataFlavorSupported(flavor: DataFlavor) = getTransferDataFlavors().contains(flavor)

  override fun getTransferData(flavor: DataFlavor) =
      if (isDataFlavorSupported(flavor)) image else ""
}

private class SvgTransferable(private val svg: String) : Transferable {
  override fun getTransferDataFlavors(): Array<DataFlavor> =
      arrayOf(DataFlavor("image/svg+xml; class=java.io.InputStream", "SVG (stream)"))

  override fun isDataFlavorSupported(flavor: DataFlavor) = getTransferDataFlavors().contains(flavor)

  override fun getTransferData(flavor: DataFlavor) =
      if (!isDataFlavorSupported(flavor)) ""
      else ByteArrayInputStream(svg.toByteArray(StandardCharsets.UTF_8))
}
