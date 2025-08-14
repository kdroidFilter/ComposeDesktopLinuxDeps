package io.github.kdroidfilter.compose.linux.packagedeps

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

abstract class DebInjectDependsTask : DefaultTask() {
    @get:Inject
    protected abstract val execOperations: ExecOperations

    init {
        description = "Inject Debian Depends into jpackage-generated .deb files"
        group = "distribution"
    }

    @get:Input
    abstract val debDepends: ListProperty<String>

    @get:Input
    abstract val startupWMClass: Property<String>

    @get:InputDirectory
    abstract val debDirectory: DirectoryProperty

    @TaskAction
    fun injectDepends() {
        ensureDpkgDebAvailable()

        val deps = debDepends.getOrElse(emptyList())
        val debFile = findLatestDeb(debDirectory.get().asFile)
        val workDir = extractDebToWorkDir(debFile)

        updateControlDependsIfNeeded(workDir, debFile, deps)
        updateDesktopStartupWMClass(workDir, startupWMClass.getOrElse("MainClassKt"))

        repackDeb(workDir, debFile)
    }

    private fun ensureDpkgDebAvailable() {
        val result = execOperations.exec { execSpec ->
            execSpec.isIgnoreExitValue = true
            execSpec.commandLine("bash", "-lc", "command -v dpkg-deb >/dev/null 2>&1")
        }
        if (result.exitValue != 0) {
            error("dpkg-deb is required (Debian/Ubuntu). Install it with: sudo apt-get install dpkg-dev")
        }
    }

    private fun findLatestDeb(dir: File): File {
        if (!dir.exists()) {
            error(".deb directory not found: ${dir.absolutePath} — run first: ./gradlew packageDeb")
        }
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".deb") }
            ?.maxByOrNull { it.lastModified() }
            ?: error("No .deb file found in ${dir.absolutePath} — run first: ./gradlew packageDeb")
    }

    private fun extractDebToWorkDir(debFile: File): File {
        val workDir = project.layout.buildDirectory.dir("deb-edit").get().asFile
        workDir.deleteRecursively()
        workDir.mkdirs()
        execOperations.exec { execSpec ->
            execSpec.commandLine("dpkg-deb", "-R", debFile.absolutePath, workDir.absolutePath)
        }
        return workDir
    }

    private fun updateControlDependsIfNeeded(workDir: File, debFile: File, deps: List<String>) {
        if (deps.isEmpty()) {
            logger.lifecycle("No Debian dependencies configured (debDepends is empty). Skipping Depends injection.")
            return
        }
        val controlFile = File(workDir, "DEBIAN/control")
        if (!controlFile.exists()) error("Control file not found in extracted package: ${controlFile.absolutePath}")

        val controlText = controlFile.readText()
        val dependsLine = "Depends: " + deps.joinToString(", ")

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
        logger.lifecycle("✅ Injected Debian Depends into ${debFile.name}: $dependsLine")
    }

    private fun updateDesktopStartupWMClass(workDir: File, wmClassRaw: String) {
        val wmClass = wmClassRaw.replace('.', '-')
        val desktopFiles = workDir.walkTopDown().filter { it.isFile && it.name.endsWith(".desktop") }.toList()
        if (desktopFiles.isEmpty()) {
            logger.lifecycle("No .desktop files found in extracted package to update StartupWMClass.")
            return
        }
        desktopFiles.forEach { file ->
            val original = file.readText()
            val hasKey = Regex("^StartupWMClass=", RegexOption.MULTILINE).containsMatchIn(original)
            val updated = if (hasKey) {
                original.replace(Regex("^StartupWMClass=.*$", RegexOption.MULTILINE), "StartupWMClass=$wmClass")
            } else if (original.contains("[Desktop Entry]")) {
                original.replaceFirst("[Desktop Entry]", "[Desktop Entry]\nStartupWMClass=$wmClass")
            } else {
                original.trimEnd() + "\nStartupWMClass=$wmClass\n"
            }
            if (updated != original) {
                file.writeText(updated)
                logger.lifecycle("✅ Updated StartupWMClass in ${file.relativeTo(workDir)} -> $wmClass")
            } else {
                logger.lifecycle("No changes needed for ${file.relativeTo(workDir)} (StartupWMClass already set to desired value)")
            }
        }
    }

    private fun repackDeb(workDir: File, debFile: File) {
        val supportsRootOwnerGroup = execOperations.exec { execSpec ->
            execSpec.isIgnoreExitValue = true
            execSpec.commandLine("bash", "-lc", "dpkg-deb --help | grep -q -- --root-owner-group")
        }.exitValue == 0

        val cmd = mutableListOf("dpkg-deb", "-Zxz", "-b")
        if (supportsRootOwnerGroup) {
            cmd.add("--root-owner-group")
            logger.info("dpkg-deb supports --root-owner-group; enabling rootless build ownership normalization")
        } else {
            logger.info("dpkg-deb does not support --root-owner-group; proceeding without it")
        }
        cmd.add(workDir.absolutePath)
        cmd.add(debFile.absolutePath)
        execOperations.exec { execSpec -> execSpec.commandLine(cmd) }
        logger.lifecycle("✅ Repacked ${debFile.name} with updated metadata")
    }
}
