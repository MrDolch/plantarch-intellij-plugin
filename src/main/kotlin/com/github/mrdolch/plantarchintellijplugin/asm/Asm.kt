package com.github.mrdolch.plantarchintellijplugin.asm

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import org.objectweb.asm.ClassReader

enum class DepKind {
  EXTENDS,
  IMPLEMENTS,
  FIELD_TYPE,
  METHOD_RET,
  METHOD_PARAM,
  ANNOTATION,
  METHOD_CALL,
  FIELD_ACCESS,
  INSN_TYPE,
  GENERIC,
}

data class Edge(val from: Klasse, val to: Klasse, val kind: DepKind, val name: String)

data class ClassDeps(val owner: Klasse, val deps: MutableSet<Edge> = linkedSetOf())

data class Klasse(
    val container: Container,
    val fullname: String,
    var klassenart: Klassenart,
    val fields: MutableSet<String> = mutableSetOf(),
    val methods: MutableSet<String> = mutableSetOf(),
    val simplename: String = fullname.substringAfterLast('.'),
    val packagename: String = fullname.substringBeforeLast('.', ""),
    val id: String = fullname.toMd5(),
) {
  fun toNameAsId(showPackages: ShowPackages) =
      when (showPackages) {
        ShowPackages.NESTED -> fullname
        ShowPackages.FLAT -> "$packagename::$simplename"
        ShowPackages.NONE -> "\"$simplename\" as $id"
      }

  fun toId(showPackages: ShowPackages) =
      when (showPackages) {
        ShowPackages.NESTED -> fullname
        ShowPackages.FLAT -> "$packagename::$simplename"
        ShowPackages.NONE -> id
      }
}

enum class Klassenart(val umlTyp: String, val stereotyp: String = "<<$umlTyp>>") {
  ANNOTATION("annotation"),
  INTERFACE("interface"),
  ABSTRACT_CLASS("abstract class", "<<abstract>>"),
  CLASS("class"),
  ENUM("enum"),
  RECORD("record"),
}

data class Parameters(
    val projectName: String,
    val moduleName: String,
    val title: String?,
    val caption: String?,
    var classesToAnalyze: List<String> = emptyList(),
    var classesToHide: List<String> = emptyList(),
    var markerClasses: List<String> = emptyList(),
    var librariesToHide: List<String> = emptyList(),
    val libraryPaths: Set<Path>,
    val classPaths: Set<Path>,
    var showLibraries: Boolean = false,
    var showPackages: ShowPackages = ShowPackages.NONE,
    var showUseByMethodNames: UseByMethodNames = UseByMethodNames.NONE,
    var targetPumlFile: String,
    var plantumlInlineOptions: String,
)

enum class ShowPackages {
  NONE,
  NESTED,
  FLAT,
}

enum class UseByMethodNames {
  NONE,
  FOCUSED_ONLY,
  DEFINITION,
  ARROW,
}

data class Result(
    val deps: Set<ClassDeps>,
    val classesInDiagram: Set<String>,
    val containersInDiagram: Set<String>,
    val plantUml: String,
    val libraryPaths: Set<String>,
    val classPaths: Set<Path>,
)

object Asm {
  private const val OPTIONS = ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES

  fun renderDiagram(parameters: Parameters): Result {
    parameters.libraryPaths.forEach { Containers.addLibrary(it) }
    parameters.classPaths.forEach { Containers.addLibrary(it) }

    val classDeps = scanClassPath(parameters.classPaths)
    return toPlantUml(classDeps, parameters)
  }

  fun scanClassPath(roots: Set<Path>): Set<ClassDeps> {
    val edges = linkedSetOf<Edge>()
    roots.forEach { root ->
      when {
        Files.isDirectory(root) ->
            Files.walk(root).use { stream ->
              stream
                  .filter { it.toString().endsWith(".class") }
                  .forEach { p -> collectEdges(Files.newInputStream(p), edges) }
            }

        root.toString().endsWith(".jar") ->
            JarFile(root.toFile()).use { jar ->
              println(root)
              jar.entries()
                  .asSequence()
                  .filter { it.name.endsWith(".class") }
                  .forEach { e -> collectEdges(jar.getInputStream(e), edges) }
            }
      }
    }

    // Gruppieren pro Owner
    return edges
        .groupBy { it.from }
        .map { (from, es) -> ClassDeps(from, es.toMutableSet()) }
        .toSet()
  }

  private fun collectEdges(inputStream: InputStream, edges: LinkedHashSet<Edge>) =
      inputStream.use { ins -> ClassReader(ins).accept(ClassDependencyCollector(edges), OPTIONS) }

  fun toPlantUml(deps: Set<ClassDeps>, job: Parameters): Result {
    val classesToAnalyze = job.classesToAnalyze
    val mainDeps = deps.filter { classesToAnalyze.contains(it.owner.fullname) }
    val klassenInFocus = (mainDeps.map { it.owner }).toSet()
    val klassenInDiagram =
        (klassenInFocus +
                deps
                    .flatMap { it.deps }
                    .filter { klassenInFocus.contains(it.to) }
                    .map { it.from }
                    .filter { it.container != Containers.UNKNOWN }
                    .filter { !it.container.name.endsWith(".jar") } +
                deps
                    .flatMap { it.deps }
                    .filter { klassenInFocus.contains(it.from) }
                    .map { it.to }
                    .filter { it.container != Containers.UNKNOWN }
                    .filter { !it.container.name.endsWith(".jar") })
            .filter { !job.classesToHide.contains(it.fullname) }
            .filter { !job.markerClasses.contains(it.simplename) }
            .toSet()
    val edgesInDiagram =
        deps
            .flatMap { it.deps }
            .filter { klassenInDiagram.contains(it.from) }
            .filter { klassenInDiagram.contains(it.to) }
            .filter { it.to != it.from }

    val edgesToMarkers =
        deps
            .flatMap { it.deps }
            .filter { klassenInDiagram.contains(it.from) }
            .filter { job.markerClasses.contains(it.to.simplename) }
            .filter {
              it.kind == DepKind.IMPLEMENTS ||
                  it.kind == DepKind.EXTENDS ||
                  it.kind == DepKind.ANNOTATION ||
                  it.kind == DepKind.GENERIC
            }

    val uml = StringBuilder("@startuml\n")
    if (job.showPackages == ShowPackages.FLAT) uml.appendLine("set namespaceSeparator ::")

    // Klassen deklarieren
    klassenInDiagram
        .map { klasse ->
          val marker =
              edgesToMarkers
                  .filter { it.from == klasse }
                  .map { "<<${it.to.simplename}>>" }
                  .sorted()
                  .distinct()
                  .joinToString("")
          if (klassenInFocus.contains(klasse)) {
            val body =
                if (
                    job.showUseByMethodNames == UseByMethodNames.FOCUSED_ONLY ||
                        job.showUseByMethodNames == UseByMethodNames.DEFINITION ||
                        job.showUseByMethodNames == UseByMethodNames.ARROW
                ) {
                  val fields = klasse.fields.sorted().distinct().joinToString("") { "    $it\n" }
                  val methods = klasse.methods.sorted().distinct().joinToString("") { "    $it\n" }
                  if (methods.isEmpty()) "" else " {\n$fields    ---\n$methods}"
                } else ""
            "${klasse.klassenart.umlTyp} ${klasse.toNameAsId(job.showPackages)}<<inFocus>>$marker${klasse.klassenart.stereotyp}$body"
          } else {
            val members =
                edgesInDiagram
                    .filter { it.kind == DepKind.FIELD_ACCESS || it.kind == DepKind.METHOD_CALL }
                    .filter { it.to == klasse }
                    .map { it.name }
                    .distinct()
            val fields =
                members
                    .filter { it.isNotEmpty() && it.subSequence(0, 1).matches("[A-Z]".toRegex()) }
                    .sorted()
                    .joinToString("") { "    $it\n" }
            val methods =
                members
                    .filter { it.isEmpty() || !it.subSequence(0, 1).matches("[A-Z]".toRegex()) }
                    .sorted()
                    .joinToString("") { "    $it\n" }
            val body =
                if (members.isEmpty() || job.showUseByMethodNames != UseByMethodNames.DEFINITION) ""
                else " {\n$fields    ---\n$methods}"
            "${klasse.klassenart.umlTyp} ${klasse.toNameAsId(job.showPackages)}$marker${klasse.klassenart.stereotyp}$body"
          }
        }
        .forEach { uml.appendLine(it) }

    // implements
    edgesInDiagram
        .filter { it.kind == DepKind.IMPLEMENTS }
        .map { "${it.from.toId(job.showPackages)} -up-|> ${it.to.toId(job.showPackages)}" }
        .distinct()
        .forEach { uml.appendLine(it) }

    // implements
    edgesInDiagram
        .filter { it.kind == DepKind.EXTENDS }
        .map { ("${it.from.toId(job.showPackages)} .up.|> ${it.to.toId(job.showPackages)}") }
        .distinct()
        .forEach { uml.appendLine(it) }

    // has and access
    edgesInDiagram
        .filter {
          it.kind == DepKind.GENERIC ||
              it.kind == DepKind.ANNOTATION ||
              it.kind == DepKind.FIELD_TYPE ||
              it.kind == DepKind.FIELD_ACCESS ||
              it.kind == DepKind.METHOD_RET ||
              it.kind == DepKind.METHOD_PARAM ||
              it.kind == DepKind.METHOD_CALL
        }
        .distinctBy { it.from to it.to }
        .filter {
          // TODO: filter all who already in implements in grandparents
          edgesInDiagram
              .filter { edge -> edge.kind == DepKind.EXTENDS || edge.kind == DepKind.IMPLEMENTS }
              .none { edge -> edge.to == it.to && edge.from == it.from }
        }
        .map { edge ->
          val toFields =
              edgesInDiagram
                  .filter { it.kind == DepKind.FIELD_ACCESS }
                  .filter { it.from == edge.from }
                  .filter { it.to == edge.to }
          val toMethods =
              edgesInDiagram
                  .filter { it.kind == DepKind.METHOD_CALL }
                  .filter { it.from == edge.from }
                  .filter { it.to == edge.to }
          val toMembers = (toMethods + toFields).map { it.name }.sorted().joinToString("\\n")
          val dotted = if (toMethods.isEmpty()) "[dotted]" else ""
          val up = if (edge.kind == DepKind.ANNOTATION) "up" else ""
          if (toMembers.isEmpty() || job.showUseByMethodNames != UseByMethodNames.ARROW)
              "${edge.from.toId(job.showPackages)} .$dotted$up.> ${edge.to.toId(job.showPackages)}"
          else
              "${edge.from.toId(job.showPackages)} .$dotted$up.> ${edge.to.toId(job.showPackages)} : $toMembers"
        }
        .forEach { uml.appendLine(it) }

    // libraries
    val jarContainers = Containers.getJarContainers()

    val edgesToContainers =
        mainDeps
            .flatMap { it.deps }
            .filter { jarContainers.contains(it.to.container) }
            .filter { !job.librariesToHide.contains(it.to.container.name) }

    val usedLibraryPaths = mutableSetOf<Container>()
    if (job.showLibraries) {
      edgesToContainers
          .map { it.to.container }
          .distinct()
          .forEach { jar ->
            val klassenInDependency =
                edgesToContainers
                    .filter { dep -> dep.to.container == jar }
                    .filter { dep -> klassenInDiagram.contains(dep.from) }
                    .map { dep -> dep.to.fullname }
                    .sorted()
                    .distinct()
                    .joinToString("\n")
            uml.appendLine("object \"${jar.name}\" as ${jar.id} {\n$klassenInDependency\n}")
          }
      klassenInDiagram.forEach { klasse ->
        edgesToContainers
            .filter { edge -> edge.from == klasse }
            .map { edge -> edge.to.container }
            .distinct()
            .forEach { container ->
              usedLibraryPaths += container
              uml.appendLine("${klasse.toId(job.showPackages)} ..> ${container.id}")
            }
      }
    }

    job.title?.let { uml.appendLine("title\n$it\nendtitle") }
    job.caption?.let { uml.appendLine("caption\n$it\nendcaption") }
    uml.appendLine(job.plantumlInlineOptions)
    uml.appendLine("@enduml")

    return Result(
        deps,
        klassenInDiagram.map { it.fullname }.toSet(),
        edgesToContainers.map { it.to.container.name }.toSet(),
        uml.toString(),
        usedLibraryPaths.map { it.name }.toSet(),
        job.classPaths,
    )
  }
}
