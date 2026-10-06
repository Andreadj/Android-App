# MobileD Android + iOS — update checkpoint

Date: 2026-10-06

## Work performed

- Extended Android `LightCommand` to represent current Color / Legacy / Matrix / Music JSON fields.
- Corrected Android direct-device routing so `GState=X` and `GPort=8889` are not swapped.
- Corrected Android grouped Color routing to remain `GState=X` / `GPort=8889`.
- Added Android Matrix controller with GLights 101–112, Speed, Brightness, GUniverse, PixelID and PixelCount.
- Replaced Android Music placeholder page with an Android 10+ playback-capture implementation using MediaProjection/AudioPlaybackCapture and an sACN RGBW sender.
- Android Music keeps command selection on UDP JSON and sends realtime output separately as sACN.
- Extended iOS `DeviceData` with current Matrix/Music fields and changed device equality to use stable APName.
- Added iOS `MobileDCommand` JSON builder for current protocol fields.
- Corrected iOS ON/OFF to copy the current Discovery device state and change only Command, preserving GState/GPort and advanced fields.
- Propagated Matrix/Music fields through the iOS Home runtime state update.
- Added iOS Matrix controls under the existing Functions/Matrix tab.
- Added iOS sACN transport code and a deterministic transport test page.

## Important verification status

- iOS Swift syntax was checked with `swiftc -parse` for all modified Swift files.
- Android build was attempted with the project's Gradle wrapper, but the environment could not download Gradle 8.6 because external network access is unavailable. Therefore no APK was generated here and Android compilation is NOT claimed as verified.
- No iOS Xcode/macOS build was possible in this environment. No iOS binary is claimed.
- No MobileD hardware runtime test was performed.

## Known limitation intentionally preserved

The supplied iOS project has no ReplayKit Broadcast Upload Extension. iOS does not expose arbitrary system-wide playback audio to a normal app process in the same way as Android AudioPlaybackCapture. Therefore the supplied iOS Music page prepares the current command/transport path and provides a deterministic transport test, but it does NOT pretend to implement system-wide playback-audio analysis. The actual iOS Music capture layer requires a macOS/Xcode project change with the appropriate Broadcast Extension and user flow.

## Firmware Update

Not added to Android or iOS, per project rules.
