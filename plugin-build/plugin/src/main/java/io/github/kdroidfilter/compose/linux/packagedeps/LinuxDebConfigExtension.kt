package io.github.kdroidfilter.compose.linux.packagedeps

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

@Suppress("UnnecessaryAbstractClass")
abstract class LinuxDebConfigExtension @Inject constructor(project: Project) {
    // Exposed properties for plugin consumers
    abstract val debDepends: ListProperty<String>
    abstract val packageTaskNames: ListProperty<String>
    abstract val debDirectory: DirectoryProperty
    abstract val startupWMClass: Property<String>
    abstract val enableT64AlternativeDeps: Property<Boolean>

    init {
        val objects = project.objects

        // List of Debian package dependencies to inject into the generated .deb
        // Supports alternative dependencies by using the Debian control syntax within an entry, e.g.: "libA | libB"
        debDepends.convention(emptyList())

        // Names of packaging tasks to hook after (finalizedBy). Default: jpackage Compose tasks.
        packageTaskNames.convention(listOf("packageDeb", "packageReleaseDeb"))

        // Directory where .deb files are generated (by default, Compose Multiplatform layout)
        debDirectory.convention(project.layout.buildDirectory.dir("compose/binaries/main/deb"))

        // StartupWMClass that should be written into the generated .desktop file(s)
        // Default matches Kotlin default main class for top-level main: MainClassKt
        startupWMClass.convention("MainClassKt")

        // When true, replace certain Ubuntu 24 t64 deps with alternatives for backward compatibility.
        // libasound2t64 -> "libasound2t64 | libasound2"
        // libpng16-16t64 -> "libpng16-16t64 | libpng16-16"
        enableT64AlternativeDeps.convention(false)
    }

    /**
     * Convenience method to add dependencies required by compose-desktop-tray (Qt5 + dbusmenu).
     * Always adds the t64-first alternatives for better cross-distro compatibility.
     *
     * You can still fully override with: debDepends.set(listOf("..."))
     */
    fun addComposeNativeTrayDeps() {
        debDepends.addAll(
            listOf(
                "libqt5core5t64 | libqt5core5a",
                "libqt5gui5t64 | libqt5gui5",
                "libqt5widgets5t64 | libqt5widgets5",
                "libdbusmenu-qt5-2"
            )
        )
    }
}
