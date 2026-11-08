# FM1-Companion

Open-source Android companion app for the M-VAVE FM-1 synthesizer.

## Current milestone: BLE-MIDI diagnostic v0.1.0

This is a native Kotlin / Jetpack Compose Android app, initially designed to find out whether the FM-1's current firmware offers the **standard BLE-MIDI service**. No changes to the FM-1 firmware are made.

**Implemented in source:** Android Bluetooth permissions; nearby BLE scan; MIDI service advertisement indicator; Android MidiManager BLE MIDI connection; MIDI note test keyboard (C4 to C5), channel selection 1–16; incoming MIDI hex log; disconnect All Notes Off; GitHub Actions debug APK build and MIDI unit tests.

**Not implemented yet:** MIDI CC/SysEx sound editor, sequencer, preset/project transfer, screen mirroring, firmware updates, Play Store signing.

## IMPORTANT: Firmware compatibility

FM-1 hardware supports BLE MIDI and official M-VAVE firmware advertises this functionality. **Caravel 0.11.0 documents USB and TRS MIDI, not BLE-MIDI**. The Caravel Plus build on our FM-1 has not been shown to advertise BLE-MIDI, so the app may not find or connect to it. This is a firmware compatibility question, not a reason to flash working firmware.

- Caravel: https://github.com/bonszaimusic/caravel
- Android BLE MIDI API: https://developer.android.com/reference/android/media/midi/MidiManager
- M-VAVE FM-1 product information: https://www.m-vave.com/

## Build an APK

The GitHub Actions workflow **Android debug APK** runs tests, builds the app, and uploads a downloadable **fm1-companion-debug** build artifact containing **app-debug.apk**. Alternatively open this repo in Android Studio (JDK 17, Android SDK 35), sync Gradle, then build the debug APK. No Gradle wrapper is committed: Android Studio or a local Gradle 8.9 installation is required.

On your Android phone, extract and install the debug APK, grant Nearby devices permission, tap Scan, look for the FM-1, and try the MIDI connection. If it opens, select the MIDI channel and send a test note. If it does not, capture the precise status text and the FM-1 firmware settings.

## Privacy and scope

Bluetooth scans will display nearby device names and hardware addresses locally. The app requests no internet permission, does not persist scan data, and cannot modify synth firmware. Android 12+ requests BLUETOOTH_SCAN and BLUETOOTH_CONNECT; Android 8–11 requires location permission for BLE scanning.

## Roadmap

1. Verify BLE MIDI on FM-1 firmware that supports it; determine Caravel Plus BLE capability safely.
2. Better touch keyboard, velocity, multi-touch and controller mappings.
3. Sequencer, MIDI clock and transport.
4. Firmware-specific synth editing once CC/SysEx mappings are documented.
5. Optional preset librarian and screen interface when firmware protocols are known.

MIT licensed. Not affiliated with M-VAVE or the Caravel maintainers.
