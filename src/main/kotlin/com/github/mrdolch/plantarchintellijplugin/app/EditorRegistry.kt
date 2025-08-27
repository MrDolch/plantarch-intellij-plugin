package com.github.mrdolch.plantarchintellijplugin.app

import com.github.mrdolch.plantarchintellijplugin.diagram.view.DiagramEditor
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

object EditorRegistry {
  val map = WeakHashMap<VirtualFile, DiagramEditor>()

  fun registerEditor(diagramFile: VirtualFile, editor: DiagramEditor) {
    map[diagramFile] = editor
  }

  fun getEditor(diagramFile: VirtualFile): DiagramEditor? {
    return map[diagramFile]
  }
}
