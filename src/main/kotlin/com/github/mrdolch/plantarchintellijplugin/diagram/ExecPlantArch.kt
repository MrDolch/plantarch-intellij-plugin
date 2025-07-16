package com.github.mrdolch.plantarchintellijplugin.diagram

import com.github.mrdolch.plantarchintellijplugin.app.FILE_PREFIX_DEPENDENCY_DIAGRAM
import com.github.mrdolch.plantarchintellijplugin.app.PANEL_KEY
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.toNioPathOrNull
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.serialization.json.Json
import tech.dolch.plantarch.cmd.IdeaRenderJob
import tech.dolch.plantarch.cmd.OptionPanelState
import tech.dolch.plantarch.cmd.RenderJob
import tech.dolch.plantarch.cmd.ShowPackages
import java.io.File
import java.lang.System.err
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString

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
            val renderSuccess = executePlantArch(job)
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
    val jdkHome = relevantJdk.homePath ?: error("JDK home not found")

    val tempArgsFile = Files.createTempFile("plantarch-cp", ".txt").toFile()
    val cpString = job.classPaths.joinToString(File.pathSeparator) { File(it).canonicalPath }
    tempArgsFile.writeText("-cp\n$cpString\ntech.dolch.plantarch.cmd.MainKt")

    val javaExe = Path.of(jdkHome, "bin", "java").toString()
    return GeneralCommandLine(javaExe, "@${tempArgsFile.canonicalPath}")
      .withWorkDirectory(
        Path.of(PathManager.getPluginsPath(), "plantarch-intellij-plugin", "lib").toFile().canonicalPath
      )
      .withCharset(StandardCharsets.UTF_8)
      .withEnvironment(mapOf("JAVA_TOOL_OPTIONS" to "-Xmx4g"))
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

      if (plantuml.lines().size < 5) return

      // remove transmission boilerplate
      plantuml = plantuml.lines().subList(1, plantuml.lines().size - 3).joinToString("\n")

      if (jobParams.optionPanelState.showPackages == ShowPackages.FLAT) {
        plantuml = plantuml.replace("\\.(?=[A-Z])".toRegex(), "::")
          .replaceFirst("@startuml\n", "@startuml\nset namespaceSeparator ::")
      }
      if (jobParams.optionPanelState.showPackages == ShowPackages.NONE) {
        plantuml = plantuml.replace("\\b[a-z.]+\\.(?=[A-Z])".toRegex(), "")
      }
      // insert parameters as comment
      plantuml = plantuml.replaceFirst(
        "@startuml\n",
        "@startuml\n' ${Json.encodeToString(jobParams)}\n!pragma layout smetana\n"
      )

      ApplicationManager.getApplication().invokeLater {
        val project = getProjectByName(jobParams.projectName)
        // update Plugins-Panel
        project.getUserData(PANEL_KEY)!!.updatePanel(jobParams)
        // Write and open Editor
        val virtualFile = VirtualFileManager.getInstance()
          .refreshAndFindFileByUrl("file://${jobParams.optionPanelState.targetPumlFile}")
        if (virtualFile != null) {
          ApplicationManager.getApplication().runWriteAction {
            VfsUtil.saveText(virtualFile, plantuml)
          }
          FileEditorManager.getInstance(project).openFile(virtualFile)
        }
      }
    }
  }

}

fun createIdeaRenderJob(
  module: Module,
  className: String
): IdeaRenderJob {
  val jobParams = IdeaRenderJob(
    projectName = module.project.name,
    moduleName = module.name,
    classPaths = module.getClasspath(),
    optionPanelState = OptionPanelState(
      targetPumlFile = File.createTempFile(FILE_PREFIX_DEPENDENCY_DIAGRAM, ".puml").canonicalPath,
      showPackages = ShowPackages.NESTED,
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
        workingDir = ModuleUtil.getModuleDirPath(module)
      ),
    ),
  )
  return jobParams
}

fun Module.getClasspath(): ImmutableSet<String> {
  val plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.mrdolch.plantarchintellijplugin"))
  val pluginPath = plugin?.pluginPath?.toFile()
  val jarPath = pluginPath?.resolve("lib/plantarch-0.1.12-launcher.jar")?.canonicalPath
  val classpath = mutableSetOf(jarPath!!)

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

fun getProjectByName(projectName: String) =
  ProjectManager.getInstance().openProjects.first { it.name == projectName }