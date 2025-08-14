# Compose Desktop Linux Package Deps (DEB)

Gradle plugin that injects Debian package dependencies into jpackage-generated `.deb` files produced by Compose Multiplatform (Compose Desktop).

It edits the DEBIAN/control file of the latest generated .deb to add or merge a `Depends:` line using the dependencies you configure in Gradle.

## Why?
Many Compose Desktop apps require system libraries at runtime (for example, Qt or X11 libs). When distributing a `.deb`, you should declare these as Debian dependencies so that `apt` installs them automatically for your users. This plugin automates that step right after jpackage builds the `.deb`.

## Features
- Configurable list of Debian packages to inject as `Depends:`.
- Automatically runs after packaging tasks:
  - `packageDeb` -> edits `.deb` in `build/compose/binaries/main/deb`.
  - `packageReleaseDeb` -> edits `.deb` in `build/compose/binaries/main-release/deb`.
- Merges with existing `Depends:` if present (de-duplicates entries).
- Fixes Linux dock/taskbar icon matching by ensuring the generated `.desktop` file contains `StartupWMClass`.
- New setting: `startupWMClass` lets you control which WM class is written (defaults to `MainClassKt`).

## Requirements
- A Debian/Ubuntu-like environment with `dpkg-deb` installed:
  - `sudo apt-get install dpkg-dev`
- Your project uses Compose Multiplatform’s jpackage tasks (`packageDeb` / `packageReleaseDeb`).

## Plugin ID
```
io.github.kdroidfilter.compose.linux.packagedeps
```

## Getting started

### Apply the plugin (using included build in this repo)
This repository is set up as a composite build. In your settings.gradle.kts, you will find:
```kotlin
includeBuild("plugin-build")
```
Then in the module where you package your app:
```kotlin
plugins {
    id("io.github.kdroidfilter.compose.linux.packagedeps")
}

linuxDebConfig {
    debDepends.set(listOf("libqt5widgets5t64"))
    // Optional: ensure correct dock icon matching on Linux
    startupWMClass.set("MainClassKt")
}
```

### If using the plugin from a published repository
When the plugin is published, simply apply it by id and set the version, then configure the extension the same way as above.

## Configuration (extension linuxDebConfig)
- `debDepends: List<String>`
  - The Debian packages you want to add to the `Depends:` field (e.g., `libqt5widgets5t64`, `libx11-6`).
- `debDirectory: Directory`
  - Directory where `.deb` files are generated for the `packageDeb` variant. Defaults to `build/compose/binaries/main/deb`.
  - Note: For `packageReleaseDeb`, the plugin automatically uses `build/compose/binaries/main-release/deb` regardless of `debDirectory`.
- `startupWMClass: String`
  - The value to write into the generated `.desktop` file(s) as `StartupWMClass`. Defaults to `MainClassKt` (which matches a top-level `fun main()` in `MainClass.kt`).
  - Note: For compatibility, the plugin replaces any `.` with `-` before writing the value (e.g., `com.example.App` becomes `com-example-App`).
  - Tip: If your dock icon doesn’t show correctly on GNOME/KDE, set this to your app’s window class.
- `packageTaskNames: List<String>`
  - Names of packaging tasks to consider. Defaults to `listOf("packageDeb", "packageReleaseDeb")`. The plugin wires itself to these tasks automatically.

## Tasks created by the plugin
- `debInjectDependsPackageDeb`
  - Injects/merges `Depends:` into the latest `.deb` under `build/compose/binaries/main/deb`.
- `debInjectDependsPackageReleaseDeb`
  - Injects/merges `Depends:` into the latest `.deb` under `build/compose/binaries/main-release/deb`.

These tasks are automatically run after their corresponding packaging tasks. You can also execute them manually if needed.

## Example
```kotlin
plugins {
    java
    id("io.github.kdroidfilter.compose.linux.packagedeps")
}

linuxDebConfig {
    debDepends.set(listOf(
        "libqt5widgets5t64",
        "libx11-6"
    ))

    // Optional: Set WM class to ensure dock icon matches your running window
    // Default is "MainClassKt" (top-level main function in MainClass.kt)
    startupWMClass.set("YourMainClassName")
}
```
Run your packaging task as usual:
```bash
./gradlew packageDeb
# or
./gradlew packageReleaseDeb
```
The plugin will then inject/merge the `Depends:` line into the newly created `.deb`.

## How it works (implementation details)
1. Locates the latest `.deb` in the appropriate directory based on the packaging variant.
2. Extracts it using `dpkg-deb -R` into a temporary work directory under `build/deb-edit`.
3. Edits the `DEBIAN/control` file:
   - If `Depends:` exists, merges your list with existing ones (deduplicated).
   - Otherwise, appends a new `Depends:` line.
4. Finds all `.desktop` files inside the extracted package (recursively; often under `lib/` for Compose apps) and ensures they contain the desired `StartupWMClass`:
   - Replaces an existing `StartupWMClass=...` line, or
   - Inserts `StartupWMClass=...` right after the `[Desktop Entry]` header, or
   - Appends it at the end if no header is present.
   - This update runs even if `debDepends` is empty, so you can use the plugin solely to fix the dock icon.
5. Rebuilds the `.deb` with `dpkg-deb -Zxz -b`, replacing the original file.

## Troubleshooting
- Error: `dpkg-deb is required (Debian/Ubuntu). Install it with: sudo apt-get install dpkg-dev)`
  - Install `dpkg-dev` and re-run your packaging task.
- Error: `.deb directory not found` or `No .deb file found`
  - Ensure you ran the packaging task first (e.g., `./gradlew packageDeb`).
  - Confirm the directory matches your variant. For release: `build/compose/binaries/main-release/deb`.
- Dock/taskbar icon is missing or not matched on GNOME/KDE
  - Set `startupWMClass` to your application’s WM class (default is `MainClassKt`).
  - The plugin updates `.desktop` files even when `debDepends` is empty, so you can use it just for this fix.
  - Tip: You can inspect your window’s class with tools like `xprop` (look for `WM_CLASS`).
- Nothing happens / no changes in .deb
  - Check that `linuxDebConfig.debDepends` is not empty if you expect dependency changes, otherwise only the `.desktop` fix will apply.

## Limitations
- Currently supports Debian `.deb` only. RPM support can be added in the future.
- The plugin modifies the latest `.deb` file based on modification time in the target directory.

## License
This project is licensed under the terms of the LICENSE file in this repository.

## Acknowledgments
Based on a Kotlin Gradle plugin template and tailored for Compose Desktop Linux packaging workflows.
