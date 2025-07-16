package com.github.mrdolch.plantarchintellijplugin.diagram

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiDocumentManager

class ActionStartNewDiagram : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
      val psiFile =
        PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? PsiClassOwner ?: return
      val className = psiFile.classes.firstOrNull()?.qualifiedName ?: return
      val module = ModuleUtil.findModuleForPsiElement(psiFile) ?: return
      val jobParams = createIdeaRenderJob(module, className)
      ExecPlantArch.runAnalyzerBackgroundTask(jobParams, false)
    }
  }
}