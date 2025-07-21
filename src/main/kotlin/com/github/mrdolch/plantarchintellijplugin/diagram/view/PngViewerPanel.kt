package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import net.sourceforge.plantuml.SourceStringReader
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.JPanel

class PngViewerPanel(file: VirtualFile) : JPanel() {
  private var image: BufferedImage = renderPng(file)

  init {
    preferredSize = Dimension(image.width, image.height)
  }

  fun updatePanel(file: VirtualFile) {
    image = renderPng(file)
    preferredSize = Dimension(image.width, image.height)
  }

  fun renderPng(umlSource: VirtualFile): BufferedImage {
    val reader = SourceStringReader(umlSource.readText())
    val outputStream = ByteArrayOutputStream()
    reader.outputImage(outputStream)
    return ImageIO.read(outputStream.toByteArray().inputStream())
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    g.drawImage(image, 0, 0, this)
  }
}