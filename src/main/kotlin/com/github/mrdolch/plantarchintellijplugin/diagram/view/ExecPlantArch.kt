package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.github.mrdolch.plantarchintellijplugin.asm.Asm
import com.github.mrdolch.plantarchintellijplugin.asm.Parameters
import com.github.mrdolch.plantarchintellijplugin.asm.Result
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

object ExecPlantArch {

  object TaskLock {
    val isRunning = AtomicBoolean(false)
    val isWaiting = AtomicBoolean(false)

    @Volatile var pendingJob: Parameters? = null
  }

  fun runAnalyzerBackgroundTask(
      project: Project,
      optionPanelState: OptionPanelState,
      skipCompile: Boolean,
      onResult: (Result) -> Unit = {},
  ) {
    val asmParameters: Parameters = createRenderParameters(optionPanelState)
    if (!TaskLock.isRunning.compareAndSet(false, true)) {
      // Bereits laufend → Task nicht nochmal starten
      TaskLock.pendingJob = asmParameters
      TaskLock.isWaiting.set(true)
      return
    }

    ProgressManager.getInstance()
        .run(
            object : Task.Backgroundable(project, "Running analysis", false) {
              override fun run(indicator: ProgressIndicator) {
                try {
                  if (!skipCompile) {
                    indicator.text = "Compiling..."
                    val success = compileSynchronously(project)
                    if (!success) {
                      NotificationGroupManager.getInstance()
                        .getNotificationGroup("PlantArch")
                        .createNotification(
                          "Build failed",
                          "Project must be compiled before analysis.",
                          NotificationType.ERROR
                        ).notify(project)
                    }
                  }

                  indicator.text = "Analyzing classes..."
                  val resultPlantuml = Asm.renderDiagram(asmParameters)

                  indicator.text = "Updating UI..."
                  val plantuml =
                      resultPlantuml.plantUml +
                          "\n${OptionPanelState.Companion.MARKER_STARTCONFIG}\n${optionPanelState.toYaml()}\n${OptionPanelState.Companion.MARKER_ENDCONFIG}"
                  writeDiagram(plantuml, optionPanelState.targetPumlFile)
                  onResult(resultPlantuml)
                } finally {
                  TaskLock.isRunning.set(false) // Task wieder freigeben
                  // Falls ein weiterer Job inzwischen ansteht → sofort starten
                  if (TaskLock.isWaiting.compareAndSet(true, false)) {
                    TaskLock.pendingJob?.let {
                      TaskLock.pendingJob = null
                      runAnalyzerBackgroundTask(project, optionPanelState, true, onResult)
                    }
                  }
                }
              }
            }
        )
  }

  private fun writeDiagram(
      plantuml: String,
      diagramFilename: String,
  ) {
    val application = ApplicationManager.getApplication()
    val virtualFileManager = VirtualFileManager.getInstance()
    application.invokeLater {
      virtualFileManager.refreshAndFindFileByUrl("file://$diagramFilename")?.let {
        application.runWriteAction { VfsUtil.saveText(it, plantuml) }
      }
    }
  }

  private fun compileSynchronously(project: Project): Boolean {
    val future = CompletableFuture<Boolean>()
    ApplicationManager.getApplication().invokeLater {
      CompilerManager.getInstance(project).make { aborted, errors, _, _ ->
        future.complete(!aborted && errors == 0)
      }
    }
    return future.get()
  }

  fun createRenderParameters(optionPanelState: OptionPanelState): Parameters =
      Parameters(
          title = optionPanelState.title,
          caption = optionPanelState.description,
          projectName = optionPanelState.projectName,
          moduleName = optionPanelState.moduleName,
          libraryPaths = optionPanelState.libraryPaths.map { Path.of(it) }.toSet(),
          classPaths = optionPanelState.classPaths.map { Path.of(it) }.toSet(),
          showPackages = optionPanelState.showPackages,
          classesToAnalyze = optionPanelState.classesInFocusSelected,
          classesToHide = optionPanelState.hiddenClassesSelected,
          librariesToHide = optionPanelState.hiddenContainersSelected,
          targetPumlFile = optionPanelState.targetPumlFile,
          showUseByMethodNames = optionPanelState.showUseByMethodNames,
          showLibraries = true,
          markerClasses = optionPanelState.markerClassesSelected,
          plantumlInlineOptions = optionPanelState.plamtumlInlineOptions,
      )
}
