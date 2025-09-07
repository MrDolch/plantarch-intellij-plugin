package com.github.mrdolch.plantarchintellijplugin.configuration

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.PROJECT)
@State(name = "PlantArchLiveAnalyzerSettings", storages = [Storage("plantArch-live-analyzer.xml")])
class PersistConfigurationService : PersistentStateComponent<Configuration> {
  private var configuration = Configuration()
  override fun getState(): Configuration = configuration
  override fun loadState(configuration: Configuration) {
    this.configuration = configuration
  }

}