package com.github.mrdolch.plantarchintellijplugin.configuration

import com.github.mrdolch.plantarchintellijplugin.asm.ShowPackages
import com.github.mrdolch.plantarchintellijplugin.asm.UseByMethodNames

class Configuration {
  var showMethodNames: UseByMethodNames = UseByMethodNames.FOCUSED_ONLY
  var showPackages: ShowPackages = ShowPackages.NONE
  var plantumlOptions: String =
      """
skinparam linetype polyline
!pragma layout smetana
<style>
  .inFocus {
    BackgroundColor LightYellow
    BorderColor GoldenRod
  }
  .annotation {
  }
  .class {
  }
  .enum {
    BackgroundColor AliceBlue
    BorderColor SteelBlue
  }
  .record {
    BackgroundColor Lavender
    BorderColor Indigo
  }
  .interface {
  }
  .abstract {
  }
  .Serializable {
    BackgroundColor LightCyan
    BorderColor Teal
  }
</style>
         
hide  <<inFocus>> stereotype
hide  <<annotation>> stereotype
hide  <<class>> stereotype
hide  <<enum>> stereotype
hide  <<record>> stereotype
hide  <<interface>> stereotype
hide  <<abstract>> stereotype
      """
          .trimIndent()
  var markerClasses: String =
      """
              Serializable
              Stateless
      """
          .trimIndent()
}
