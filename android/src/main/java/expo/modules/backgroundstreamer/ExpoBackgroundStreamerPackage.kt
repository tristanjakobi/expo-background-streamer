package expo.modules.backgroundstreamer

import expo.modules.kotlin.Package
import expo.modules.kotlin.modules.Module

class ExpoBackgroundStreamerPackage : Package {
    override fun createModules(): List<Module> {
        return listOf(ExpoBackgroundStreamerModule())
    }
}