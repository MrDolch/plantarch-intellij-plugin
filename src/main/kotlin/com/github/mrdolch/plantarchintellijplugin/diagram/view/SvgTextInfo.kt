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

data class SvgTextInfo(
    val text: String,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
) {
  fun asEntry() = text to Rectangle(x.toInt(), y.toInt(), max(1.0, w).toInt(), max(1.0, h).toInt())
}

fun collectSvgTexts(svg: String): List<SvgTextInfo> {
  val docFactory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
  val doc = docFactory.newDocumentBuilder().parse(InputSource(StringReader(svg)))

  val xPath =
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

  // Alle "Blatt"-Texte: tspans ODER texts ohne tspan-Kind
  val expr = xPath.compile("//svg:text[not(svg:tspan)] | //svg:text/svg:tspan")
  val nodes = expr.evaluate(doc, XPathConstants.NODESET) as NodeList

  fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotBlank() }

  fun Element.findAttrUp(name: String): String? {
    var n: Node? = this
    while (n is Element) {
      n.attr(name)?.let {
        return it
      }
      n = n.parentNode
    }
    return null
  }

  fun parseFirstNumber(listOrNull: String?): Double? {
    val s = listOrNull?.trim() ?: return null
    val token = s.split(Regex("[,\\s]+")).firstOrNull() ?: return null
    val num = token.replace(Regex("[a-zA-Z%]+$"), "")
    return num.toDoubleOrNull()
  }

  fun estimateWidth(text: String, fontSize: Double): Double {
    // Grobe Schätzung, wenn kein textLength vorhanden ist.
    // Erfahrungswert: ~0.6 * fontSize pro Zeichen (Monospace-artig).
    return text.length * fontSize * 0.6
  }

  val out = ArrayList<SvgTextInfo>(nodes.length)
  for (i in 0 until nodes.length) {
    val el = nodes.item(i) as Element
    val text = el.textContent.orEmpty().trim()
    if (text.isEmpty()) continue

    // x & y – inkl. Erben vom <text/> und Berücksichtigen von dy
    val x = parseFirstNumber(el.attr("x") ?: el.findAttrUp("x")) ?: continue

    val rawY = parseFirstNumber(el.attr("y") ?: el.findAttrUp("y"))
    val dy = parseFirstNumber(el.attr("dy")) ?: 0.0
    val yBaseline =
        when {
          rawY != null -> rawY + dy
          else -> continue
        }

    // font-size kann (häufig bei <tspan>) fehlen -> nach oben erben; sonst 14 als Fallback
    // (PlantUML-Default)
    val fontSize = parseFirstNumber(el.attr("font-size") ?: el.findAttrUp("font-size")) ?: 14.0

    // textLength kann fehlen -> nach oben erben oder schätzen
    val textLength =
        parseFirstNumber(el.attr("textLength") ?: el.findAttrUp("textLength"))
            ?: estimateWidth(text, fontSize)

    // Die Y-Koordinate in SVG-Texten ist die Baseline -> für Top-Left die Höhe abziehen
    val h = fontSize
    val w = textLength
    val yTop = yBaseline - h

    out += SvgTextInfo(text = text, x = x, y = yTop, w = w, h = h)
  }
  return out
}
