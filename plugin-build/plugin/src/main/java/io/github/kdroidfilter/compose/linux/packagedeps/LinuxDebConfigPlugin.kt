package io.github.kdroidfilter.compose.linux.packagedeps

import org.gradle.api.Plugin
import org.gradle.api.Project

const val EXTENSION_NAME = "linuxDebConfig"
const val TASK_NAME = "debInjectDepends"

@Suppress("UnnecessaryAbstractClass")
abstract class LinuxDebConfigPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create plugin extension with configurable properties
        val extension = project.extensions.create(EXTENSION_NAME, LinuxDebConfigExtension::class.java, project)

        // Register the deb injection task
        val injectTaskProvider = project.tasks.register(TASK_NAME, DebInjectDependsTask::class.java) { task ->
            task.debDepends.set(extension.debDepends)
            task.debDirectory.set(extension.debDirectory)
        }

        // Hook automatically: run after packaging tasks (avoid dependsOn to prevent loops)
        project.afterEvaluate {
            val names = extension.packageTaskNames.getOrElse(listOf("packageDeb", "packageReleaseDeb"))
            project.tasks.matching { t -> names.contains(t.name) }.configureEach { t ->
                t.finalizedBy(injectTaskProvider)
            }
        }
    }
}
