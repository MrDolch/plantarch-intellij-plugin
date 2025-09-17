package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.navigation.NavigationItem
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

object JumpToSource {
  fun jumpToClassInSources(project: Project, classNameOrFqn: String) {
    // SCOPE: nur Projektinhalte (keine Libraries)
    val scope = GlobalSearchScope.projectScope(project)

    // 1) FQN? -> direkt suchen + auf Source-Datei filtern
    if ('.' in classNameOrFqn) {
      val cls = JavaPsiFacade.getInstance(project).findClass(classNameOrFqn, scope)
      if (cls != null && elementIsInSources(project, cls)) {
        navigateToElement(project, cls)
      } else {
        notifyNotFound(project, "$classNameOrFqn (not in sources)")
      }
      return
    }

    // 2) Kurzname -> Kandidaten suchen, dann auf Source-Dateien filtern
    val all = PsiShortNamesCache.getInstance(project).getClassesByName(classNameOrFqn, scope)
    val candidates = all.filter { elementIsInSources(project, it) }

    when (candidates.size) {
      0 -> notifyNotFound(project, "$classNameOrFqn (not in sources)")
      1 -> navigateToElement(project, candidates.first())
      else -> {
        val renderer =
            object : PsiElementListCellRenderer<PsiElement>() {
              override fun getElementText(element: PsiElement): String =
                  (element as? NavigationItem)?.presentation?.presentableText ?: element.toString()

              override fun getContainerText(element: PsiElement, name: String): String? =
                  (element as? NavigationItem)?.presentation?.locationString

              override fun getIconFlags(): Int = 0
            }

        runCatching {
          JBPopupFactory.getInstance()
              .createPopupChooserBuilder(candidates.toList())
              .setTitle("Jump to Source: $classNameOrFqn")
              .setRenderer(renderer)
              .setItemChosenCallback { navigateToElement(project, it) }
              .createPopup()
              .showInFocusCenter()
        }
      }
    }
  }

  private fun elementIsInSources(project: Project, element: PsiElement): Boolean {
    // IMMER über das navigationElement gehen (führt bei Kotlin zu .kt statt Light-Class)
    val vFile: VirtualFile? = element.navigationElement.containingFile?.virtualFile
    if (vFile == null) return false
    val index = ProjectFileIndex.getInstance(project)
    // Nur echte Source-Inhalte, keine Libraries/Decompiled/JAR
    return index.isInSourceContent(vFile) && !index.isInLibraryClasses(vFile)
  }

  private fun navigateToElement(project: Project, element: PsiElement) {
    val nav = element.navigationElement
    val vFile = nav.containingFile?.virtualFile
    if (vFile != null) {
      FileEditorManager.getInstance(project)
          .openTextEditor(OpenFileDescriptor(project, vFile, nav.textOffset), true)
    } else {
      (nav as? Navigatable)?.navigate(true)
    }
  }

  private fun notifyNotFound(project: Project, name: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("PlantArch")
        .createNotification("Class not found in sources: $name", NotificationType.WARNING)
        .notify(project)
  }
}
