# Installing PowerGuard as a System App

PowerGuard requires system permissions to fully function as an AI-powered battery and network optimization system. This document explains how to install PowerGuard as a system app.

## Prerequisites

1. A rooted Android device, or
2. A custom ROM that allows system app installation, or
3. An Android emulator with system privileges

## Automatic Installation (via Script)

For development and testing, you can use the provided `run_android.sh` script which will automatically detect if your device is rooted or an emulator and install PowerGuard as a system app.

```bash
# Make the script executable
chmod +x run_android.sh

# Run the script
./run_android.sh
```

## Manual Installation

### Step 1: Build the APK

1. Build the app from Android Studio or command line:
   ```bash
   ./gradlew assembleDebug
   ```
2. Locate the APK file in `app/build/outputs/apk/debug/app-debug.apk`

### Step 2: Install as a System App (Rooted Device)

1. Enable USB debugging on your device
2. Connect your device to your computer
3. Copy the APK to your device:
   ```bash
   adb push app/build/outputs/apk/debug/app-debug.apk /sdcard/
   ```
4. Open a shell on your device:
   ```bash
   adb shell
   ```
5. Gain root access:
   ```bash
   su
   ```
6. Remount the system partition as read-write:
   ```bash
   mount -o rw,remount /system
   ```
7. Create a directory for the app:
   ```bash
   mkdir -p /system/priv-app/PowerGuard
   ```
8. Copy the APK to the system directory:
   ```bash
   cp /sdcard/app-debug.apk /system/priv-app/PowerGuard/PowerGuard.apk
   ```
9. Set the correct permissions:
   ```bash
   chmod 644 /system/priv-app/PowerGuard/PowerGuard.apk
   chown root:root /system/priv-app/PowerGuard/PowerGuard.apk
   ```
10. Remount the system partition as read-only:
    ```bash
    mount -o ro,remount /system
    ```
11. Reboot your device:
    ```bash
    reboot
    ```

### Step 3: Install as a System App (Android Emulator)

If you're using Android Emulator from Android Studio:

1. Create an AVD with a system image that has Google APIs
2. Start the emulator with writable system:
   ```bash
   emulator -avd <your_avd_name> -writable-system
   ```
3. Restart ADB as root:
   ```bash
   adb root
   adb remount
   ```
4. Copy the APK to the system directory:
   ```bash
   adb push app/build/outputs/apk/debug/app-debug.apk /system/priv-app/PowerGuard/PowerGuard.apk
   ```
5. Set the correct permissions:
   ```bash
   adb shell chmod 644 /system/priv-app/PowerGuard/PowerGuard.apk
   ```
6. Reboot the emulator:
   ```bash
   adb reboot
   ```

## Verifying Installation

After rebooting, you should see PowerGuard in your app drawer. The app should automatically start when the device boots up, and you should have access to system-level optimizations.

You can verify it's running as a system app by going to Settings > Apps > PowerGuard, where it should show "System app" in the app info.

## Troubleshooting

- If the app doesn't appear after reboot, check if the APK was properly installed in the system directory.
- If the app crashes, check logcat for any permission-related errors.
- Some custom ROMs may require additional steps or have different system directories.
- For Android 10+ emulators, you may need to disable verified boot:
  ```bash
  emulator -avd <your_avd_name> -writable-system -no-snapshot-load
  ``` 