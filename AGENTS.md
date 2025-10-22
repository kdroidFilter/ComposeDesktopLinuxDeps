# Repository Guidelines

## Project Structure & Module Organization
- Root Gradle build controls verification and CI helpers.
- Included build: `plugin-build/` – Gradle plugin source.
  - Plugin code: `plugin-build/plugin/src/main/java/...`
  - Tests: `plugin-build/plugin/src/test/java/...`
- Example module: `example/` – minimal consumer for local checks.
- Static config: `config/detekt/detekt.yml` (Detekt rules), `.editorconfig`.
- CI: `.github/workflows/*.yaml` for pre-merge and publishing.

## Build, Test, and Development Commands
- `./gradlew preMerge` – run all checks for root and included build (tests, Detekt, plugin validation).
- `./gradlew detekt` – Kotlin static analysis (HTML report at `build/reports/detekt.html`).
- `./gradlew dependencyUpdates` – report available dependency upgrades.
- Plugin tests only: `./gradlew -p plugin-build :plugin:test` or `./gradlew -p plugin-build :plugin:check`.
- Publish (maintainers): `./gradlew --project-dir plugin-build setupPluginUploadFromEnvironment publishPlugins` (requires `GRADLE_PUBLISH_KEY/SECRET`).

## Coding Style & Naming Conventions
- Kotlin “official” style (`gradle.properties`), 4‑space indent; YAML uses 2 spaces (`.editorconfig`).
- Names: packages `lower.case`, classes `PascalCase`, functions/vals `camelCase`.
- Linting: Detekt is required (config in `config/detekt/detekt.yml`). Fix violations before PR.
- Formatting: Ktlint is optional; if enabled, use `ktlintFormat` and the root `reformatAll` task.

## Testing Guidelines
- Framework: JUnit 4 for the plugin module.
- Location: `plugin-build/plugin/src/test/java`.
- Naming: readable back‑ticked test names (see `PluginTest.kt`).
- Run locally with `preMerge` or `-p plugin-build :plugin:test`. Keep tests hermetic (no network; mock external processes).

## Commit & Pull Request Guidelines
- Commits: imperative, concise, scoped if helpful (e.g., “plugin: inject Depends into DEB”).
- PRs must include: clear description, rationale, linked issues, and before/after notes for behavior changes.
- Update documentation when behavior/config flags change (e.g., README for `enableT64AlternativeDeps`).
- For releases, bump `plugin-build/gradle.properties:VERSION` and tag; CI publishes on tag.

## Platform Notes
- Linux packaging validation requires `dpkg-deb` (Debian/Ubuntu): `sudo apt-get install dpkg-dev`.
- The injection task is a no‑op on non‑Linux hosts; unit tests do not require Linux.

