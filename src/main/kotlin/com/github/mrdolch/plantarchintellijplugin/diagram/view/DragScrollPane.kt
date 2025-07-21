package com.github.mrdolch.plantarchintellijplugin.diagram.view

import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

class DragScrollPane(view: Component) : JScrollPane(view) {
  private var lastPoint: Point? = null

  init {
    val dragAdapter = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        lastPoint = SwingUtilities.convertPoint(e.component, e.point, viewport.view)
        cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
      }

      override fun mouseReleased(e: MouseEvent) {
        lastPoint = null
        cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
      }

      override fun mouseDragged(e: MouseEvent) {
        val viewPos = viewport.viewPosition
        val newPoint = SwingUtilities.convertPoint(e.component, e.point, viewport.view)
        val dx = lastPoint!!.x - newPoint.x
        val dy = lastPoint!!.y - newPoint.y

        val newX = (viewPos.x + dx).coerceIn(0, view.width - viewport.width)
        val newY = (viewPos.y + dy).coerceIn(0, view.height - viewport.height)

        viewport.viewPosition = Point(newX, newY)
      }
    }

    viewport.view.addMouseListener(dragAdapter)
    viewport.view.addMouseMotionListener(dragAdapter)
  }
}