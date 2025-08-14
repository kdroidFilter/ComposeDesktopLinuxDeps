package io.github.kdroidfilter.compose.linux.packagedeps

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Test
import java.io.File

class PluginTest {

    @Test
    fun `plugin is applied correctly to the project`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.kdroidfilter.compose.linux.packagedeps")

        assert(project.tasks.getByName("debInjectDepends") is DebInjectDependsTask)
    }

    @Test
    fun `extension linuxDebConfig is created correctly`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.kdroidfilter.compose.linux.packagedeps")

        Assert.assertNotNull(project.extensions.getByName("linuxDebConfig"))
    }

    @Test
    fun `parameters are passed correctly from extension to task`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.kdroidfilter.compose.linux.packagedeps")
        val tmpDebDir = File(project.projectDir, "deb-dir").apply { mkdirs() }
        (project.extensions.getByName("linuxDebConfig") as LinuxDebConfigExtension).apply {
            debDepends.set(listOf("libqt5widgets5t64", "libx11-6"))
            debDirectory.set(tmpDebDir)
            packageTaskNames.set(listOf("packageDeb", "packageReleaseDeb"))
        }

        val task = project.tasks.getByName("debInjectDepends") as DebInjectDependsTask

        Assert.assertEquals(listOf("libqt5widgets5t64", "libx11-6"), task.debDepends.get())
        Assert.assertEquals(tmpDebDir, task.debDirectory.get().asFile)
    }
}
