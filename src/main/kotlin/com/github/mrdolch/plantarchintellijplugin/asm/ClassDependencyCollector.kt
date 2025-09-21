package com.github.mrdolch.plantarchintellijplugin.asm

import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

val containers: MutableMap<Container, MutableMap<String, Klasse>> = mutableMapOf()
private val enumOwners = mutableSetOf<String>()

class ClassDependencyCollector(private val edgesOut: MutableSet<Edge>) :
    ClassVisitor(Opcodes.ASM9) {

  private lateinit var owner: Klasse

  // --- NEU: Heuristik-Flags je besuchter Klasse ---
  private var hasKotlinMetadata = false
  private var hasCopyMethod = false
  private var hasAnyComponentN = false

  override fun visit(
      version: Int,
      access: Int,
      name: String,
      signature: String?,
      superName: String?,
      interfaces: Array<out String>?,
  ) {
    owner = toKlasse(name, access)

    // Flags für die neue Klasse zurücksetzen
    hasKotlinMetadata = false
    hasCopyMethod = false
    hasAnyComponentN = false

    if ((access and Opcodes.ACC_ENUM) != 0 || superName == "java/lang/Enum") {
      enumOwners += name
    }
    if (superName != null && superName != "java/lang/Object") {
      edgesOut += Edge(owner, toKlasse(superName), DepKind.EXTENDS, "")
    }
    interfaces?.forEach { edgesOut += Edge(owner, toKlasse(it), DepKind.IMPLEMENTS, "") }
    signature?.let { SignatureReader(it).accept(genericTypeCollector(owner, edgesOut)) }
  }

  override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
    // NEU: Kotlin-Metadata erkennen
    if (descriptor == "Lkotlin/Metadata;") {
      hasKotlinMetadata = true
    }

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
    }
  }

  override fun visitField(
      access: Int,
      name: String,
      descriptor: String,
      signature: String?,
      value: Any?,
  ): FieldVisitor {
    if ((access and Opcodes.ACC_PUBLIC) != 0) owner.fields += name
    addTypeEdge(owner, Type.getType(descriptor), DepKind.FIELD_TYPE, edgesOut)
    signature?.let { SignatureReader(it).acceptType(genericTypeCollector(owner, edgesOut)) }
    return object : FieldVisitor(Opcodes.ASM9) {}
  }

  override fun visitMethod(
      access: Int,
      name: String,
      descriptor: String,
      signature: String?,
      exceptions: Array<out String>?,
  ): MethodVisitor {
    // data-class-typische Methoden erkennen
    if ((access and Opcodes.ACC_PUBLIC) != 0) {
      if (name == "copy") hasCopyMethod = true
      // componentN(): startet mit "component" und hat 0 Parameter
      if (name.startsWith("component") && descriptor.startsWith("()")) {
        hasAnyComponentN = true
      }
    }

    if (
        (access and Opcodes.ACC_PUBLIC) != 0 &&
            !isIgnoredDeclaredMethod(owner.fullname.replace('.', '/'), name, descriptor)
    ) {
      owner.methods += name
    }
    addMethodDescEdges(owner, descriptor, edgesOut)
    signature?.let { SignatureReader(it).accept(genericTypeCollector(owner, edgesOut)) }
    exceptions?.forEach { edgesOut += Edge(owner, toKlasse(it), DepKind.METHOD_RET, "") }

    return object : MethodVisitor(Opcodes.ASM9) {
      override fun visitTypeInsn(opcode: Int, type: String) {
        edgesOut += Edge(owner, toKlasse(type), DepKind.INSN_TYPE, "")
      }

      override fun visitFieldInsn(opcode: Int, owner2: String, name: String, desc: String) {
        edgesOut += Edge(owner, toKlasse(owner2), DepKind.FIELD_ACCESS, name)
        addTypeEdge(owner, Type.getType(desc), DepKind.FIELD_TYPE, edgesOut)
      }

      override fun visitMethodInsn(
          opcode: Int,
          owner2: String,
          name: String,
          desc: String,
          itf: Boolean,
      ) {
        if (!isIgnoredCall(owner2, name, desc, itf, opcode)) {
          edgesOut += Edge(owner, toKlasse(owner2), DepKind.METHOD_CALL, name)
        }
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
        addMethodDescEdges(owner, desc, edgesOut)
        edgesOut += Edge(owner, toKlasse(bsm.owner), DepKind.METHOD_CALL, name)
        bsmArgs.forEach {
          when (it) {
            is Type -> addTypeEdge(owner, it, DepKind.INSN_TYPE, edgesOut)
            is Handle -> edgesOut += Edge(owner, toKlasse(it.owner), DepKind.METHOD_CALL, name)
          }
        }
      }

      override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
        if (type != null) edgesOut += Edge(owner, toKlasse(type), DepKind.INSN_TYPE, name)
      }
    }
  }

  // Am Ende des Klassenbesuchs ggf. in RECORD umstufen
  override fun visitEnd() {
    // Nur „normale“ Klassen anfassen, keine Interfaces/Enums/Annotationen/Records
    if (owner.klassenart == Klassenart.CLASS || owner.klassenart == Klassenart.ABSTRACT_CLASS) {
      if (hasKotlinMetadata && hasCopyMethod && hasAnyComponentN) {
        owner.klassenart = Klassenart.RECORD
      }
    }
    super.visitEnd()
  }

  private fun isObjectMethod(name: String, desc: String): Boolean =
      (name == "hashCode" && desc == "()I") ||
          (name == "toString" && desc == "()Ljava/lang/String;") ||
          (name == "equals" && desc == "(Ljava/lang/Object;)Z")

  private fun isEnumSynthetic(ownerInternal: String, name: String, desc: String): Boolean {
    if (ownerInternal !in enumOwners) return false
    // Klassische JVM-Enum-Helpers
    if (name == "values" && desc.startsWith("()[L")) return true
    if (name == "valueOf" && desc.startsWith("(Ljava/lang/String;)")) return true
    // Kotlin 1.8+: EnumEntries
    if (name == "getEntries" && desc == "()Lkotlin/enums/EnumEntries;") return true
    // Optional, falls du noch mehr Enum-Basics ausblenden willst:
    // if (name == "ordinal" && desc == "()I") return true
    // if (name == "name" && desc == "()Ljava/lang/String;") return true
    return false
  }

  private fun isIgnoredDeclaredMethod(ownerInternal: String, name: String, desc: String): Boolean =
      isObjectMethod(name, desc) || isEnumSynthetic(ownerInternal, name, desc)

  private fun isIgnoredCall(
      ownerInternal: String,
      name: String,
      desc: String,
      itf: Boolean,
      opcode: Int,
  ): Boolean {
    // 1) Object-Basics (auch wenn über Interface-Typ aufgerufen)
    if (isObjectMethod(name, desc)) return true
    // 2) Enum-Synthetik (static calls auf die Enum-Klasse)
    if (isEnumSynthetic(ownerInternal, name, desc)) return true
    // 3) Übliche Utility-Hash-Aufrufe (optional)
    if (opcode == Opcodes.INVOKESTATIC) {
      if (
          ownerInternal == "java/util/Objects" &&
              name == "hashCode" &&
              desc == "(Ljava/lang/Object;)I"
      )
          return true
      if (ownerInternal == "java/util/Arrays" && name == "hashCode")
          return true // diverse Deskriptoren
    }
    return false
  }

  private fun toKlasse(internal: String, access: Int = 0): Klasse {
    val fullname = internal.replace('/', '.').substringBefore("$")
    val klassenart = toKlassenart(access)
    val container = Containers.getContainerOfClass(fullname)
    return containers
        .computeIfAbsent(container) { mutableMapOf() }
        .computeIfAbsent(fullname) { Klasse(container, fullname, klassenart) }
        .apply { if (access > 0 && !internal.contains("$")) this.klassenart = klassenart }
  }

  private fun toKlassenart(access: Int): Klassenart =
      when {
        (access and Opcodes.ACC_ANNOTATION) != 0 -> Klassenart.ANNOTATION
        (access and Opcodes.ACC_INTERFACE) != 0 -> Klassenart.INTERFACE
        (access and Opcodes.ACC_ENUM) != 0 -> Klassenart.ENUM
        (access and Opcodes.ACC_RECORD) != 0 -> Klassenart.RECORD
        (access and Opcodes.ACC_ABSTRACT) != 0 -> Klassenart.ABSTRACT_CLASS
        else -> Klassenart.CLASS
      }

  private fun addTypeEdge(
      from: Klasse,
      asmType: Type,
      kind: DepKind,
      sink: MutableSet<Edge>,
  ) {
    when (asmType.sort) {
      Type.ARRAY -> addTypeEdge(from, asmType.elementType, kind, sink)
      Type.OBJECT -> sink += Edge(from, toKlasse(asmType.internalName), kind, "")
      else -> {}
    }
  }

  private fun addMethodDescEdges(from: Klasse, desc: String, sink: MutableSet<Edge>) {
    Type.getArgumentTypes(desc).forEach { addTypeEdge(from, it, DepKind.METHOD_PARAM, sink) }
    addTypeEdge(from, Type.getReturnType(desc), DepKind.METHOD_RET, sink)
  }

  private fun genericTypeCollector(from: Klasse, sink: MutableSet<Edge>) =
      object : SignatureVisitor(Opcodes.ASM9) {

        // Merkt sich Bounds von Typparametern: T -> [java/util/List, java/lang/Number, ...]
        private val typeVarBounds = mutableMapOf<String, MutableSet<String>>()
        private var currentTypeVar: String? = null

        private fun addGenericEdge(internalName: String) {
          sink += Edge(from, toKlasse(internalName), DepKind.GENERIC, "")
        }

        // ---- Formale Typparameter und ihre Bounds (z. B. <T:Ljava/lang/Number;>) ----
        override fun visitFormalTypeParameter(name: String) {
          currentTypeVar = name
          typeVarBounds.putIfAbsent(name, mutableSetOf())
        }

        override fun visitClassBound(): SignatureVisitor =
            object : SignatureVisitor(Opcodes.ASM9) {
              override fun visitClassType(name: String) {
                typeVarBounds.getValue(currentTypeVar!!).add(name)
                addGenericEdge(name)
              }

              override fun visitTypeVariable(name: String) {
                // Falls ein Bound selbst ein Typparameter ist, nimm dessen Bounds
                typeVarBounds[name]?.forEach { addGenericEdge(it) }
              }
            }

        override fun visitInterfaceBound(): SignatureVisitor = visitClassBound()

        // ---- Verwendung eines Typparameters (z. B. List<T>) -> auf Bounds zeigen ----
        override fun visitTypeVariable(name: String) {
          val bounds = typeVarBounds[name]
          if (bounds.isNullOrEmpty()) {
            // Keine Info zu T vorhanden – nichts zu verbinden
            return
          }
          bounds.forEach { addGenericEdge(it) }
        }

        // ---- „Normale“ Klassentypen in Signaturen (inkl. Raw- und Argument-Typen) ----
        override fun visitClassType(name: String) {
          // name ist internalName, z. B. java/util/List
//          addGenericEdge(name)
        }

        // ---- Methodensignaturen: Exceptions können generisch sein ----
        override fun visitExceptionType(): SignatureVisitor =
            object : SignatureVisitor(Opcodes.ASM9) {
              override fun visitClassType(name: String) {
                // Du nutzt für exceptions bereits METHOD_RET – bleiben wir konsistent
                sink += Edge(from, toKlasse(name), DepKind.METHOD_RET, "")
              }
            }
      }
}
