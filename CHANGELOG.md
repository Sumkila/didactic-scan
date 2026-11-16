# Changelog

All notable project changes are recorded here.

## [Unreleased]

### Added
- Added a single-module Kotlin/Compose Android app with Material 3 light/dark themes and Home-to-Detail navigation.
- Added fictional sample documents, empty/loading/error states, and previews for the requested screens and dialogs.
- Added initial simulated scan-and-name, rename, and confirmed-delete flows backed by temporary observable state; Open and Share began as placeholders.
- Added focused Compose UI tests for the empty state, document-card callback, blank-name validation, and delete confirmation/cancellation.
- Added beginner-facing project setup notes, user instructions, and coding-agent guidance.
- Added a physical-device ADB guide covering SDK Platform-Tools, USB debugging, debug APK installation, logs, device selection, and a manual test checklist.

### Changed
- Converted the generated JVM command-line starter module to an Android app while retaining the `org.example` package and using a version catalogue for UI dependencies.
- Kept document placeholders and actions accessible without adding the large Material extended-icons package.
- Moved the temporary document list and add, rename, and delete actions into a lifecycle-scoped `ScanViewModel`; configuration changes retain edits, but process death and fresh launches still reset them.
- Changed `ScanViewModel` to observe repository-backed saved documents; fictional documents are now limited to previews and tests, and Scan explains that scanner integration is not connected instead of creating a fake saved item.

### Added
- Added Room metadata storage, exported schema configuration, app-private PDF file handling, and a repository that coordinates import, rename, and delete operations.
- Added application-owned manual dependency construction, ViewModel repository injection, and Room/file/repository instrumentation checks.
- Added a test-only PDF fixture workflow for checking import, rename, database reopen, and deletion without a production import screen.
- Added ML Kit Document Scanner PDF capture, repository-backed scan import, restricted FileProvider access, external PDF viewing, Android sharing, and focused platform tests.
- Added resource-backed scanner, import, open, and share errors, including safeguards against duplicate scan/save operations.

### Changed
- Replaced the unavailable-scan, Open, and Share placeholders with ML Kit scanning and secure external PDF actions.
