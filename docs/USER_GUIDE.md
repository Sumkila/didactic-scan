# DocScanner User Guide

DocScanner uses Google ML Kit Document Scanner to scan PDF documents and stores them locally. Each scan is limited to five pages; gallery import is disabled.

## Open the app

Run the `app` configuration from Android Studio on an Android device with Google Play services. The home screen lists PDFs saved in this app. A new installation has an empty saved-document list; fictional examples are used only in previews and tests.

## Browse documents

Scroll the **My Documents** list. Tap a card to see its saved date, page count, and preview placeholder.

## Scan and save

1. Tap **Scan a document** and use the ML Kit scanner to capture and review up to five pages.
2. When the scanner returns to DocScanner, enter a name and tap **Save**.
3. Wait for the save to finish; the PDF is copied into private storage and appears in Home after Room observes the saved metadata.

The first use may wait while Google Play services downloads the scanner UI and models. Network access may be needed for this setup; subsequent scanning runs on-device. The device must support Google Play services and have at least 1.7 GB total RAM. DocScanner does not request the CAMERA or broad storage permission for this scanner flow.

Canceling scanner capture or the naming dialog creates no saved document. A pending result can survive ordinary configuration changes while the app remains alive. If scan output is no longer readable or the process was killed before saving, scan again; an unfinished scan is not restored after process death. If scanning is unavailable or setup fails, check that Google Play services is installed and updated, connect to a network for first-use setup, then try again.

## Rename or delete

Open a saved document by tapping its card. Tap **Rename**, edit the name, and confirm; a blank name cannot be saved. A successful rename updates the Room record while keeping the PDF file unchanged.

Tap **Delete** and confirm to delete the app-private PDF and its Room record. If file deletion fails, the record is kept and an error is shown; if metadata deletion fails after the file is removed, retrying deletion can remove the remaining record. Cancel the delete dialog to leave the document unchanged.

## Open and share

Tap **Open** to open the saved PDF in an installed viewer. If no compatible viewer is installed, DocScanner displays an error; install a PDF viewer or use **Share** with an app that can read PDFs. If the saved file is missing, the document details are kept and an error is shown; scan the document again to create a new saved copy.

Tap **Share** to open Android's Share Sheet. Choosing an app does not guarantee that the recipient completed delivery. Canceling the Share Sheet leaves the original PDF unchanged. Open and Share grant the external app temporary read access to a content URI, not a raw filesystem path.

## Storage and limitations

Imported PDF metadata is stored in Room, and PDF files are stored in the app-private documents directory. These remain after a normal app restart. Android normally removes them if app data is cleared or the app is uninstalled. There is no cloud backup or sync promise. A process crash during a file/database update can leave an orphan file or a record whose file is missing; automatic recovery is not implemented. The detail screen's PDF preview is a placeholder; the app does not provide custom PDF rendering, OCR, or a custom camera.

Useful errors explain when scan output cannot be read or saving fails. If the scan output can no longer be accessed, scan again. If deletion reports that the PDF was removed but its record remains, retry Delete.

No PDF bytes are stored in Room. Open/Share use a non-exported FileProvider restricted to the managed PDF directory. Clearing app data or uninstalling normally removes local documents. There is no cloud backup/sync promise and no promise that an unfinished scan survives process death.

## Basic troubleshooting

- If the app does not build, check that Android Studio has the required Android SDK Platform 36 installed and Gradle has synced.
- If no device appears in Android Studio, start an emulator or connect a device with USB debugging enabled.
- Preview sample documents are not runtime saved documents and cannot be restored into the library.
