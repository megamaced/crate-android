# Crate — Android

Native Android companion for [Crate](https://github.com/megamaced/crate), a personal physical media cataloguing app for Nextcloud.

> **100 % AI-written.** Every line of source, every test, every CI workflow, this README, and almost every commit message in this repository was written by [Claude Code](https://www.anthropic.com/claude-code) under direction from a human reviewer. No code in this repository was hand-typed.

## Status

Early development — auth, networking, and offline storage are in place; the UI is being built screen-by-screen.

## Requirements

- Android 10 (API 29) or newer
- A Nextcloud instance running the [Crate server app](https://github.com/megamaced/crate)

## Tech stack

- Kotlin 2.x + Jetpack Compose + Material 3 (with Material You dynamic colour on Android 12+)
- Hilt for dependency injection
- Retrofit 2 + OkHttp 5 + kotlinx.serialization
- Room 2.7 for offline cache
- Coil 3 for image loading
- ZXing for barcode scanning (F-Droid friendly — no Google Play Services)
- WorkManager for background delta sync

## Building

```
./gradlew assembleDebug
```

Debug APK is written to `app/build/outputs/apk/debug/`.

## Distribution

F-Droid + sideload. The build intentionally avoids any Google Play Services dependencies so it can be published through F-Droid and installed on de-Googled devices.

## License

[AGPL-3.0-or-later](LICENSE) — same as the Crate server app and Nextcloud itself.
