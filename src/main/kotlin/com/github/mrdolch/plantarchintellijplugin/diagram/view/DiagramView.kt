package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.charleskorn.kaml.Yaml
import com.github.mrdolch.plantarchintellijplugin.app.EditorRegistry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import tech.dolch.plantarch.cmd.IdeaRenderJob
import tech.dolch.plantarch.cmd.ShowPackages

const val MARKER_STARTCONFIG = "@startIdeaRenderJob"
const val MARKER_ENDCONFIG = "@endIdeaRenderJob"

object DiagramView {
  fun processDiagramViewUpdate(project: Project, job: IdeaRenderJob, rawPlantuml: String) {

    updateJobsOptionPanelState(job, rawPlantuml)

    var plantuml = rawPlantuml

    if (plantuml.lines().size < 5) return

    // remove transmission boilerplate
    plantuml = plantuml.lines().subList(1, plantuml.lines().size - 3).joinToString("\n")

    if (job.optionPanelState.showPackages == ShowPackages.FLAT) {
      plantuml = plantuml.replace("\\.(?=[A-Z])".toRegex(), "::")
        .replaceFirst("@startuml\n", "@startuml\nset namespaceSeparator ::")
    }
    if (job.optionPanelState.showPackages == ShowPackages.NONE) {
      plantuml = plantuml.replace("\\b(?:[a-z][a-zA-Z]*\\.)+(?=[A-Z])".toRegex(), "")
    }
    val globalOptions = """
      !pragma layout smetana
    """.trimIndent()
    plantuml = plantuml.replaceFirst("\n@enduml", "$globalOptions\n@enduml")

    val jobYaml = Yaml.default.encodeToString(IdeaRenderJob.serializer(), job)
    plantuml += "\n$MARKER_STARTCONFIG\n$jobYaml\n$MARKER_ENDCONFIG"

    // update Plugins-Panel
    openDiagramEditor(project, plantuml, job.optionPanelState.targetPumlFile)
  }

  private fun openDiagramEditor(project: Project, plantuml: String, diagramFile: String) {
    val application = ApplicationManager.getApplication()
    val virtualFileManager = VirtualFileManager.getInstance()
    val fileEditorManager = FileEditorManager.getInstance(project)
    application.invokeLater {
      virtualFileManager.refreshAndFindFileByUrl("file://$diagramFile")?.let {
        application.runWriteAction { VfsUtil.saveText(it, plantuml) }
        fileEditorManager.openFile(it, true, true)
        EditorRegistry.getEditor(it)
          ?.updateFields(plantuml)
      }
    }
  }

  private fun updateJobsOptionPanelState(
    job: IdeaRenderJob,
    rawPlantuml: String
  ) {
    val allVisibleClasses = getAllVisibleClasses(rawPlantuml, job)

    job.optionPanelState.classesInFocus = allVisibleClasses
    job.optionPanelState.classesInFocusSelected = job.renderJob.classDiagrams.classesToAnalyze

    job.optionPanelState.hiddenClasses = allVisibleClasses
    job.optionPanelState.hiddenClassesSelected = job.renderJob.classDiagrams.classesToHide

    job.optionPanelState.hiddenContainersSelected = job.renderJob.classDiagrams.containersToHide
    job.optionPanelState.hiddenContainers = rawPlantuml.lineSequence()
      .filter { it.startsWith("object ") }
      .map { it.split('"')[1] }
      .plus(job.optionPanelState.hiddenContainersSelected)
      .sorted().distinct().toList()
  }

  private fun getAllVisibleClasses(
    rawPlantuml: String,
    job: IdeaRenderJob
  ): List<String> {
    return rawPlantuml.lineSequence()
      .filter {
        it.startsWith("enum ")
            || it.startsWith("class ")
            || it.startsWith("abstract ")
            || it.startsWith("interface ")
      }
      .map { it.split(' ')[1] }
      .filter { !it.contains('<') }
      .plus(job.renderJob.classDiagrams.classesToAnalyze)
      .plus(job.renderJob.classDiagrams.classesToHide)
      .sorted().distinct().toList()
  }
}

