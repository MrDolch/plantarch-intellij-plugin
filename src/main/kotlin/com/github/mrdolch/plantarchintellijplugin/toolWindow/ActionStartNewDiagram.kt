package com.github.mrdolch.plantarchintellijplugin.toolWindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiDocumentManager
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import tech.dolch.plantarch.cmd.IdeaRenderJob
import tech.dolch.plantarch.cmd.OptionPanelState
import tech.dolch.plantarch.cmd.RenderJob
import java.io.File
import kotlin.io.path.absolutePathString

class ActionStartNewDiagram : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
      val psiFile =
        PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? PsiClassOwner ?: return
      val className = psiFile.classes.firstOrNull()?.qualifiedName ?: return
      val module = ModuleUtil.findModuleForPsiElement(psiFile) ?: return

      CompilerManager.getInstance(project).make { aborted, errors, warnings, compileContext ->
        if (!aborted && errors == 0) {
          val jobParams = createIdeaRenderJob(project, module, className)
          ExecPlantArch.executePlantArch(jobParams)
        }
      }
    }
  }


}

fun createIdeaRenderJob(
  project: Project,
  module: Module,
  className: String
): IdeaRenderJob {
  val jobParams = IdeaRenderJob(
    projectName = project.name,
    moduleName = module.name,
    classPaths = module.getClasspath(),
    optionPanelState = OptionPanelState(
      targetPumlFile = File.createTempFile(FILE_PREFIX_DEPENDENCY_DIAGRAM, ".puml").absolutePath,
      showPackages = true,
      flatPackages = false,
      classesInFocus = listOf(className),
      classesInFocusSelected = listOf(className),
      hiddenContainers = listOf("jrt"),
      hiddenContainersSelected = listOf("jrt"),
      hiddenClasses = listOf(),
      hiddenClassesSelected = listOf(),
    ),
    renderJob = RenderJob(
      classDiagrams = RenderJob.ClassDiagramParams(
        title = "Dependencies of ${className.replaceBeforeLast(".", "").substring(1)}",
        description = "",
        classesToAnalyze = listOf(className),
        containersToHide = listOf("jrt"),
      ),
    ),
  )
  return jobParams
}

fun Module.getClasspath(): ImmutableSet<String> {
  val classpath = mutableSetOf("plantarch-0.1.8-launcher.jar")
  // 2. Abhängigkeiten (Libraries, andere Module)
  ModuleRootManager.getInstance(this)
    .orderEntries()
    .productionOnly()
    .classes()
    .roots
    .map { File(it.path).toPath() }
    .map { it.absolutePathString() }
    .map { it.removeSuffix("!") }
    .forEach { classpath.add(it) }
  // 1. Eigene kompilierten Klassen
  CompilerModuleExtension.getInstance(this)?.compilerOutputPath?.let {
    it.toNioPathOrNull()?.let { path -> classpath.add(path.absolutePathString()) }
  }
  return classpath.toImmutableSet()
}
