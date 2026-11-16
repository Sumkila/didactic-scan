# Coding agent instructions

- Inspect existing code and project configuration before editing.
- Keep code clear and beginner-friendly; preserve the single-module structure unless a clear need emerges.
- Keep screen composables parameter-driven and put navigation wiring in the navigation host.
- Do not add real scanning or permanent persistence unless a task explicitly asks for it.
- Keep fictional sample documents for previews and tests only; never use them as the runtime saved-document source of truth. Persist real imported PDF metadata through the Room repository and files in app-private storage.
- Do not add scanner, camera, or storage permissions until a real feature requires them.
- Preserve the Room schema history and add migrations instead of enabling destructive migration.
- Keep ML Kit launchers and external VIEW/SEND activity launches in Activity/Compose code, never in ScanViewModel.
- Keep FileProvider limited to `files/documents/` and use temporary `content://` URI read grants; do not expose app roots or `file://` paths.
- Send ML Kit PDF output through `DocumentRepository.importScannedPdf`; do not store scanner URIs as permanent file paths or create fake scan records.
- Never silently replace working features with placeholders.
- Update `CHANGELOG.md` for every completed change set.
- Update `README.md` or `docs/USER_GUIDE.md` when setup or behaviour changes.
- Verify builds and tests where possible, and report only results actually observed.
- Do not record credentials or other sensitive information.
- Do not make Git commits or publish anything without permission.

This file provides guidance to coding agents; it does not automatically enforce these rules.
