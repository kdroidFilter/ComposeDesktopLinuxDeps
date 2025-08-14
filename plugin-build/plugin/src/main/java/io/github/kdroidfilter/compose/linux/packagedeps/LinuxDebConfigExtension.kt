package io.github.kdroidfilter.compose.linux.packagedeps

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

@Suppress("UnnecessaryAbstractClass")
abstract class LinuxDebConfigExtension
    @Inject
    constructor(
        project: Project,
    ) {
        val objects = project.objects

        // List of Debian package dependencies to inject into the generated .deb
        val debDepends: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

        // Names of packaging tasks to hook after (finalizedBy). Default: jpackage Compose tasks.
        val packageTaskNames: ListProperty<String> = objects.listProperty(String::class.java)
            .convention(listOf("packageDeb", "packageReleaseDeb"))

        // Directory where .deb files are generated (by default, Compose Multiplatform layout)
        val debDirectory: DirectoryProperty = objects.directoryProperty()
            .convention(project.layout.buildDirectory.dir("compose/binaries/main/deb"))

        // StartupWMClass that should be written into the generated .desktop file(s)
        // Default matches Kotlin default main class for top-level main: MainClassKt
        val startupWMClass: Property<String> = objects.property(String::class.java).convention("MainClassKt")
    }
