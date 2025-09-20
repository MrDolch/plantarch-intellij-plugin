package com.github.mrdolch.plantarchintellijplugin.asm

import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.use

data class Container(val name: String, val id: String = name.toMd5())

fun String.toMd5(): String =
    MessageDigest.getInstance("MD5").digest(toByteArray()).joinToString("") { "%02x".format(it) }

object Containers {
  val UNKNOWN: Container = Container("unknown")
  private val nameZuContainer = mutableMapOf<String, Container>()
  private val klassenZuContainer: MutableMap<String, Container> = mutableMapOf()

  fun getContainers() = nameZuContainer.values.toSet()

  fun getJarContainers() = getContainers().filter { it.name.endsWith(".jar") }
  fun getContainerOfClass(fullname:String) = klassenZuContainer.getOrDefault(fullname, UNKNOWN)

  fun addLibrary(path: Path) {
    val name =
        when {
          path.toString().endsWith(".jar") -> path.last().name
          path.toString().contains("!") -> "jrt.jar"
          else -> path.name
        }
    val container = nameZuContainer.computeIfAbsent(name) { Container(name) }
    when {
      Files.isDirectory(path) -> listClassesFromDirectory(path)
      else -> listClassesFromIdeaBangPath(path)
    }.forEach { klasse -> klassenZuContainer[klasse] = container }
  }

  private fun listClassesFromDirectory(path: Path): List<String> =
      Files.walk(path).use { stream ->
        stream
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
            .map { clazz ->
              val rel = path.relativize(clazz).toString()
              rel.removeSuffix(".class") // Klasse ohne Endung
                  .replace('/', '.') // Pakettrenner
                  .replace('\\', '.') // Windows
            }
            .filter { it != "module-info" }
            .toList()
      }

  fun listClassesFromIdeaBangPath(ideaPath: Path): List<String> {
    val bang = "!/"
    if (!ideaPath.toString().contains(bang)) {
      return if (ideaPath.extension == "jar" || ideaPath.extension == "zip")
          listClassesFromJar(ideaPath.toString())
      else emptyList()
    }
    val (left, right) = ideaPath.toString().split(bang, limit = 2)

    return if (!(left.endsWith(".jar", true) || left.endsWith(".zip", true))) {
      val parts = right.split('/', limit = 2)
      val module = parts[0]
      val sub = if (parts.size > 1) parts[1] else ""
      listClassesFromJrt(module, sub)
    } else {
      // Echte Jar-Datei vor dem "!/"
      listClassesFromJar(left, right)
    }
  }

  private fun listClassesFromJrt(module: String, subdir: String): List<String> {
    FileSystems.newFileSystem(URI.create("jrt:/"), emptyMap<String, Any>()).use { jrt ->
      val base =
          if (subdir.isEmpty()) jrt.getPath("modules", module)
          else jrt.getPath("modules", module, subdir)

      if (!Files.exists(base)) return emptyList()
      return Files.walk(base).use { paths ->
        paths
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
            .map { base.relativize(it).toString().removeSuffix(".class").replace('/', '.') }
            .filter { it != "module-info" }
            .toList()
      }
    }
  }

  private fun listClassesFromJar(jarPath: String, inside: String = ""): List<String> {
    ZipFile(jarPath).use { zip ->
      val prefix = if (inside.isBlank()) "" else inside.trimEnd('/') + "/"
      return zip.entries()
          .asSequence()
          .filter { !it.isDirectory && it.name.endsWith(".class") && it.name.startsWith(prefix) }
          .map { it.name.removePrefix(prefix).removeSuffix(".class").replace('/', '.') }
          .filter { it != "module-info" }
          .toList()
    }
  }

  fun clear() {
    nameZuContainer.clear()
    klassenZuContainer.clear()
  }
}
