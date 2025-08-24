package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.intellij.openapi.ide.CopyPasteManager
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

class PngViewerPanel(puml: String, val onChange: (String) -> Unit) : JPanel() {
  private lateinit var image: BufferedImage
  lateinit var svg: String
  private lateinit var classNameBounds: Map<String, Rectangle>

  init {
    updatePanel(puml)
    installPopupMenu(this)
    addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        val overClass = classNameBounds.values.any { it.contains(e.point) }
        cursor = if (overClass) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
      }
    })
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        classNameBounds.filter { it.value.contains(e.point) }
          .forEach { onChange(it.key) }
      }
    })
  }

  fun updatePanel(puml: String) {
    image = renderPng(puml)
    svg = renderSvg(puml)
    preferredSize = Dimension(image.width, image.height)
    classNameBounds = collectSvgTexts(svg).associate { it.asEntry() }
  }

  fun renderPng(puml: String): BufferedImage = ImageIO.read(renderDiagram(puml, FileFormat.PNG).inputStream())
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

  fun installPopupMenu(diagramPanel: JPanel) {
    val popup = JPopupMenu().apply {
      add(JMenuItem("Copy SVG Image").apply {
        addActionListener {
          CopyPasteManager.getInstance().setContents(SvgTransferable(svg))
        }
      })
      add(JMenuItem("Copy SVG XML").apply {
        addActionListener {
          CopyPasteManager.copyTextToClipboard(svg)
        }
      })
      add(JMenuItem("Copy PNG Image").apply {
        addActionListener {
          CopyPasteManager.getInstance().setContents(ImageTransferable(image))
        }
      })
    }
    diagramPanel.componentPopupMenu = popup
  }
}

private class ImageTransferable(val image: Image) : Transferable {
  override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
  override fun isDataFlavorSupported(flavor: DataFlavor) = getTransferDataFlavors().contains(flavor)
  override fun getTransferData(flavor: DataFlavor) = if (isDataFlavorSupported(flavor)) image else ""
}

private class SvgTransferable(private val svg: String) : Transferable {
  override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(
    DataFlavor("image/svg+xml; class=java.io.InputStream", "SVG (stream)")
  )

  override fun isDataFlavorSupported(flavor: DataFlavor) = getTransferDataFlavors().contains(flavor)
  override fun getTransferData(flavor: DataFlavor) =
    if (isDataFlavorSupported(flavor)) ByteArrayInputStream(svg.toByteArray(StandardCharsets.UTF_8))
    else ""
}