package io.github.kdroidfilter.compose.linux.packagedeps

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
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
    @get:Inject
    protected abstract val layout: ProjectLayout

    init {
        description = "Inject Debian Depends into jpackage-generated .deb files"
        group = "distribution"
    }

    @get:Input
    abstract val debDepends: ListProperty<String>

    @get:Input
    abstract val startupWMClass: Property<String>

    @get:Input
    abstract val enableT64AlternativeDeps: Property<Boolean>

    // Mark as internal to avoid Gradle input validation on hosts or builds
    // where the directory doesn't exist (e.g., macOS/Windows or before packaging).
    // We perform existence checks at runtime and skip gracefully.
    @get:org.gradle.api.tasks.Internal
    abstract val debDirectory: DirectoryProperty

    @TaskAction
    fun injectDepends() {
        // No-op on non-Linux hosts to provide a safe fallback
        val osName = System.getProperty("os.name").orEmpty()
        val isLinux = osName.lowercase().contains("linux")
        if (!isLinux) {
            logger.lifecycle("Host OS is not Linux (detected: '$osName'). Skipping Debian dependency injection task.")
            return
        }

        ensureDpkgDebAvailable()

        val baseDeps = debDepends.getOrElse(emptyList())
        val deps = baseDeps
        val dir = debDirectory.get().asFile
        if (!dir.exists()) {
            logger.lifecycle("No .deb directory found at: ${dir.absolutePath}. Skipping injection. Run './gradlew packageDeb' first if packaging.")
            return
        }
        val debFile = findLatestDeb(dir)
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
        val workDir = layout.buildDirectory.dir("deb-edit").get().asFile
        workDir.deleteRecursively()
        workDir.mkdirs()
        execOperations.exec { execSpec ->
            execSpec.commandLine("dpkg-deb", "-R", debFile.absolutePath, workDir.absolutePath)
        }
        return workDir
    }

    private fun updateControlDependsIfNeeded(workDir: File, debFile: File, deps: List<String>) {
        val controlFile = File(workDir, "DEBIAN/control")
        if (!controlFile.exists()) error("Control file not found in extracted package: ${controlFile.absolutePath}")

        var controlText = controlFile.readText()
        var changedByRewrite = false

        if (enableT64AlternativeDeps.getOrElse(false)) {
            val rewritten = rewriteExistingT64Alternatives(controlText)
            if (rewritten != controlText) {
                controlText = rewritten
                changedByRewrite = true
            }
        }

        if (deps.isNotEmpty()) {
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
        } else if (changedByRewrite) {
            controlFile.writeText(controlText)
            logger.lifecycle("✅ Rewrote existing t64 dependencies in ${debFile.name} Depends line for compatibility")
        } else {
            logger.lifecycle("No Debian dependencies configured and no rewrite needed; leaving control file unchanged.")
        }
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

    private fun rewriteExistingT64Alternatives(controlText: String): String {
        val dependsRegex = Regex("^Depends:\\s*(.*)$", RegexOption.MULTILINE)
        if (!dependsRegex.containsMatchIn(controlText)) return controlText
        return controlText.replace(dependsRegex) { m ->
            val line = m.groupValues[1]
            val updated = rewriteDependsLineItems(line)
            "Depends: $updated"
        }
    }

    private fun rewriteDependsLineItems(depLine: String): String {
        val items = depLine.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val updated = items.map { item ->
            if (item.contains('|')) {
                // Already an alternative dependency; do not alter ordering or versions
                item
            } else {
                // Handle t64 variants first -> add alternative with non-t64
                val asoundT64 = Regex("^libasound2t64(\\s*\\([^)]+\\))?$").matchEntire(item)
                if (asoundT64 != null) {
                    val ver = asoundT64.groupValues.getOrNull(1).orEmpty()
                    "libasound2t64$ver | libasound2"
                } else {
                    val pngT64 = Regex("^libpng16-16t64(\\s*\\([^)]+\\))?$").matchEntire(item)
                    if (pngT64 != null) {
                        val ver = pngT64.groupValues.getOrNull(1).orEmpty()
                        "libpng16-16t64$ver | libpng16-16"
                    } else {
                        // Handle non-t64 variants -> normalize to alternative with t64 first
                        val asound = Regex("^libasound2(\\s*\\([^)]+\\))?$").matchEntire(item)
                        if (asound != null) {
                            val ver = asound.groupValues.getOrNull(1).orEmpty()
                            "libasound2t64$ver | libasound2"
                        } else {
                            val png = Regex("^libpng16-16(\\s*\\([^)]+\\))?$").matchEntire(item)
                            if (png != null) {
                                val ver = png.groupValues.getOrNull(1).orEmpty()
                                "libpng16-16t64$ver | libpng16-16"
                            } else item
                        }
                    }
                }
            }
        }
        return updated.joinToString(", ")
    }
}
