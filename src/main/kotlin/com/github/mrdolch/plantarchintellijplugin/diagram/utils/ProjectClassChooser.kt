package com.github.mrdolch.plantarchintellijplugin.diagram.utils

import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

object ProjectClassChooser {
  fun openProjectClassDialog(project: Project): String? {
    val runtimeScope =
        ModuleManager.getInstance(project)
            .modules
            .map { GlobalSearchScope.moduleRuntimeScope(it, false) }
            .reduce { a, b -> a.uniteWith(b) }

    val chooser =
        TreeClassChooserFactory.getInstance(project)
            .createNoInnerClassesScopeChooser(
                "Select Class",
                runtimeScope,
                { psiClass -> psiClass.containingClass == null },
                null,
            )
    chooser.showDialog()
    return chooser.selected?.qualifiedName
  }
}
