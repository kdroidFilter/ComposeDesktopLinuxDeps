package io.github.kdroidfilter.compose.linux.packagedeps

import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.text.Normalizer
import javax.inject.Inject

/**
 * Prepares jpackage argument file for Linux DEB builds by:
 * - capturing the original --name value for later desktop display usage
 * - forcing a Debian-compliant package name (lowercase ASCII) via --linux-package-name
 * - optionally normalizing --name to the same sanitized slug to avoid jpackage validation errors
 */
abstract class PrepareJpackageArgsTask : DefaultTask() {
    @get:Inject
    protected abstract val layout: ProjectLayout

    /** The jpackage args basename, e.g. "packageDeb" or "packageReleaseDeb". */
    @get:Input
    abstract val argsBaseName: Property<String>

    init {
        group = "build"
        description = "Sanitize jpackage args for Debian bundler and capture display name"
    }

    @TaskAction
    fun run() {
        val osName = System.getProperty("os.name").orEmpty()
        if (!osName.lowercase().contains("linux")) {
            // Only relevant for Linux DEB packaging; no-op elsewhere
            logger.info("PrepareJpackageArgsTask: non-Linux host; skipping")
        } else {
            val base = argsBaseName.get()
            val tmpDir = layout.buildDirectory.dir("compose/tmp").get().asFile
            val argsFile = File(tmpDir, "$base.args.txt")
            if (!argsFile.exists()) {
                logger.info("PrepareJpackageArgsTask: args file not found: ${argsFile.absolutePath}; skipping")
                return
            }

            val lines = argsFile.readLines().toMutableList()
            val nameOcc = findNameOccurrence(lines)
            if (nameOcc == null) {
                logger.warn("PrepareJpackageArgsTask: could not detect --name in args; leaving file unchanged")
                return
            }
            val originalName = nameOcc.original
            val slug = toDebPackageSlug(originalName)

            // Record original display name for later .desktop patching
            File(tmpDir, "$base.original-name.txt").writeText(originalName)

            // Ensure --linux-package-name is set to the slug (add or replace)
            ensureLinuxPackageName(lines, slug)

            // Normalize --name to slug as well to avoid strict validators rejecting Unicode/uppercase
            applyName(lines, nameOcc, slug)

            argsFile.writeText(lines.joinToString(System.lineSeparator()))
            logger.lifecycle("✅ Prepared jpackage args for $base: name='$originalName' -> slug='$slug'")
        }
    }

    private data class NameOccurrence(val index: Int, val inline: Boolean, val original: String)

    private fun findNameOccurrence(lines: List<String>): NameOccurrence? {
        var i = 0
        var result: NameOccurrence? = null
        while (i < lines.size && result == null) {
            val l = lines[i].trim()
            if (l == "--name") {
                val nextIdx = i + 1
                if (nextIdx < lines.size) {
                    val original = lines[nextIdx].trim().trim('"')
                    result = NameOccurrence(nextIdx, inline = false, original = original)
                }
            } else if (l.startsWith("--name ")) {
                val original = l.removePrefix("--name ").trim().trim('"')
                result = NameOccurrence(i, inline = true, original = original)
            }
            i++
        }
        return result
    }

    private fun ensureLinuxPackageName(lines: MutableList<String>, slug: String) {
        var j = 0
        while (j < lines.size) {
            val l = lines[j].trim()
            if (l == "--linux-package-name") {
                val valIdx = j + 1
                if (valIdx < lines.size) lines[valIdx] = slug
                return
            }
            if (l.startsWith("--linux-package-name ")) {
                lines[j] = "--linux-package-name $slug"
                return
            }
            j++
        }
        // Append as separate tokens to be safe
        lines.add("--linux-package-name")
        lines.add(slug)
    }

    private fun applyName(lines: MutableList<String>, occ: NameOccurrence, slug: String) {
        if (occ.inline) {
            lines[occ.index] = "--name $slug"
        } else {
            lines[occ.index] = slug
        }
    }

    private fun toDebPackageSlug(input: String): String {
        // Normalize unicode to ASCII, then keep [a-z0-9+.-]
        val noDiacritics = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        val ascii = noDiacritics.replace("[^\\p{ASCII}]".toRegex(), "-")
        var slug = ascii.lowercase()
            .replace("[^a-z0-9+.-]".toRegex(), "-")
            .replace("-+".toRegex(), "-")
            .trim('-')
        if (slug.isBlank()) slug = "app"
        if (!slug[0].isLetterOrDigit()) slug = "app-$slug"
        return slug
    }
}
