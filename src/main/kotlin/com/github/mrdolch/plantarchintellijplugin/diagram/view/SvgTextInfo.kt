package com.github.mrdolch.plantarchintellijplugin.diagram.view

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.awt.Rectangle
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

data class SvgTextInfo(
    val text: String,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
) {
  fun asEntry() = text to Rectangle(x.toInt(), y.toInt(), w.toInt(), h.toInt())
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
    // Strip evtl. Einheiten wie "px"
    val num = token.replace(Regex("[a-zA-Z%]+$"), "")
    return num.toDoubleOrNull()
  }

  val out = ArrayList<SvgTextInfo>(nodes.length)
  for (i in 0 until nodes.length) {
    val el = nodes.item(i) as Element
    val text = el.textContent.orEmpty().trim()
    if (text.isEmpty()) continue

    val x = parseFirstNumber(el.attr("x") ?: el.findAttrUp("x")) ?: continue
    val y = parseFirstNumber(el.attr("y") ?: el.findAttrUp("y")) ?: continue
    val w = parseFirstNumber(el.attr("textLength")) ?: continue
    val h = parseFirstNumber(el.attr("font-size")) ?: continue

    out += SvgTextInfo(text = text, x = x, y = y - h, w = w, h = h)
  }
  return out
}
