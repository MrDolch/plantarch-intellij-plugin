package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class DiagramEditorProvider : FileEditorProvider {
  companion object {
    const val FILE_EXTENSION = "plantarch"
    const val FILE_PREFIX_DEPENDENCY_DIAGRAM = "dependency-diagram-"
  }

  override fun accept(project: Project, file: VirtualFile) =
      file.extension == FILE_EXTENSION && file.name.startsWith("dependency-diagram-")

  override fun createEditor(project: Project, file: VirtualFile): DiagramEditor {
    return DiagramEditor(file)
  }

  override fun getEditorTypeId(): String = "plantarch-diagram-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
}
