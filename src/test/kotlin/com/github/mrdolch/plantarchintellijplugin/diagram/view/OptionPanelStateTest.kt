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
              classesToAnalyze = mutableSetOf("className"),
              classesInDiagram = mutableSetOf("className"),
              librariesToHide = mutableSetOf(),
              librariesDiscovered = mutableSetOf(),
              classesToHide = mutableSetOf(),
              plamtumlInlineOptions = "",
              markerClasses = mutableSetOf(),
          )
      "should serialize to yaml" {
        val yaml = testee.toYaml()
        yaml.shouldContain("Details of className")
      }
    })
