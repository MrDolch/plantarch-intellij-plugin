package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.intellij.openapi.ide.CopyPasteManager
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

class PngViewerPanel(puml: String) : JPanel() {
  private var image: BufferedImage = renderPng(puml)
  var svg: String = renderSvg(puml)

  init {
    preferredSize = Dimension(image.width, image.height)
    installPopupMenu(this) { image }
  }

  fun updatePanel(puml: String) {
    image = renderPng(puml)
    svg = renderSvg(puml)
    preferredSize = Dimension(image.width, image.height)
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
}

fun installPopupMenu(diagramPanel: JPanel, getImage: () -> Image) {
  val popup = JPopupMenu().apply {
    add(JMenuItem("Copy to Clipboard").apply {
      addActionListener {
        val image = getImage()
        val transferable = ImageTransferable(image)
        CopyPasteManager.getInstance().setContents(transferable)
      }
    })
  }

  diagramPanel.componentPopupMenu = popup
}

private class ImageTransferable(val image: Image) : Transferable {
  override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
  override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
  override fun getTransferData(flavor: DataFlavor) = image
}