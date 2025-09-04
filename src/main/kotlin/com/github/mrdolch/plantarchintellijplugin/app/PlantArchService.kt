package com.github.mrdolch.plantarchintellijplugin.app

import com.github.mrdolch.plantarchintellijplugin.diagram.command.PersistentSequentialPlantArchClient
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class PlantArchService(project: Project) : Disposable {
  override fun dispose() {
    PersistentSequentialPlantArchClient.shutdown()
  }
}
