package com.github.mrdolch.plantarchintellijplugin.asm

import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile

fun main() {
  val roots =
      listOf(
          Paths.get("build/classes/kotlin/main"),
      )

  val classDeps = scanClassPath(roots)

  println(classDeps)

  val puml =
      toPlantUml(classDeps)
          .lines()
          .filter {
            it.contains("com.github.mrdolch.plantarchintellijplugin.asm") || it.contains("@")
          }
          .filter {
            !it.contains(">") && !it.contains("<") ||
                it.matches(
                    (".*com\\.github\\.mrdolch\\.plantarchintellijplugin\\.asm\\..*[<>].*" +
                            "com\\.github\\.mrdolch\\.plantarchintellijplugin\\.asm\\..*")
                        .toRegex()
                )
          }
          .joinToString("\n")

  println(puml)
  Paths.get("docs", "asm.puml").toFile().writer().use { it.write(puml) }
}

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

data class Edge(val from: String, val to: String, val kind: DepKind, val name: String)

data class ClassDeps(val owner: String, val deps: MutableSet<Edge> = linkedSetOf())

private fun internalToFqcn(internal: String): String = internal.replace('/', '.')

private fun addTypeEdge(
    from: String,
    asmType: Type,
    kind: DepKind,
    sink: MutableSet<Edge>,
) {
  when (asmType.sort) {
    Type.ARRAY -> addTypeEdge(from, asmType.elementType, kind, sink)
    Type.OBJECT -> sink += Edge(from, internalToFqcn(asmType.internalName), kind, "")
    else -> {}
  }
}

private fun addMethodDescEdges(from: String, desc: String, sink: MutableSet<Edge>) {
  Type.getArgumentTypes(desc).forEach { addTypeEdge(from, it, DepKind.METHOD_PARAM, sink) }
  addTypeEdge(from, Type.getReturnType(desc), DepKind.METHOD_RET, sink)
}

private class GenericTypeCollector(private val from: String, private val sink: MutableSet<Edge>) :
    SignatureVisitor(Opcodes.ASM9) {

  override fun visitClassType(name: String) {
    sink += Edge(from, internalToFqcn(name), DepKind.GENERIC, "")
  }
}

class ClassDependencyCollector(private val edgesOut: MutableSet<Edge>) :
    ClassVisitor(Opcodes.ASM9) {

  private lateinit var owner: String

  override fun visit(
      version: Int,
      access: Int,
      name: String,
      signature: String?,
      superName: String?,
      interfaces: Array<out String>?,
  ) {
    owner = internalToFqcn(name)
    if (superName != null && superName != "java/lang/Object") {
      edgesOut += Edge(owner, internalToFqcn(superName), DepKind.EXTENDS, "")
    }
    interfaces?.forEach { edgesOut += Edge(owner, internalToFqcn(it), DepKind.IMPLEMENTS, "") }
    signature?.let { SignatureReader(it).accept(GenericTypeCollector(owner, edgesOut)) }
  }

  override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
    addTypeEdge(owner, Type.getType(descriptor), DepKind.ANNOTATION, edgesOut)
    return object : AnnotationVisitor(Opcodes.ASM9) {
      override fun visit(name: String?, value: Any?) {
        if (value is Type) addTypeEdge(owner, value, DepKind.ANNOTATION, edgesOut)
      }

      override fun visitEnum(name: String?, desc: String?, value: String?) {
        if (desc != null) addTypeEdge(owner, Type.getType(desc), DepKind.ANNOTATION, edgesOut)
      }

      override fun visitAnnotation(name: String?, desc: String?): AnnotationVisitor? {
        if (desc != null) addTypeEdge(owner, Type.getType(desc), DepKind.ANNOTATION, edgesOut)
        return this
      }

      override fun visitArray(name: String?): AnnotationVisitor? = this
    }
  }

  override fun visitField(
      access: Int,
      name: String,
      descriptor: String,
      signature: String?,
      value: Any?,
  ): FieldVisitor {
    addTypeEdge(owner, Type.getType(descriptor), DepKind.FIELD_TYPE, edgesOut)
    signature?.let { SignatureReader(it).acceptType(GenericTypeCollector(owner, edgesOut)) }
    return object : FieldVisitor(Opcodes.ASM9) {}
  }

  override fun visitMethod(
      access: Int,
      name: String,
      descriptor: String,
      signature: String?,
      exceptions: Array<out String>?,
  ): MethodVisitor {
    addMethodDescEdges(owner, descriptor, edgesOut)
    signature?.let { SignatureReader(it).accept(GenericTypeCollector(owner, edgesOut)) }
    exceptions?.forEach { edgesOut += Edge(owner, internalToFqcn(it), DepKind.METHOD_RET, "") }

    return object : MethodVisitor(Opcodes.ASM9) {
      override fun visitTypeInsn(opcode: Int, type: String) {
        edgesOut += Edge(owner, internalToFqcn(type), DepKind.INSN_TYPE, "")
      }

      override fun visitFieldInsn(opcode: Int, owner2: String, name: String, desc: String) {
        edgesOut += Edge(owner, internalToFqcn(owner2), DepKind.FIELD_ACCESS, name)
        addTypeEdge(owner, Type.getType(desc), DepKind.FIELD_TYPE, edgesOut)
      }

      override fun visitMethodInsn(
          opcode: Int,
          owner2: String,
          name: String,
          desc: String,
          itf: Boolean,
      ) {
        edgesOut += Edge(owner, internalToFqcn(owner2), DepKind.METHOD_CALL, name)
        addMethodDescEdges(owner, desc, edgesOut)
      }

      override fun visitLdcInsn(value: Any) {
        if (value is Type) addTypeEdge(owner, value, DepKind.INSN_TYPE, edgesOut)
      }

      override fun visitInvokeDynamicInsn(
          name: String,
          desc: String,
          bsm: Handle,
          vararg bsmArgs: Any,
      ) {
        // Signaturen des dynamischen Aufrufs
        addMethodDescEdges(owner, desc, edgesOut)
        // Bootstrap-Handle-Owner referenziert häufig Funktions-/Lambda-Typen
        edgesOut += Edge(owner, internalToFqcn(bsm.owner), DepKind.METHOD_CALL, name)
        bsmArgs.forEach {
          when (it) {
            is Type -> addTypeEdge(owner, it, DepKind.INSN_TYPE, edgesOut)
            is Handle ->
                edgesOut += Edge(owner, internalToFqcn(it.owner), DepKind.METHOD_CALL, name)
          }
        }
      }

      override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
        if (type != null) edgesOut += Edge(owner, internalToFqcn(type), DepKind.INSN_TYPE, name)
      }

      override fun visitLocalVariable(
          name: String,
          desc: String,
          signature: String?,
          start: Label?,
          end: Label?,
          index: Int,
      ) {
        addTypeEdge(owner, Type.getType(desc), DepKind.INSN_TYPE, edgesOut)
        signature?.let { SignatureReader(it).acceptType(GenericTypeCollector(owner, edgesOut)) }
      }
    }
  }
}

fun scanClassPath(roots: List<Path>): Set<ClassDeps> {
  val edges = linkedSetOf<Edge>()
  roots.forEach { root ->
    when {
      Files.isDirectory(root) ->
          Files.walk(root).use { stream ->
            stream
                .filter { it.toString().endsWith(".class") }
                .forEach { p ->
                  Files.newInputStream(p).use { ins ->
                    ClassReader(ins)
                        .accept(
                            ClassDependencyCollector(edges),
                            ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
                        )
                  }
                }
          }
      root.toString().endsWith(".jar") ->
          JarFile(root.toFile()).use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
              val e = entries.nextElement()
              if (e.name.endsWith(".class")) {
                jar.getInputStream(e).use { ins ->
                  ClassReader(ins)
                      .accept(
                          ClassDependencyCollector(edges),
                          ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
                      )
                }
              }
            }
          }
    }
  }

  // Gruppieren pro Owner
  return edges.groupBy { it.from }.map { (from, es) -> ClassDeps(from, es.toMutableSet()) }.toSet()
}

fun toPlantUml(deps: Set<ClassDeps>): String {
  val sb = StringBuilder()
  sb.appendLine("@startuml")
  // Klassen deklarieren (nur was vorkommt)
  val nodes = deps.flatMap { listOf(it.owner) + it.deps.map(Edge::to) }.toSet()
  nodes.forEach { sb.appendLine("class \"${it}\"") }

  deps.forEach { cd ->
    cd.deps.forEach { e ->
      val arrow =
          when (e.kind) {
            DepKind.EXTENDS -> " <|-- "
            DepKind.IMPLEMENTS -> " <|.. "
            else -> " ..> "
          }
      sb.appendLine("\"${e.to}\"$arrow\"${e.from}\" : ${e.kind} ${e.name}")
    }
  }
  sb.appendLine("@enduml")
  return sb.toString()
}
