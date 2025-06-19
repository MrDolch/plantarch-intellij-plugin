package com.github.mrdolch.plantarchintellijplugin.toolWindow

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.serialization.json.Json
import tech.dolch.plantarch.cmd.IdeaRenderJob
import java.lang.System.err
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun getProjectByName(projectName: String) =
  ProjectManager.getInstance().openProjects.first { it.name == projectName }

object ExecPlantArch {

  object TaskLock {
    val isRunning = AtomicBoolean(false)
    val isWaiting = AtomicBoolean(false)

    @Volatile
    var pendingJob: IdeaRenderJob? = null
  }

  private fun getRelevantJdk(project: Project): Sdk? = ProjectRootManager.getInstance(project).projectSdk

  fun runAnalyzerBackgroundTask(job: IdeaRenderJob, skipCompile: Boolean) {
    if (!TaskLock.isRunning.compareAndSet(false, true)) {
      // Bereits laufend → Task nicht nochmal starten
      TaskLock.pendingJob = job
      TaskLock.isWaiting.set(true)
      return
    }

    ProgressManager.getInstance()
      .run(object : Task.Backgroundable(getProjectByName(job.projectName), "Running analysis", false) {
        override fun run(indicator: ProgressIndicator) {
          try {
            if (!skipCompile) {
              indicator.text = "Compiling..."
              val success = compileSynchronously(project)
              if (!success) return
            }

            indicator.text = "Analyzing classes..."
            val renderSuccess = ExecPlantArch.executePlantArch(job)
            if (!renderSuccess) return

            indicator.text = "Updating UI..."
            ApplicationManager.getApplication().invokeLater {
              // Update your UI here
            }
          } finally {
            TaskLock.isRunning.set(false) // Task wieder freigeben
            // Falls ein weiterer Job inzwischen ansteht → sofort starten
            if (TaskLock.isWaiting.compareAndSet(true, false)) {
              TaskLock.pendingJob?.let {
                TaskLock.pendingJob = null
                runAnalyzerBackgroundTask(it, true)
              }
            }
          }
        }
      })
  }

  fun executePlantArch(job: IdeaRenderJob): Boolean {
    val commandLine = createCommandLine(job)
    val doneLatch = CountDownLatch(1)
    with(OSProcessHandler(commandLine)) {
      addProcessListener(RendererDoneListener(this, job) {
        doneLatch.countDown()
      })
      startNotify()
    }
    return doneLatch.await(30, TimeUnit.SECONDS)
  }

  private fun createCommandLine(job: IdeaRenderJob): GeneralCommandLine {
    val project = getProjectByName(job.projectName)
    val module = ModuleManager.getInstance(project).findModuleByName(job.moduleName)!!
    val relevantJdk = getRelevantJdk(module.project)!!
    val workingDir = Path.of(PathManager.getPluginsPath(), "plantarch-intellij-plugin", "lib").toString()
    return SimpleJavaParameters().apply {
      jdk = ProjectJdkTable.getInstance().findJdk(relevantJdk.name)
      mainClass = "tech.dolch.plantarch.cmd.MainKt"
      classPath.addAll(job.classPaths.stream().toList())
      workingDirectory = workingDir
      vmParametersList.add("-Xmx4g")
    }.toCommandLine().withCharset(StandardCharsets.UTF_8)
  }

  private fun compileSynchronously(project: Project): Boolean {
    val future = CompletableFuture<Boolean>()
    ApplicationManager.getApplication().invokeLater {
      CompilerManager.getInstance(project).make { aborted, errors, _, _ ->
        future.complete(!aborted && errors == 0)
      }
    }

    return future.get() // blockiert bis fertig
  }


  internal class RendererDoneListener(
    private val processHandler: ProcessHandler,
    private val jobParams: IdeaRenderJob,
    private val onDone: () -> Unit,
  ) : CapturingProcessAdapter() {
    override fun startNotified(event: ProcessEvent) {
      println(jobParams)
      processHandler.processInput?.writer()?.use {
        it.write("${Json.encodeToString(jobParams.renderJob)}\n")
        it.write("exit\n")
        it.flush()
      }
    }

    override fun processTerminated(event: ProcessEvent) {
      when {
        event.exitCode != 0 -> err.println(output.stderr)
        else -> processDiagramUpdate(output.stdout)
      }
      onDone()
    }

    private fun processDiagramUpdate(rawPlantuml: String) {
      // Read Results
      val allVisibleClasses = rawPlantuml.lineSequence()
        .filter {
          it.startsWith("enum ") || it.startsWith("class ")
              || it.startsWith("abstract ") || it.startsWith("interface ")
        }.map { it.split(' ')[1] }
        .plus(jobParams.renderJob.classDiagrams.classesToAnalyze)
        .plus(jobParams.renderJob.classDiagrams.classesToHide)
        .sorted().distinct().toList()
      jobParams.optionPanelState.classesInFocus = allVisibleClasses
      jobParams.optionPanelState.classesInFocusSelected = jobParams.renderJob.classDiagrams.classesToAnalyze
      jobParams.optionPanelState.hiddenClasses = allVisibleClasses
      jobParams.optionPanelState.hiddenClassesSelected = jobParams.renderJob.classDiagrams.classesToHide

      jobParams.optionPanelState.hiddenContainersSelected = jobParams.renderJob.classDiagrams.containersToHide
      jobParams.optionPanelState.hiddenContainers = rawPlantuml.lineSequence()
        .filter { it.startsWith("object ") }
        .map { it.split('"')[1] }
        .plus(jobParams.optionPanelState.hiddenContainersSelected)
        .sorted().distinct().toList()

      var plantuml = rawPlantuml

      // remove transmission boilerplate
      plantuml = plantuml.lines().subList(1, plantuml.lines().size - 3).joinToString("\n")

      if (jobParams.optionPanelState.flatPackages) {
        plantuml = plantuml.replace("\\.(?=[A-Z])".toRegex(), "::")
          .replaceFirst("@startuml\n", "@startuml\nset namespaceSeparator ::")
      }
      if (!jobParams.optionPanelState.showPackages) {
        plantuml = plantuml.replace("\\b[a-z.]+\\.(?=[A-Z])".toRegex(), "")
      }
      // insert parameters as comment
      plantuml = plantuml.replaceFirst(
        "@startuml\n",
        "@startuml\n' ${Json.encodeToString(jobParams)}\n!pragma layout smetana\n"
      )


      val project = getProjectByName(jobParams.projectName)
      // update Plugins-Panel
      project.getUserData(PANEL_KEY)!!.updatePanel(jobParams)
      // Write and open Editor
      val virtualFile =
        VirtualFileManager.getInstance().findFileByUrl("file://${jobParams.optionPanelState.targetPumlFile}")!!
      ApplicationManager.getApplication().invokeLater {
        ApplicationManager.getApplication().runWriteAction {
          VfsUtil.saveText(virtualFile, plantuml)
        }
        FileEditorManager.getInstance(project).openFile(virtualFile)
      }
    }
  }

}
