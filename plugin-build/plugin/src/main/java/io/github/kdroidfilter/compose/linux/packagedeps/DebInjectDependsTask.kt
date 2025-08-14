package io.github.kdroidfilter.compose.linux.packagedeps

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class DebInjectDependsTask : DefaultTask() {
    init {
        description = "Inject Debian Depends into jpackage-generated .deb files"
        group = "distribution"
    }

    @get:Input
    abstract val debDepends: ListProperty<String>

    @get:InputDirectory
    abstract val debDirectory: DirectoryProperty

    @TaskAction
    fun injectDepends() {
        // 0) Check tool availability
        val check = project.exec { execSpec ->
            execSpec.isIgnoreExitValue = true
            execSpec.commandLine("bash", "-lc", "command -v dpkg-deb >/dev/null 2>&1")
        }
        if (check.exitValue != 0) {
            error("dpkg-deb is required (Debian/Ubuntu). Install it with: sudo apt-get install dpkg-dev")
        }

        val deps = debDepends.getOrElse(emptyList())
        if (deps.isEmpty()) {
            logger.lifecycle("No Debian dependencies configured (debDepends is empty). Nothing to inject.")
            return
        }

        // 1) Find the most recent .deb
        val debDirFile: File = debDirectory.get().asFile
        if (!debDirFile.exists()) {
            error(".deb directory not found: ${debDirFile.absolutePath} — run first: ./gradlew packageDeb")
        }
        val debFile = debDirFile.listFiles { f -> f.isFile && f.name.endsWith(".deb") }
            ?.maxByOrNull { it.lastModified() }
            ?: error("No .deb file found in ${debDirFile.absolutePath} — run first: ./gradlew packageDeb")

        // 2) Extract
        val workDir = File(project.buildDir, "deb-edit").apply { deleteRecursively(); mkdirs() }
        project.exec { execSpec -> execSpec.commandLine("dpkg-deb", "-R", debFile.absolutePath, workDir.absolutePath) }

        // 3) Modify control
        val controlFile = File(workDir, "DEBIAN/control")
        if (!controlFile.exists()) error("Control file not found in extracted package: ${controlFile.absolutePath}")

        val controlText = controlFile.readText()
        val depList = deps.joinToString(", ")
        val dependsLine = "Depends: $depList"

        val newControl = if (Regex("^Depends:", RegexOption.MULTILINE).containsMatchIn(controlText)) {
            controlText.replace(Regex("^Depends:\\s*(.*)$", RegexOption.MULTILINE)) { m ->
                val existing = m.groupValues[1]
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toMutableSet()
                existing.addAll(deps)
                "Depends: " + existing.joinToString(", ")
            }
        } else {
            controlText.trimEnd() + "\n" + dependsLine + "\n"
        }
        controlFile.writeText(newControl)

        // 4) Repack (overwrite original)
        project.exec { execSpec -> execSpec.commandLine("dpkg-deb", "-Zxz", "-b", workDir.absolutePath, debFile.absolutePath) }
        logger.lifecycle("✅ Injected into ${debFile.name}: $dependsLine")
    }
}
