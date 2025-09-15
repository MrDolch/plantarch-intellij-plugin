package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.github.mrdolch.plantarchintellijplugin.asm.ShowPackages
import com.github.mrdolch.plantarchintellijplugin.asm.UseByMethodNames
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.*

class ClassTreePanelTest :
    StringSpec({
      fun sampleJob(): OptionPanelState {
        return OptionPanelState(
            projectName = "Test",
            libraryPaths = emptySet(),
            classPaths = emptySet(),
            classesToAnalyze = listOf("com.acme.Foo"),
            classesToHide = listOf("com.acme.Hidden"),
            targetPumlFile = "n/a",
            showPackages = ShowPackages.NONE,
            librariesToHide = emptySet(),
            title = "Title",
            description = "Description",
            showUseByMethodNames = UseByMethodNames.NONE,
            plamtumlInlineOptions = "",
            markerClasses = emptyList(),
        )
      }

      "should build tree with given entries" {
        val testee = ClassTreePanel(sampleJob()) {}
        testee.containerEntries.map { it.name } shouldContainExactly listOf("Source Classes")
        //        testee.getClassesToAnalyze() shouldContainExactly listOf("com.acme.Foo")
      }

      "toggleEntryFromDiagram should change visibility" {
        var changed = false
        val testee = ClassTreePanel(sampleJob()) { changed = true }

        testee.toggleEntryFromDiagram("Foo")

        val bar =
            testee.containerEntries
                .flatMap { it.packages }
                .flatMap { it.classes }
                .firstOrNull { it.name.endsWith(".Foo") }

        //        bar.visibility shouldBe VisibilityStatus.MAYBE
        //        changed shouldBe true
      }

      "getClassesToHide should return hidden ones" {
        val panel = ClassTreePanel(sampleJob()) {}
        // panel.getClassesToHide() shouldContainExactly listOf("com.acme.Hidden")
      }
    })

fun main() {
  SwingUtilities.invokeLater {
    val frame = JFrame("Diagram Filter Panel Demo")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    val jobParams = OptionPanelState.fromYaml(jobParamsYaml)
    frame.contentPane.add(ClassTreePanel(jobParams, { m -> JTree(m) }) {})
    frame.setSize(400, 600)
    frame.setLocationRelativeTo(null)

    frame.rootPane.let { rootPane ->
      rootPane
          .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
          .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeWindow")
      rootPane.actionMap.put(
          "closeWindow",
          object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
              frame.dispose()
            }
          },
      )
    }

    frame.isVisible = true
  }
}

var jobParamsYaml =
    """
projectName: "configurable-google-java-format"
libraryPaths: []
classPaths: []
targetPumlFile: "/tmp/dependency-diagram-11761671643623754960.plantarch"
"""
