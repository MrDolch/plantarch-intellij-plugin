package com.github.mrdolch.plantarchintellijplugin.diagram.view

import java.awt.Rectangle
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.math.max
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

data class SvgClassBox(val text: String, val rect: Rectangle) {
  fun asEntry() = text to rect
}

fun collectSvgClassBoxes(svg: String): List<SvgClassBox> {
  val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
  val doc = dbf.newDocumentBuilder().parse(InputSource(StringReader(svg)))

  val xp =
      XPathFactory.newInstance().newXPath().apply {
        namespaceContext =
            object : NamespaceContext {
              override fun getNamespaceURI(prefix: String?) =
                  if (prefix == "svg") "http://www.w3.org/2000/svg" else XMLConstants.NULL_NS_URI

              override fun getPrefix(namespaceURI: String?) =
                  if (namespaceURI == "http://www.w3.org/2000/svg") "svg" else null

              override fun getPrefixes(namespaceURI: String?) =
                  (getPrefix(namespaceURI)?.let { listOf(it) } ?: emptyList()).iterator()
            }
      }

  // Alle Gruppen, die eine Klasse/Entity darstellen
  val groups =
      xp.compile("//svg:g[contains(concat(' ', normalize-space(@class), ' '), ' entity ')]")
          .evaluate(doc, XPathConstants.NODESET) as NodeList

  fun Element.attrD(name: String): Double? =
      getAttribute(name).takeIf { it.isNotBlank() }?.toDoubleOrNull()

  fun firstChildByLocalName(el: Element, local: String): Element? {
    var c = el.firstChild
    while (c != null) {
      if (c is Element && c.localName == local) return c
      c = c.nextSibling
    }
    return null
  }

  fun allDescTextsIn(docOrderRoot: Element): List<Element> {
    val out = ArrayList<Element>()
    fun walk(n: Node) {
      if (n is Element && n.localName == "text") out += n
      var c = n.firstChild
      while (c != null) {
        walk(c)
        c = c.nextSibling
      }
    }
    walk(docOrderRoot)
    return out
  }

  val result = ArrayList<SvgClassBox>(groups.length)
  for (i in 0 until groups.length) {
    val g = groups.item(i) as Element

    // 1) Box-Rechteck (erster <rect> im <g>)
    val rectEl = firstChildByLocalName(g, "rect") ?: continue
    val x = rectEl.attrD("x") ?: continue
    val y = rectEl.attrD("y") ?: continue
    val w = rectEl.attrD("width") ?: continue
    val h = rectEl.attrD("height") ?: continue
    val rect = Rectangle(x.toInt(), y.toInt(), max(1.0, w).toInt(), max(1.0, h).toInt())

    // 2) Klassennamen finden:
    //    - alle <text> im <g> in Dokumentreihenfolge
    //    - bis zur ersten <line> (Titel-Trennlinie) sammeln
    //    - der letzte gesammelte <text> ist der Name (Stereotype stehen davor)
    var firstLine: Element? = null
    run {
      var c: Node? = g.firstChild
      while (c != null) {
        if (c is Element && c.localName == "line") {
          firstLine = c
          break
        }
        c = c.nextSibling
      }
    }
    val textsBeforeLine: List<Element> =
        if (firstLine != null) {
          // durch Kinder laufen und Texte bis firstLine einsammeln (inkl. verschachtelte tspans)
          val collected = ArrayList<Element>()
          var c: Node? = g.firstChild
          while (c != null && c != firstLine) {
            if (c is Element) collected += allDescTextsIn(c)
            c = c.nextSibling
          }
          collected
        } else {
          // Fallback: nimm die ersten Textknoten im <g>
          allDescTextsIn(g)
        }

    // Letzten Text vor der Linie wählen; Stereotype «…» rausfiltern, falls vorhanden
    val titleTextEl: Element? =
        textsBeforeLine.lastOrNull { el ->
          val t = el.textContent.orEmpty().trim()
          t.isNotEmpty() && !t.startsWith("«") // Stereotype ignorieren
        } ?: textsBeforeLine.lastOrNull()

    val title = titleTextEl?.textContent?.trim().orEmpty()
    if (title.isEmpty()) continue

    result += SvgClassBox(text = title, rect = rect)
  }
  return result
}
