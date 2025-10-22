package io.github.kdroidfilter.compose.linux.packagedeps

import org.gradle.api.Plugin
import org.gradle.api.Project

const val EXTENSION_NAME = "linuxDebConfig"
const val TASK_NAME_DEB = "debInjectDependsPackageDeb"
const val TASK_NAME_RELEASE = "debInjectDependsPackageReleaseDeb"

@Suppress("UnnecessaryAbstractClass")
abstract class LinuxDebConfigPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val isLinux = System.getProperty("os.name").orEmpty().lowercase().contains("linux")
        // Create plugin extension with configurable properties
        val extension = project.extensions.create(EXTENSION_NAME, LinuxDebConfigExtension::class.java, project)

        // Register dedicated injection tasks for each packaging variant
        val injectDebTask = project.tasks.register(TASK_NAME_DEB, DebInjectDependsTask::class.java) { task ->
            task.group = "distribution"
            task.description = "Inject Debian Depends into jpackage-generated .deb (packageDeb)"
            task.debDepends.set(extension.debDepends)
            task.startupWMClass.set(extension.startupWMClass)
            task.enableT64AlternativeDeps.set(extension.enableT64AlternativeDeps)
            // main variant keeps using the extension-configured directory (defaults to main)
            task.debDirectory.set(extension.debDirectory)
            task.jpackageArgsBaseName.set("packageDeb")
            // Gate execution on Linux hosts only
            task.onlyIf { isLinux }
        }
        val releaseDir = project.layout.buildDirectory.dir("compose/binaries/main-release/deb")
        val injectReleaseTask = project.tasks.register(TASK_NAME_RELEASE, DebInjectDependsTask::class.java) { task ->
            task.group = "distribution"
            task.description = "Inject Debian Depends into jpackage-generated .deb (packageReleaseDeb)"
            task.debDepends.set(extension.debDepends)
            task.startupWMClass.set(extension.startupWMClass)
            task.enableT64AlternativeDeps.set(extension.enableT64AlternativeDeps)
            // release variant must use main-release directory
            task.debDirectory.set(releaseDir)
            task.jpackageArgsBaseName.set("packageReleaseDeb")
            // Gate execution on Linux hosts only
            task.onlyIf { isLinux }
        }

        // Prepare jpackage args before packaging to ensure Debian-compliant names and capture display name
        val prepareDeb = project.tasks.register("prepareJpackageArgsDeb", PrepareJpackageArgsTask::class.java) { t ->
            t.argsBaseName.set("packageDeb")
            t.onlyIf { isLinux }
        }
        val prepareReleaseDeb = project.tasks.register("prepareJpackageArgsReleaseDeb", PrepareJpackageArgsTask::class.java) { t ->
            t.argsBaseName.set("packageReleaseDeb")
            t.onlyIf { isLinux }
        }

        // Hook automatically: run sanitize -> packaging -> inject
        project.afterEvaluate {
            project.tasks.matching { it.name == "packageDeb" }.configureEach { pkg ->
                pkg.dependsOn(prepareDeb)
                pkg.finalizedBy(injectDebTask)
            }
            project.tasks.matching { it.name == "packageReleaseDeb" }.configureEach { pkg ->
                pkg.dependsOn(prepareReleaseDeb)
                pkg.finalizedBy(injectReleaseTask)
            }
        }
    }
}
