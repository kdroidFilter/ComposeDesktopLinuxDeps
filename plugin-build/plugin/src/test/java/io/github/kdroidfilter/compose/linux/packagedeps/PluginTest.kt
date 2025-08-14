package io.github.kdroidfilter.compose.linux.packagedeps

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Test
import java.io.File

class PluginTest {

    @Test
    fun `plugin registers per-variant tasks`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.kdroidfilter.compose.linux.packagedeps")

        assert(project.tasks.getByName("debInjectDependsPackageDeb") is DebInjectDependsTask)
        assert(project.tasks.getByName("debInjectDependsPackageReleaseDeb") is DebInjectDependsTask)
    }

    @Test
    fun `extension linuxDebConfig is created correctly`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.kdroidfilter.compose.linux.packagedeps")

        Assert.assertNotNull(project.extensions.getByName("linuxDebConfig"))
    }

    @Test
    fun `parameters are passed correctly and directories are variant-specific`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.kdroidfilter.compose.linux.packagedeps")
        val tmpDebDir = File(project.projectDir, "deb-dir").apply { mkdirs() }
        (project.extensions.getByName("linuxDebConfig") as LinuxDebConfigExtension).apply {
            debDepends.set(listOf("libqt5widgets5t64", "libx11-6"))
            debDirectory.set(tmpDebDir)
            packageTaskNames.set(listOf("packageDeb", "packageReleaseDeb"))
        }

        val debTask = project.tasks.getByName("debInjectDependsPackageDeb") as DebInjectDependsTask
        val releaseTask = project.tasks.getByName("debInjectDependsPackageReleaseDeb") as DebInjectDependsTask

        Assert.assertEquals(listOf("libqt5widgets5t64", "libx11-6"), debTask.debDepends.get())
        Assert.assertEquals(listOf("libqt5widgets5t64", "libx11-6"), releaseTask.debDepends.get())
        // deb task uses extension-provided directory
        Assert.assertEquals(tmpDebDir, debTask.debDirectory.get().asFile)
        // release task enforces main-release directory
        val expectedReleaseDir = File(project.buildDir, "compose/binaries/main-release/deb")
        Assert.assertEquals(expectedReleaseDir, releaseTask.debDirectory.get().asFile)
    }
}
