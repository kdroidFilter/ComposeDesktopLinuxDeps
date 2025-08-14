package io.github.kdroidfilter.compose.linux.packagedeps

import org.gradle.api.Plugin
import org.gradle.api.Project

const val EXTENSION_NAME = "linuxDebConfig"
const val TASK_NAME_DEB = "debInjectDependsPackageDeb"
const val TASK_NAME_RELEASE = "debInjectDependsPackageReleaseDeb"

@Suppress("UnnecessaryAbstractClass")
abstract class LinuxDebConfigPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create plugin extension with configurable properties
        val extension = project.extensions.create(EXTENSION_NAME, LinuxDebConfigExtension::class.java, project)

        // Register dedicated injection tasks for each packaging variant
        val injectDebTask = project.tasks.register(TASK_NAME_DEB, DebInjectDependsTask::class.java) { task ->
            task.group = "distribution"
            task.description = "Inject Debian Depends into jpackage-generated .deb (packageDeb)"
            task.debDepends.set(extension.debDepends)
            task.startupWMClass.set(extension.startupWMClass)
            // main variant keeps using the extension-configured directory (defaults to main)
            task.debDirectory.set(extension.debDirectory)
        }
        val releaseDir = project.layout.buildDirectory.dir("compose/binaries/main-release/deb")
        val injectReleaseTask = project.tasks.register(TASK_NAME_RELEASE, DebInjectDependsTask::class.java) { task ->
            task.group = "distribution"
            task.description = "Inject Debian Depends into jpackage-generated .deb (packageReleaseDeb)"
            task.debDepends.set(extension.debDepends)
            task.startupWMClass.set(extension.startupWMClass)
            // release variant must use main-release directory
            task.debDirectory.set(releaseDir)
        }

        // Hook automatically: run after the corresponding packaging tasks (avoid dependsOn to prevent loops)
        project.afterEvaluate {
            project.tasks.matching { it.name == "packageDeb" }.configureEach { pkg ->
                pkg.finalizedBy(injectDebTask)
            }
            project.tasks.matching { it.name == "packageReleaseDeb" }.configureEach { pkg ->
                pkg.finalizedBy(injectReleaseTask)
            }
        }
    }
}
