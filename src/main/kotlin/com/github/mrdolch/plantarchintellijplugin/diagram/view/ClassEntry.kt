package com.github.mrdolch.plantarchintellijplugin.diagram.view

data class ClassEntry(
  val name: String,
  var visibility: VisibilityStatus = VisibilityStatus.MAYBE,
)

data class ContainerEntry(
  val name: String,
  val packages: List<PackageEntry>,
  var visibility: VisibilityStatus = VisibilityStatus.MAYBE,
)

data class PackageEntry(
  val name: String,
  val classes: List<ClassEntry>,
  var visibility: VisibilityStatus = VisibilityStatus.MAYBE,
)

enum class VisibilityStatus {
  IN_FOCUS,
  MAYBE,
  HIDDEN
}

fun ClassEntry.getPackageName() = name.substringBeforeLast('.')
fun ClassEntry.getSimpleName() = name.substringAfterLast('.')