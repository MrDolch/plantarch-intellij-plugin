package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.github.mrdolch.plantarchintellijplugin.asm.ShowPackages
import com.github.mrdolch.plantarchintellijplugin.asm.UseByMethodNames
import com.intellij.mock.MockProject
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class PngViewerPanelTest :
    StringSpec({
      val optionPanelState =
          OptionPanelState(
              projectName = "Test",
              libraryPaths = emptySet(),
              classPaths = emptySet(),
              classesToAnalyze = mutableSetOf("com.acme.Foo"),
              classesInDiagram = mutableSetOf("com.acme.Foo"),
              classesToHide = mutableSetOf("com.acme.Hidden"),
              targetPumlFile = "n/a",
              showPackages = ShowPackages.NONE,
              librariesToHide = mutableSetOf(),
              librariesDiscovered = mutableSetOf(),
              title = "Title",
              description = "Description",
              showUseByMethodNames = UseByMethodNames.NONE,
              plamtumlInlineOptions = "",
              markerClasses = mutableSetOf(),
          )
      val puml =
          """@startuml


class com.github.mrdolch.plantarchintellijplugin.diagram.view.DiagramEditor #ccc
class com.github.mrdolch.plantarchintellijplugin.diagram.view.PngViewerPanel
class com.github.mrdolch.plantarchintellijplugin.diagram.view.PngViewerPanelKt #ccc



object "plantuml-1.2025.4.jar" as 2118180299 #ccc{
  net.sourceforge.plantuml.FileFormat
  net.sourceforge.plantuml.FileFormatOption
  net.sourceforge.plantuml.SourceStringReader
}

object "util-8.jar" as 465261062 #ccc{
  kotlin.jvm.internal.Intrinsics
  kotlin.text.Charsets
}
com.github.mrdolch.plantarchintellijplugin.diagram.view.DiagramEditor ..> com.github.mrdolch.plantarchintellijplugin.diagram.view.PngViewerPanel  
com.github.mrdolch.plantarchintellijplugin.diagram.view.PngViewerPanel ..> 2118180299
com.github.mrdolch.plantarchintellijplugin.diagram.view.PngViewerPanel ..> 465261062
com.github.mrdolch.plantarchintellijplugin.diagram.view.PngViewerPanel ..> com.github.mrdolch.plantarchintellijplugin.diagram.view.PngViewerPanelKt  

title
Dependencies of PngViewerPanel
endtitle

caption

endcaption

skinparam linetype polyline
!pragma layout smetana

@enduml"""
      val testee =
          PngViewerPanel(
              puml,
              MockProject(null) {},
              OptionPanel(optionPanelState) {},
          ) {}

      "should build panel based on puml" {
        testee.svg shouldNotContain "Dot executable does not exist"
        testee.svg shouldContain "PngViewerPanelKt"
      }
    })
