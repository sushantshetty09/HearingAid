# How to Run "Hearing Aid Pro"

This project can be run either directly on an Android device using the prebuilt APK, or opened and built in Android Studio.

---

## Option 1: Quick Install on Phone (No Developer Tools Needed)

If your friend just wants to test and use the app on their Android phone:

1. Locate the pre-compiled APK:
   `app/build/outputs/apk/debug/app-debug.apk`
2. Send `app-debug.apk` to the phone (via WhatsApp, Google Drive, Telegram, USB, etc.).
3. On the phone, tap the APK file and select **Install** (enable "Install unknown apps" if prompted).
4. Plug in or pair Bluetooth headphones/earbuds and start the app!

---

## Option 2: Open & Run in Android Studio (Recommended for Developers)

1. **Install Android Studio** (Ladybug / Koala or newer recommended with JDK 17 or JDK 21).
2. Open Android Studio and choose:
   **File > Open...** -> select this project folder (`HearingAid`).
3. Android Studio will automatically:
   - Detect the Gradle project.
   - Generate your local `local.properties` pointing to your Android SDK.
   - Sync the project dependencies.
4. Connect an Android phone (with USB debugging enabled) or start an Android Virtual Device (AVD).
5. Click the green **Run (▶)** button in the top toolbar.

> [!NOTE]
> If you received this project in a ZIP file and it came with an existing `local.properties` file that mentions `C:\Users\susha\...`, simply **delete `local.properties`**. Android Studio will recreate it with your computer's correct SDK path.

---

## Option 3: Build from Command Line (Terminal / PowerShell)

### Windows:
```powershell
# Clean build (re-downloads dependencies if needed)
.\gradlew assembleDebug

# Install directly to a USB-connected phone:
.\gradlew installDebug
```

### Mac / Linux:
```bash
chmod +x gradlew
./gradlew assembleDebug
./gradlew installDebug
```

The output APK will be placed at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Requirements
- **Android Device / Emulator**: Android 5.0 (Lollipop, API 21) or higher.
- **Headphones**: Wired or Bluetooth headphones (recommended to avoid acoustic feedback).
- **Permissions**: Audio Recording (`RECORD_AUDIO`) and Bluetooth Audio (`BLUETOOTH_CONNECT` for Android 12+).

