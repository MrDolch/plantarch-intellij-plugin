package com.github.mrdolch.plantarchintellijplugin.toolWindow

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.serialization.json.Json
import tech.dolch.plantarch.cmd.IdeaRenderJob
import java.nio.charset.StandardCharsets
import java.nio.file.Path


object ExecPlantArch {

    private fun getRelevantJdk(project: Project): Sdk? = ProjectRootManager.getInstance(project).projectSdk

    private fun createCommandLine(classPaths: Set<String>, sdkName: String): GeneralCommandLine {
        return SimpleJavaParameters().let { params ->
            params.jdk = ProjectJdkTable.getInstance().findJdk(sdkName)
            params.mainClass = "tech.dolch.plantarch.cmd.MainKt"
            classPaths.forEach { params.classPath.add(it) }
            params.workingDirectory =
                Path.of(PathManager.getPluginsPath(), "plantarch-intellij-plugin", "lib").toString()
            params.toCommandLine().withCharset(StandardCharsets.UTF_8)
        }
    }

    fun executePlantArch(
        job: IdeaRenderJob
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val project = getProjectByName(job.projectName)
            val module = ModuleManager.getInstance(project).findModuleByName(job.moduleName)!!
            val classpath = job.classPaths
            val relevantJdk = getRelevantJdk(module.project)!!
            val commandLine = createCommandLine(classpath, relevantJdk.name)
            with(OSProcessHandler(commandLine)) {
                addProcessListener(RendererDoneListener(this, job))
                startNotify()
                waitFor(10 * 1000L).let {
                    //                            if (!it) notifications.reportError(notificationGroup, name, timeoutMessage)
                }
            }
        }
    }

    private fun getProjectByName(projectName: String) =
        ProjectManager.getInstance().openProjects.first { it.name == projectName }

    internal class RendererDoneListener(
        private val processHandler: ProcessHandler,
        private val jobParams: IdeaRenderJob,
    ) : CapturingProcessAdapter() {
        override fun startNotified(event: ProcessEvent) {
            println(jobParams)
            processHandler.processInput?.writer()?.use {
                it.write("${Json.encodeToString(jobParams.renderJob)}\nexit\n")
                it.flush()
            }
        }

        override fun processTerminated(event: ProcessEvent) = when {
            event.exitCode != 0 -> println(output.stderr)
            else -> processDiagramUpdate(output.stdout)
        }

        private fun processDiagramUpdate(rawPlantuml: String) {
            // Fix Result
            val plantuml = rawPlantuml.lines()
                // remove transmission boilerplate
                .subList(1, rawPlantuml.lines().size - 3)
                .joinToString("\n")
                // insert parameters as comment
                .replaceFirst("@startuml\n", "@startuml\n' $jobParams\n")

            val project = getProjectByName(jobParams.projectName)
            // update Plugins-Panel
            project.getUserData(PANEL_KEY)!!.updatePanel(jobParams, plantuml)
            // Write and open Editor
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://${jobParams.targetPumlFile}")!!
            ApplicationManager.getApplication().invokeLater {
                ApplicationManager.getApplication().runWriteAction {
                    VfsUtil.saveText(virtualFile, plantuml)
                }
                FileEditorManager.getInstance(project).openFile(virtualFile)
            }
        }
    }

}