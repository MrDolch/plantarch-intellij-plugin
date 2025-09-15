package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.github.mrdolch.plantarchintellijplugin.diagram.view.JumpToSource.jumpToClassInSources
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import kotlin.math.max
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader

class PngViewerPanel(
    puml: String,
    val project: Project,
    val optionPanelState: OptionPanelState,
    val onChange: (String) -> Unit,
) : JPanel(), Serializable {
  private lateinit var image: BufferedImage
  lateinit var svg: String
  lateinit var puml: String
  private lateinit var classNameBounds: Map<String, Rectangle>

  private val generalPopup by lazy { buildGeneralPopup() }

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
            classNameBounds.filter { it.value.contains(e.point) }.forEach { onChange(it.key) }
          }
        }
    )
  }

  fun updatePanel(puml: String) {
    this.puml = puml
    svg = renderSvg(puml)
    setPlantumlLimitSize()
    image = renderPng(puml)
    preferredSize = Dimension(image.width, image.height)
    classNameBounds =
        collectSvgClassBoxes(svg)
            .filter {
              optionPanelState.hiddenClasses.any { c -> c.endsWith(it.text) } ||
                  optionPanelState.hiddenContainers.contains(it.text)
            }
            .associate { it.asEntry() }
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
            val popup =
                when {
                  classUnderMouse == null -> generalPopup
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
      }

  private fun buildClassPopup(className: String): JPopupMenu =
      JPopupMenu().apply {
        add(
            JMenuItem("Jump to Source").apply {
              addActionListener { jumpToClassInSources(project, className) } // <— NEU
            }
        )
        add(
            JMenuItem("Copy class name").apply {
              addActionListener { CopyPasteManager.copyTextToClipboard(className) }
            }
        )
        add(
            JMenuItem("Focus class").apply {
              addActionListener { onChange(className) } // oder eigene Fokus-Action triggern
            }
        )
        addSeparator()
        add(
            JMenuItem("Hide class").apply {
              addActionListener {
                // Beispiel: Klasse zu hiddenClassesSelected hinzufügen und neu rendern
                optionPanelState.hiddenClassesSelected += className
              }
            }
        )
      }
  private fun buildLibraryPopup(className: String): JPopupMenu =
      JPopupMenu().apply {
        add(
            JMenuItem("Hide Library").apply {
              addActionListener {
                // Beispiel: Klasse zu hiddenClassesSelected hinzufügen und neu rendern
                optionPanelState.hiddenClassesSelected += className
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
