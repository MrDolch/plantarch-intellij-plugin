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

data class SvgLabel(val kind: Kind, val text: String, val rect: Rectangle) {
  enum class Kind {
    TITLE,
    CAPTION,
  }
}

fun collectSvgClassBoxes(svg: String): List<SvgClassBox> {
  val (doc, xp) = buildDocAndXPath(svg)

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

    // 2) Klassennamen finden: alle <text> bis zur ersten <line>; letzter nicht-«…» ist der Name
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
          val collected = ArrayList<Element>()
          var c: Node? = g.firstChild
          fun collectTexts(n: Node) {
            if (n is Element && n.localName == "text") collected += n
            var k = n.firstChild
            while (k != null) {
              collectTexts(k)
              k = k.nextSibling
            }
          }
          while (c != null && c != firstLine) {
            if (c is Element) collectTexts(c)
            c = c.nextSibling
          }
          collected
        } else {
          allDescTextsIn(g)
        }

    val titleTextEl: Element? =
        textsBeforeLine.lastOrNull { el ->
          val t = el.textContent.orEmpty().trim()
          t.isNotEmpty() && !t.startsWith("«")
        } ?: textsBeforeLine.lastOrNull()

    val title = titleTextEl?.textContent?.trim().orEmpty()
    if (title.isEmpty()) continue

    result += SvgClassBox(text = title, rect = rect)
  }
  return result
}

/** Hilfen * */
private fun buildDocAndXPath(svg: String): Pair<org.w3c.dom.Document, javax.xml.xpath.XPath> {
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
  return doc to xp
}

/** Title/Caption: volle Breite, Höhe aus Text-Bounds (oder <rect>, falls vorhanden) */
fun collectSvgTitleAndCaption(svg: String): List<SvgLabel> {
  val (doc, xp) = buildDocAndXPath(svg)

  val svgWidth = readSvgWidth(doc) ?: 0.0

  fun selectGroup(classToken: String): Element? =
      (xp.compile("//svg:g[contains(concat(' ', normalize-space(@class), ' '), ' $classToken ')]")
          .evaluate(doc, XPathConstants.NODE) as? Element)

  val out = mutableListOf<SvgLabel>()
  listOf("title" to SvgLabel.Kind.TITLE, "caption" to SvgLabel.Kind.CAPTION).forEach { (cls, kind)
    ->
    selectGroup(cls)?.let { g ->
      estimateGroupYBounds(g)?.let { (text, yTop, height) ->
        val rect = Rectangle(0, yTop.toInt(), max(1.0, svgWidth).toInt(), max(1.0, height).toInt())
        out += SvgLabel(kind, text, rect)
      }
    }
  }
  return out
}

/** Nur Y-Top + Höhe bestimmen (X wird ignoriert). */
/** Nur Y-Top + Höhe bestimmen (X wird ignoriert). */
private fun estimateGroupYBounds(g: Element): Triple<String, Double, Double>? {
  // 1) Falls im group ein <rect> existiert, nehmen wir dessen y/height (häufig bei Themes)
  fun Element.attrD(name: String) =
      getAttribute(name).takeIf { it.isNotBlank() }?.removeSuffix("px")?.toDoubleOrNull()

  var c: Node? = g.firstChild
  while (c != null) {
    if (c is Element && c.localName == "rect") {
      val y = c.attrD("y")
      val h = c.attrD("height")
      if (y != null && h != null) {
        val text = g.textContent.orEmpty().trim()
        return Triple(text, y, h)
      }
      // Falls y/height fehlen, nicht aussteigen, sondern weiter nach Text-Bounds suchen
    }
    c = c.nextSibling
  }

  // 2) Fallback: aus <text>/<tspan> die Y-Bounds schätzen
  val texts = ArrayList<Element>()
  fun walk(n: Node) {
    if (n is Element && n.localName == "text") texts += n
    var k = n.firstChild
    while (k != null) {
      walk(k)
      k = k.nextSibling
    }
  }
  walk(g)
  if (texts.isEmpty()) return null

  fun Element.fontSize(): Double? {
    getAttribute("font-size")
        .takeIf { it.isNotBlank() }
        ?.removeSuffix("px")
        ?.toDoubleOrNull()
        ?.let {
          return it
        }
    val style = getAttribute("style")
    if (style.isNotBlank()) {
      style.split(';').forEach {
        val kv = it.split(':', limit = 2).map(String::trim)
        val k = kv.getOrNull(0) ?: ""
        val v = kv.getOrNull(1) ?: ""
        if (k == "font-size") return v.removeSuffix("px").toDoubleOrNull()
      }
    }
    return null
  }

  var minYTop = Double.POSITIVE_INFINITY
  var maxYBottom = Double.NEGATIVE_INFINITY

  texts.forEach { t ->
    val yBase =
        t.getAttribute("y").takeIf { it.isNotBlank() }?.removeSuffix("px")?.toDoubleOrNull() ?: 0.0
    val fs = t.fontSize() ?: 12.0
    val yTop = yBase - 0.8 * fs // grobe Annäherung: Baseline – Ascender
    val yBottom = yTop + fs
    minYTop = kotlin.math.min(minYTop, yTop)
    maxYBottom = kotlin.math.max(maxYBottom, yBottom)

    var s: Node? = t.firstChild
    while (s != null) {
      if (s is Element && s.localName == "tspan") {
        val tyBase =
            s.getAttribute("y").takeIf { it.isNotBlank() }?.removeSuffix("px")?.toDoubleOrNull()
                ?: yBase
        val tfs =
            s.getAttribute("font-size")
                .takeIf { it.isNotBlank() }
                ?.removeSuffix("px")
                ?.toDoubleOrNull() ?: fs
        val tt = tyBase - 0.8 * tfs
        minYTop = kotlin.math.min(minYTop, tt)
        maxYBottom = kotlin.math.max(maxYBottom, tt + tfs)
      }
      s = s.nextSibling
    }
  }

  if (!minYTop.isFinite() || !maxYBottom.isFinite()) return null
  val text = g.textContent.orEmpty().trim()
  return Triple(text, minYTop, maxYBottom - minYTop)
}

/** Breite aus <svg width="…"> oder viewBox="minx miny w h" lesen. */
private fun readSvgWidth(doc: org.w3c.dom.Document): Double? {
  val svgEl = doc.documentElement ?: return null
  val widthAttr = svgEl.getAttribute("width").takeIf { it.isNotBlank() }
  widthAttr?.removeSuffix("px")?.toDoubleOrNull()?.let {
    return it
  }

  val viewBox = svgEl.getAttribute("viewBox").takeIf { it.isNotBlank() }?.trim()
  if (viewBox != null) {
    val parts = viewBox.split(Regex("\\s+"))
    if (parts.size == 4) return parts[2].toDoubleOrNull()
  }
  return null
}
