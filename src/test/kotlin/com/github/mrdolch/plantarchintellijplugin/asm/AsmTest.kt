package com.github.mrdolch.plantarchintellijplugin.asm

import com.github.mrdolch.plantarchintellijplugin.app.ActionStartNewDiagram
import com.github.mrdolch.plantarchintellijplugin.diagram.view.DiagramEditor
import com.github.mrdolch.plantarchintellijplugin.diagram.view.DragScrollPane
import com.github.mrdolch.plantarchintellijplugin.diagram.view.ExecPlantArch
import io.kotest.core.spec.style.StringSpec
import java.io.File
import java.io.Serializable
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.swing.JPanel

class AsmTest :
    StringSpec({
      fun parameters(): Parameters =
          Parameters(
              title = null,
              caption = null,
              classesToAnalyze =
                  listOf(
                      ActionStartNewDiagram::class.java.canonicalName,
                      ExecPlantArch::class.java.canonicalName,
                      DiagramEditor::class.java.canonicalName,
                  ),
              classesToHide =
                  listOf(
                      DragScrollPane::class.java.canonicalName,
                  ),
              markerClasses =
                  listOf(
                      JPanel::class.java.canonicalName,
                      Serializable::class.java.canonicalName,
                      kotlinx.serialization.Serializable::class.java.canonicalName,
                  ),
              libraryPaths = collectJUnitClassPathUris(),
              librariesToHide = listOf("kotlin-stdlib-2.0.20.jar"),
              classPaths = setOf(Path.of("build/classes/kotlin/main")),
              targetPumlFile = Paths.get("docs", "asm.puml").toFile().canonicalPath,
              showUseByMethodNames = UseByMethodNames.DEFINITION,
              showPackages = ShowPackages.FLAT,
              showLibraries = true,
              plantumlInlineOptions = "",
          )

      "should render UmlDiagram" {
        val job = parameters()

        job.libraryPaths.forEach { Containers.addLibrary(it) }
        job.classPaths.forEach { Containers.addLibrary(it) }

        val classDeps = Asm.scanClassPath(job.classPaths)

        val puml = Asm.toPlantUml(classDeps, job).plantUml

        println(puml)
        //        File(job.targetPumlFile).writer().use { it.write(puml) }
      }
    })

fun collectJUnitClassPathUris(): Set<Path> {
  val uris = LinkedHashSet<URI>()

  fun addUrl(url: URL) {
    // Nur Files (keine "jrt:" / "module:"-Schemes)
    if (url.protocol.equals("file", ignoreCase = true)) {
      try {
        val f = File(url.toURI())
        if (f.exists()) uris += f.toURI()
      } catch (_: Exception) {
        // Fallback: best effort
        runCatching { uris += URI(url.toString()) }
      }
    }
  }

  // 1) URLs aus der ClassLoader-Kette (Thread-Context + eigener)
  fun collectFrom(cl: ClassLoader?) {
    var c: ClassLoader? = cl
    while (c != null) {
      if (c is URLClassLoader) {
        c.urLs?.forEach(::addUrl)
      }
      c = c.parent
    }
  }
  collectFrom(Thread.currentThread().contextClassLoader)
  collectFrom(AsmTest::class.java.classLoader)

  // 2) Fallback: java.class.path
  val sep = File.pathSeparator
  System.getProperty("java.class.path")
      ?.split(sep)
      ?.filter { it.isNotBlank() }
      ?.map { File(it) }
      ?.filter { it.exists() }
      ?.mapTo(uris) { it.toURI() }

  return uris.map { Path.of(it) }.toSet()
}
