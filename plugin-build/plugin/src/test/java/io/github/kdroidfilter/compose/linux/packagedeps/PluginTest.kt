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
            startupWMClass.set("CustomMainClass")
            enableT64AlternativeDeps.set(true)
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

        // StartupWMClass should be propagated to both tasks
        Assert.assertEquals("CustomMainClass", debTask.startupWMClass.get())
        Assert.assertEquals("CustomMainClass", releaseTask.startupWMClass.get())

        // T64 alternative deps flag should propagate to both tasks
        Assert.assertTrue(debTask.enableT64AlternativeDeps.get())
        Assert.assertTrue(releaseTask.enableT64AlternativeDeps.get())
    }

    @Test
    fun `addComposeNativeTrayDeps always adds t64-first alternatives`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.kdroidfilter.compose.linux.packagedeps")
        val ext = project.extensions.getByName("linuxDebConfig") as LinuxDebConfigExtension

        // Call and verify fixed list regardless of flag
        ext.addComposeNativeTrayDeps()
        // Flip the flag to ensure it does not affect the result
        ext.enableT64AlternativeDeps.set(false)

        val expected = listOf(
            "libqt5core5t64 | libqt5core5a",
            "libqt5gui5t64 | libqt5gui5",
            "libqt5widgets5t64 | libqt5widgets5",
            "libdbusmenu-qt5-2"
        )
        Assert.assertEquals(expected, ext.debDepends.get())
    }

    @Test
    fun `manual override of debDepends still works after addComposeNativeTrayDeps`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.kdroidfilter.compose.linux.packagedeps")
        val ext = project.extensions.getByName("linuxDebConfig") as LinuxDebConfigExtension

        ext.addComposeNativeTrayDeps()
        // User overrides with their own set
        val custom = listOf("another dependencies")
        ext.debDepends.set(custom)

        val debTask = project.tasks.getByName("debInjectDependsPackageDeb") as DebInjectDependsTask
        Assert.assertEquals(custom, debTask.debDepends.get())
    }
}
