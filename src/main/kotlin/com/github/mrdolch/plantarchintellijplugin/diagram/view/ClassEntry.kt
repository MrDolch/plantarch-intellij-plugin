package com.github.mrdolch.plantarchintellijplugin.diagram.view

sealed class Entry(
    open val name: String,
    open var visibility: VisibilityStatus = VisibilityStatus.MAYBE,
)

data class ClassEntry(
    override val name: String,
    override var visibility: VisibilityStatus = VisibilityStatus.MAYBE,
) : Entry(name, visibility)

data class ContainerEntry(
    override val name: String,
    override var visibility: VisibilityStatus = VisibilityStatus.MAYBE,
    val packages: List<PackageEntry>,
) : Entry(name, visibility)

data class PackageEntry(
    override val name: String,
    override var visibility: VisibilityStatus = VisibilityStatus.MAYBE,
    val classes: List<ClassEntry>,
) : Entry(name, visibility)

enum class VisibilityStatus {
  IN_CLASSPATH,
  IN_FOCUS,
  MAYBE,
  HIDDEN,
}

fun ClassEntry.getPackageName() = name.substringBeforeLast('.')

fun ClassEntry.getSimpleName() = name.substringAfterLast('.')
