package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.github.mrdolch.plantarchintellijplugin.asm.ShowPackages
import com.github.mrdolch.plantarchintellijplugin.asm.UseByMethodNames
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class PngViewerPanelTest :
    StringSpec({
      val testee =
          PngViewerPanel(
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

@enduml""",
              OptionPanelState(
                  projectName = "Test",
                  moduleName = "test",
                  libraryPaths = emptySet(),
                  classPaths = emptySet(),
                  classesInFocus = listOf("com.acme.Foo", "com.acme.Bar", "com.acme.Hidden"),
                  classesInFocusSelected = listOf("com.acme.Foo"),
                  hiddenClassesSelected = listOf("com.acme.Hidden"),
                  hiddenContainers = listOf("ExtLib"),
                  targetPumlFile = "n/a",
                  showPackages = ShowPackages.NONE,
                  hiddenContainersSelected = emptyList(),
                  hiddenClasses = emptyList(),
                  title = "Title",
                  description = "Description",
                  showUseByMethodNames = UseByMethodNames.NONE,
                  plamtumlInlineOptions = "",
                  markerClassesSelected = emptyList(),
              ),
          ) {}

      "should build panel based on puml" {
        testee.svg shouldNotContain "Dot executable does not exist"
        testee.svg shouldContain "PngViewerPanelKt"
      }
    })
