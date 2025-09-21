package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.charleskorn.kaml.Yaml
import com.github.mrdolch.plantarchintellijplugin.asm.ShowPackages
import com.github.mrdolch.plantarchintellijplugin.asm.UseByMethodNames
import com.github.mrdolch.plantarchintellijplugin.configuration.Configuration
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.toNioPathOrNull
import java.io.File
import kotlin.io.path.absolutePathString
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.serialization.Serializable

@Serializable
data class Dependency(
    var classInheritance: Boolean = true,
    var classGenericType: Boolean = true,
    var classAnnotation: Boolean = true,
    var methodCall: Boolean = true,
    var methodParameterType: Boolean = true,
    var methodReturnType: Boolean = true,
    var fieldType: Boolean = true,
)

@Serializable
data class OptionPanelState(
    val projectName: String,
    val libraryPaths: Set<String>,
    val classPaths: Set<String>,
    val targetPumlFile: String,
    var title: String,
    var description: String,
    var plamtumlInlineOptions: String,
    var showPackages: ShowPackages,
    var showUseByMethodNames: UseByMethodNames = UseByMethodNames.NONE,
    var classesInDiagram: MutableSet<String>,
    var classesToAnalyze: MutableSet<String>,
    var classesToHide: MutableSet<String>,
    var librariesToHide: MutableSet<String>,
    var librariesDiscovered: MutableSet<String>,
    var markerClasses: MutableSet<String>,
    var showLibraries: Boolean = false,
    var showDependencies: Dependency = Dependency(),
) {
  fun toYaml(): String = Yaml.Companion.default.encodeToString(serializer(), this)

  companion object {
    const val MARKER_STARTCONFIG = "@startOptionPanelState"
    const val MARKER_ENDCONFIG = "@endOptionPanelState"

    fun createDefaultOptionPanelState(
        project: Project,
        className: String,
        configuration: Configuration,
    ): OptionPanelState =
        OptionPanelState(
            title = "Details of ${className.substringAfterLast(".")}",
            description = "Dependencies of\n$className",
            projectName = project.name,
            libraryPaths = project.modules.flatMap { it.getLibraryPath() }.toImmutableSet(),
            classPaths = project.modules.flatMap { it.getClasspath() }.toImmutableSet(),
            targetPumlFile =
                File.createTempFile(
                        DiagramEditorProvider.FILE_PREFIX_DEPENDENCY_DIAGRAM,
                        "." + DiagramEditorProvider.FILE_EXTENSION,
                    )
                    .canonicalPath,
            classesInDiagram = mutableSetOf(className),
            classesToAnalyze = mutableSetOf(className),
            librariesToHide = mutableSetOf("jrt.jar"),
            librariesDiscovered = mutableSetOf("jrt.jar"),
            classesToHide = mutableSetOf(),
            showPackages = configuration.showPackages,
            showUseByMethodNames = configuration.showMethodNames,
            markerClasses = configuration.markerClasses.split("\n").toMutableSet(),
            plamtumlInlineOptions = configuration.plantumlOptions,
            showDependencies = Dependency(),
        )

    // 1. Eigene kompilierten Klassen
    private fun Module.getClasspath(): Set<String> =
        setOfNotNull(
            CompilerModuleExtension.getInstance(this)
                ?.compilerOutputPath
                ?.toNioPathOrNull()
                ?.toString(),
        )

    // 2. Abhängigkeiten (Libraries, andere Module)
    private fun Module.getLibraryPath(): Set<String> =
        ModuleRootManager.getInstance(this)
            .orderEntries()
            .productionOnly()
            .classes()
            .roots
            .map { File(it.path).toPath() }
            .map { it.absolutePathString() }
            .map { it.removeSuffix("!") }
            .toSet()

    fun fromYaml(yaml: String): OptionPanelState =
        Yaml(configuration = Yaml.default.configuration.copy(strictMode = false))
            .decodeFromString(serializer(), yaml)
  }
}
