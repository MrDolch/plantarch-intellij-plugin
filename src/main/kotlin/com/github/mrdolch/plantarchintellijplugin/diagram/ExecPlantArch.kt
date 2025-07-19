package com.github.mrdolch.plantarchintellijplugin.diagram

import com.github.mrdolch.plantarchintellijplugin.app.FILE_PREFIX_DEPENDENCY_DIAGRAM
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
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
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
            val rawPlantUml = executePlantArch(job)
            if (rawPlantUml == null) return

            indicator.text = "Updating UI..."
            ApplicationManager.getApplication().invokeLater {
              // Update your UI here
              DiagramView.processDiagramViewUpdate(job, rawPlantUml)
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

  fun executePlantArch(job: IdeaRenderJob): String? {
    val commandLine = createCommandLine(job)
    val doneLatch = CountDownLatch(1)
    with(OSProcessHandler(commandLine)) {
      println(job)
      val rendererDoneListener = RendererDoneListener(this, job) {
        doneLatch.countDown()
      }
      addProcessListener(rendererDoneListener)
      startNotify()
      doneLatch.await(90, TimeUnit.SECONDS)
      return rendererDoneListener.rawPlantUml
    }
  }


  private fun createCommandLine(job: IdeaRenderJob): GeneralCommandLine {
    val project = getProjectByName(job.projectName)
    val module = ModuleManager.getInstance(project).findModuleByName(job.moduleName)!!
    val relevantJdk = getRelevantJdk(module.project)!!
    val jdkHome = relevantJdk.homePath ?: error("JDK home not found")

    val tempArgsFile = Files.createTempFile("plantarch-cp", ".txt").toFile()
    val sortedClasspath = job.classPaths.map { File(it) }
      .sortedBy { it.isDirectory() } // Project-Files first
      .sortedBy { it.name.endsWith("-launcher.jar") } // Plugin-Lib last
    println(sortedClasspath.joinToString("\n"))
    val cpString = sortedClasspath.joinToString(File.pathSeparator) { it.canonicalPath }
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
    return future.get()
  }

  internal class RendererDoneListener(
    private val processHandler: ProcessHandler,
    private val jobParams: IdeaRenderJob,
    var rawPlantUml: String? = null,
    private val onDone: () -> Unit,
  ) : CapturingProcessAdapter() {
    override fun startNotified(event: ProcessEvent) {
      processHandler.processInput?.writer()?.use {
        it.write("${Json.encodeToString(jobParams.renderJob)}\n")
        it.write("exit\n")
        it.flush()
      }
    }

    override fun processTerminated(event: ProcessEvent) {
      when {
        event.exitCode != 0 -> err.println(output.stderr)
        else -> rawPlantUml = output.stdout
      }
      onDone()
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
    classPaths = module.project.modules.flatMap { it.getClasspath() }.toImmutableSet(),
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
        projectDir = ModuleUtil.getModuleDirPath(module),
        moduleDirs = module.project.modules
          .map { it.guessModuleDir()?.canonicalPath ?: "" }
          .filter { it.isNotBlank() }.toList()
      ),
    ),
  )
  return jobParams
}

fun Module.getClasspath(): ImmutableSet<String> {
  val plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.mrdolch.plantarchintellijplugin"))
  val pluginPath = plugin?.pluginPath?.toFile()
  val jarPath = pluginPath?.resolve("lib/plantarch-0.1.13-SNAPSHOT-launcher.jar")?.canonicalPath
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