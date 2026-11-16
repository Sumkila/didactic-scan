# Physical-device testing with ADB

This guide explains how to build QuickScan and install its debug app on a physical Android phone. An emulator is optional. ADB (Android Debug Bridge) is an Android development tool; it is not included in the app and does not add app permissions or dependencies.

## 1. Install or locate Platform-Tools

In Android Studio, open **Tools > SDK Manager > SDK Tools**, select **Android SDK Platform-Tools**, and choose **Apply** if it is not installed. Platform-Tools contains `adb`.

Find your SDK location in the Android Studio SDK Manager. Common default locations are:

- Windows: `%LOCALAPPDATA%\Android\Sdk\platform-tools`
- macOS: `~/Library/Android/sdk/platform-tools`
- Linux: `~/Android/Sdk/platform-tools`

If Android Studio uses a different SDK location, use that location instead.

## 2. Make `adb` available in a terminal

Add the `platform-tools` folder to your `PATH`, then close and reopen the terminal.

- **Windows:** Search for **Edit environment variables for your account**, edit `Path`, and add the full `platform-tools` folder path. In PowerShell, you can temporarily add the usual SDK location for the current terminal with:

  ```powershell
  $env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"
  ```

- **macOS:** Add this line to `~/.zshrc` (or your shell's startup file), adjusting the SDK path if needed:

  ```shell
  export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
  ```

- **Linux:** Add this line to `~/.bashrc` or `~/.zshrc`, adjusting the SDK path if needed:

  ```shell
  export PATH="$PATH:$HOME/Android/Sdk/platform-tools"
  ```

Open a new terminal and check that ADB is available:

```shell
adb version
```

## 3. Enable USB debugging on the phone

The names and locations of settings vary by phone:

1. Open **Settings > About phone** and find **Build number**. On some phones it is under **Software information**.
2. Tap **Build number** seven times. Confirm the phone's screen lock if asked. This enables Developer options.
3. Return to Settings, open **Developer options**, and turn on **USB debugging**.

Only enable USB debugging when you plan to use it, and approve debugging only for computers you trust.

## 4. Connect and authorise the phone

1. Connect the unlocked phone to the computer with a USB cable that supports data, not charging only.
2. If the phone asks what the USB connection is for, choose a data option such as **File transfer**.
3. On the computer, run:

   ```shell
   adb devices -l
   ```

4. Look at the phone for an **Allow USB debugging?** prompt. Confirm the computer's key only if you trust that computer. You can select **Always allow from this computer** only for a computer you trust.
5. Run `adb devices -l` again to confirm the connection.

Device states in the output:

- **`device`** — connected and authorised.
- **`unauthorized`** — unlock the phone and approve the USB debugging prompt.
- **`offline`** — the connection needs troubleshooting. Try reconnecting the cable, checking the USB mode, or restarting the ADB server with `adb kill-server` followed by `adb start-server`; then check again.
- **No listed device** — check the cable and USB debugging. On Windows, install the phone manufacturer's USB driver if required. On Linux, check that the computer has the applicable Android USB device permissions.

## 5. Build the debug APK

Run the Gradle wrapper from the project root. The app module's build variant is `debug`.

- **Windows PowerShell:**

  ```powershell
  .\gradlew.bat :app:assembleDebug
  ```

- **macOS or Linux:**

  ```shell
  ./gradlew :app:assembleDebug
  ```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The project compiles against Android SDK Platform 36. If Gradle cannot find the SDK, install Platform 36 in Android Studio's SDK Manager and make the SDK location available to Gradle (Android Studio can create the machine-specific `local.properties`; do not share that file).

## 6. Install or update the app

With an authorised phone connected, run `adb devices -l` and make sure its state is `device`. From the project root, install the APK:

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` reinstalls or updates the app without requesting an uninstall. Room metadata and app-private PDF files normally remain during an update. Do not uninstall the app or clear data as part of these checks.

After installation, unlock the phone and open **QuickScan** from the app drawer. This guide does not ask you to uninstall the app or clear its data. Do not do either without explicit permission.

## Select one phone when multiple devices are connected

Use the serial shown by `adb devices -l` with `-s`:

```shell
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> logcat
```

Replace `<serial>` with the actual serial from the device list, without the angle brackets. The same option works with other ADB commands.

## View device logs

To show logs from the connected device:

```shell
adb logcat
```

Press **Ctrl+C** to stop viewing logs. If several devices are connected, use `adb -s <serial> logcat`.

## Physical-device testing checklist

- Install and launch the app; confirm no CAMERA or broad storage permission prompt appears.
- Tap **Scan a document**. Complete first-use Google Play services setup if prompted, scan one page, name it, and save it.
- Confirm exactly one document appears. Scan several pages and confirm the displayed page count is correct and capped at five.
- Cancel scanner capture and confirm no document was added.
- Scan again, cancel the naming dialog, and confirm no document was added.
- Tap Scan repeatedly and confirm only one scanner session opens.
- Rotate during scanner setup, scanning, and the name dialog where practical; confirm no duplicate import. Process-death recovery of pending scan output is not promised.
- Open a saved PDF in an installed external viewer and confirm it is readable.
- Share the PDF to a receiving app and confirm that app can read it. Cancel the Share Sheet and confirm the original is unchanged.
- Rename a saved document; confirm the title updates and the renamed document still opens and shares.
- Close and reopen the app; confirm saved documents remain.
- Tap **Delete**, cancel, and confirm the document remains.
- Tap **Delete** again and confirm; verify the app returns Home and both the record and PDF are removed.
- Confirm useful errors appear for missing files and unavailable viewers when practical.
- Back navigation returns to the document list.
- The keyboard does not cover important dialog controls.
- Light and dark themes and larger text remain usable.
- Rotating the phone does not crash the app.
- If ML Kit is unsupported or setup fails, confirm the app shows an error and re-enables Scan.

If no authorised physical device is available, complete the build and test-compilation checks that can run, then report that device testing was not performed. A successful build or installation does not mean the screens have been visually verified on a phone.

The test-compilation command is:

```shell
./gradlew :app:compileDebugAndroidTestKotlin
```

Run the synchronous ViewModel unit tests with:

```shell
./gradlew :app:testDebugUnitTest
```

### Test-only PDF persistence and platform checks

The instrumentation tests generate a minimal test PDF, exercise the repository import/rename/delete flow using a uniquely named test database and temporary managed directory, then close/reopen the database and remove the test files. `DocumentPlatformTest` checks scanner-result validation, VIEW/SEND intents, temporary URI grants, and FileProvider directory restrictions. These tests do not expose a production import screen, invoke ML Kit's full UI, or alter the production database.

Run only this check on an authorized device or emulator:

```shell
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.example.platform.DocumentPlatformTest
```

Run the entire UI and data-layer instrumentation suite with:

```shell
./gradlew :app:connectedDebugAndroidTest
```

The persistence test verifies Room data across database close/reopen; it is not a full process restart test. After importing a real scan, perform the manual app restart, viewer, and recipient-app checks above. Do not clear app data or uninstall as part of persistence testing.

The official ML Kit documentation requires Android API 21+, Google Play services, and at least 1.7 GB total device RAM. This app's minimum SDK is 24. ML Kit components may download through Google Play services before first use; test first-use setup with a network connection.

## Verification record

The connected target was a Samsung SM-A065F running Android 16 (API 36). ADB reported one authorized device. The debug APK installed with `adb install -r`, and `org.example/.MainActivity` launched. Google Play services was present. Scanner capture, external PDF viewing, and recipient-side sharing were not manually exercised.

This command completed successfully, including unit tests, APK assembly, and the 24 data/platform instrumentation tests (16 `ScannerDataLayerTest` and 8 `DocumentPlatformTest`):

```shell
./gradlew --no-daemon --max-workers=1 \
  :app:testDebugUnitTest \
  :app:connectedDebugAndroidTest \
  :app:assembleDebug \
  -Pandroid.testInstrumentationRunnerArguments.notClass=org.example.ScannerUiTest
```

The unfiltered `:app:connectedDebugAndroidTest` run did not pass: all 16 data-layer and 8 platform tests passed, but all four `ScannerUiTest` cases failed with “No compose hierarchies found in the app.” The Compose instrumentation harness issue remains unresolved; those UI checks are not claimed as passed. The external scanner UI and actual viewer/recipient handoff still require the manual checks above.

Official references:

- [ML Kit Document Scanner for Android](https://developers.google.com/ml-kit/vision/doc-scanner/android)
- [Android secure file sharing setup](https://developer.android.com/training/secure-file-sharing/setup-sharing)
- [Android temporary URI permission guidance](https://developer.android.com/training/secure-file-sharing/share-file)
- [Android Activity Result APIs](https://developer.android.com/training/basics/intents/result)
