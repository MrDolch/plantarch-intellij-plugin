package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.github.mrdolch.plantarchintellijplugin.asm.ShowPackages
import com.github.mrdolch.plantarchintellijplugin.asm.UseByMethodNames
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class OptionPanelStateTest :
    StringSpec({
      val testee =
          OptionPanelState(
              title = "Details of className",
              description = "Dependencies of className",
              projectName = "module.project.name",
              moduleName = "module.name",
              libraryPaths = setOf("libraryPaths"),
              classPaths = setOf("libraryPaths"),
              targetPumlFile =
                  File.createTempFile(
                          DiagramEditorProvider.FILE_PREFIX_DEPENDENCY_DIAGRAM,
                          "." + DiagramEditorProvider.FILE_EXTENSION,
                      )
                      .canonicalPath,
              showPackages = ShowPackages.NESTED,
              showUseByMethodNames = UseByMethodNames.NONE,
              classesInFocus = emptyList(),
              classesInFocusSelected = listOf("className"),
              hiddenContainers = emptyList(),
              hiddenContainersSelected = emptyList(),
              hiddenClasses = emptyList(),
              hiddenClassesSelected = emptyList(),
          )
      "should serialize to yaml" {
        val yaml = testee.toYaml()
        yaml.shouldContain("Details of className")
      }
    })
