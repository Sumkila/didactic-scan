# DocScanner

DocScanner is a single-module Android document-scanner app built with Kotlin, Jetpack Compose, and Material 3. It scans documents with Google ML Kit Document Scanner, saves PDF output privately on the device, and can open or share saved PDFs.

## What works now

- Scan up to five pages per document with ML Kit's full scanner UI; gallery import is disabled.
- Name and import scanner-generated PDFs into app-private storage with Room metadata.
- Browse documents saved to the local Room database.
- Rename saved-document metadata and delete the associated app-private PDF after confirmation.
- Observe saved documents reactively from Home and Detail.
- Open a saved PDF in an installed external viewer.
- Share a saved PDF through Android's Share Sheet.
- Use sample documents in Compose previews and tests.

The detail preview remains a placeholder; opening the PDF uses an external app. The app does not implement OCR, JPEG storage, a custom camera, a PDF editor, cloud backup, or synchronization.

## Technology and data flow

- Kotlin, Jetpack Compose, Material 3, and Navigation Compose.
- One Android app module.
- Room for document metadata and a `Flow`-based saved-document list.
- App-private storage for PDF files; Room stores only each relative PDF filename.
- ML Kit Document Scanner dynamically provided by Google Play services.
- AndroidX `FileProvider` for narrow, temporary read access to managed PDFs.
- Manual dependency construction through `ScannerApplication` and `AppContainer`.
- `ScanViewModel` observes `DocumentRepository`; `ScannerApp` collects its state using lifecycle-aware collection. The Activity Result launcher, external activity launches, navigation, and snackbar remain outside the ViewModel.

```text
app/src/main/
├── AndroidManifest.xml
├── kotlin/org/example/
│   ├── AppContainer.kt
│   ├── ScannerApplication.kt
│   ├── ScannerApp.kt
│   ├── data/
│   │   ├── DocumentRepository.kt
│   │   ├── local/database/   # Room database and DAO
│   │   ├── local/files/      # App-private PDF file store
│   │   └── model/            # Room document metadata
│   ├── platform/
│   │   ├── documents/        # FileProvider URI and VIEW/SEND intents
│   │   └── scanning/         # ML Kit result parsing
│   └── ui/
│       ├── components/       # Name and delete dialogs
│       ├── detail/           # Parameter-driven detail screen
│       ├── home/             # Home screen, state, and cards
│       ├── model/            # Display-specific document model
│       ├── navigation/       # Home/detail routes
│       ├── sample/           # Preview-only fictional documents
│       └── scan/             # ViewModel and factory
└── schemas/                  # Exported Room schema
app/src/main/res/xml/file_paths.xml # FileProvider allowlist
```

## Persistence and limitations

Real imported documents have a generated Room `Long` ID, title, creation time in epoch milliseconds, positive page count, and relative PDF filename. PDF bytes are never stored in Room. The filename is generated independently of the title, so renaming changes metadata only. Files are stored under the app's private `files/documents` directory; no public storage permission is needed.

The scan flow requests only PDF output from ML Kit, with a five-page limit, full scanner mode, and gallery import disabled. On return, `ScanViewModel.importScannedPdf(title)` passes the pending URI and scanner-provided page count to `RoomDocumentRepository.importScannedPdf(sourceUri, title, pageCount)`. The repository copies the temporary URI into private storage before inserting the Room row. If insertion fails, it attempts to remove the copied file. Deletion removes the file before deleting metadata; an already-missing file is allowed so a retry can remove a leftover database row. Room and the filesystem cannot be updated atomically: a process crash can leave an orphan PDF after an import or a row whose PDF is missing after a delete. No crash-recovery or orphan cleanup is implemented.

The database starts at schema version 1 and exports its schema under `app/schemas`. Do not enable destructive migrations. Add and test a migration before changing an installed schema in a future release. There was no prior database to migrate when this version was introduced.

Fictional `SampleDocuments` are only used by previews and tests; they are not inserted into the saved library because no matching PDF files exist. Scanner output stays pending in the Activity's `ScanViewModel` during ordinary configuration changes, but is not promised to survive process death. URI permission can expire; an unreadable result is discarded and the user is asked to scan again. Canceling the scanner or name dialog creates no database record; the app does not delete provider-owned scanner output.

Open and Share look up current metadata, verify the managed PDF still exists, and obtain a `content://` URI through FileProvider. The provider is not exported and exposes only `files/documents/`; VIEW and SEND receive temporary read permission. No `file://` URI, raw path, broad storage permission, or package-visibility query is used.

## Device requirements

The project has `minSdk 24`, while the official ML Kit Document Scanner requirement is Android API 21 or later. The device also needs at least 1.7 GB total RAM and working Google Play services. Google Play services downloads scanner UI/models as needed; the first scan may wait for this setup and can require a network connection. Do not promise that first use works offline. See [ML Kit Document Scanner for Android](https://developers.google.com/ml-kit/vision/doc-scanner/android).

## Build and tests

Prerequisites: JDK 17 or newer, Android SDK Platform 36, and Android SDK Platform-Tools.

Build the debug APK:

```shell
./gradlew :app:assembleDebug
```

Run ViewModel unit tests:

```shell
./gradlew :app:testDebugUnitTest
```

Compile the Compose, data, scanner-result, and document-intent instrumentation tests without running them:

```shell
./gradlew :app:compileDebugAndroidTestKotlin
```

Run the Room/file/repository, scanner-result, FileProvider/intent, and UI tests on an authorized device or emulator:

```shell
./gradlew :app:connectedDebugAndroidTest
```

The instrumentation persistence test uses a generated minimal PDF fixture and a test-only database; it does not add a production import screen or modify the user's saved library. ML Kit's external scanner UI is not automated. See [DEVICE_TESTING.md](docs/DEVICE_TESTING.md) for test-only checks and the manual device checklist.

On the connected SM-A065F (Android 16/API 36), the unit tests and the 24 data/platform instrumentation tests passed. The full connected suite still has four failing Compose UI tests because the test runner reports “No compose hierarchies found”; this does not verify or invalidate the app's visual behavior. Installing and launching the app succeeded, but end-to-end scanning and external viewer/recipient sharing have not been manually verified. See the device guide for exact commands and outstanding checks.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. To install or update it on an authorized phone, run `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Windows users can run `.\gradlew.bat :app:assembleDebug` instead of `./gradlew`.

The scanner flow follows [ML Kit Document Scanner](https://developers.google.com/ml-kit/vision/doc-scanner/android). PDF URIs are exposed using the narrow-directory configuration described in [Android secure file sharing](https://developer.android.com/training/secure-file-sharing/setup-sharing) and the [FileProvider grant guidance](https://developer.android.com/training/secure-file-sharing/share-file).

## Compose previews

Screen composables remain parameter-driven. The sample documents remain available for populated previews without creating a ViewModel or writing to the database.

## Project notes

- [User guide](docs/USER_GUIDE.md)
- [Physical-device setup and ADB testing](docs/DEVICE_TESTING.md)
- [Changelog](CHANGELOG.md)
- [Instructions for coding agents](AGENTS.md)
# didactic-scan
