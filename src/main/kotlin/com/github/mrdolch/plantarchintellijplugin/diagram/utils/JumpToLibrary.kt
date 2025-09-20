package com.github.mrdolch.plantarchintellijplugin.diagram.utils

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager

object JumpToLibrary {
  fun openLibraryInProjectView(project: Project, rawPath: String) {
    val norm = rawPath.replace('\\', '/')
    val localFs = LocalFileSystem.getInstance()
    val jarFs = JarFileSystem.getInstance()

    // 1) Lokale Datei/Ordner finden
    val local = localFs.findFileByPath(norm) ?: return

    // 2) Falls .jar → auf den JAR-Root (jar://…!/) mappen,
    //    sonst Ordner/Datei direkt verwenden.
    val target: VirtualFile =
        when {
          local.isDirectory -> local
          FileTypeRegistry.getInstance().isFileOfType(local, ArchiveFileType.INSTANCE) ->
              jarFs.getJarRootForLocalFile(
                  local
              )
          else -> local
        } ?: return

    // 3) PSI-Element besorgen und im Project View selektieren
    val psi =
        PsiManager.getInstance(project).let {
          if (target.isDirectory) it.findDirectory(target) else it.findFile(target)
        } ?: return

    ApplicationManager.getApplication().invokeLater {
      val pv = ProjectView.getInstance(project)
      ToolWindowManager.Companion.getInstance(project)
          .getToolWindow(ToolWindowId.PROJECT_VIEW)
          ?.activate(
              {
                try {
                  pv.changeView(ProjectViewPane.ID)
                } catch (_: Throwable) {}
                pv.selectPsiElement(psi, true)
              },
              true,
          )
    }
  }
}