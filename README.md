# Crate — Android

Native Android companion for [Crate](https://gitea.macecloud.co.uk/macebox/crate), a personal physical media cataloguing app for Nextcloud.

## Status

Early scaffolding — see the [Android Task List](https://outline.macecloud.co.uk/doc/crate-android-task-list-8rxf6egO2E) and [Building Blocks](https://outline.macecloud.co.uk/doc/crate-android-app-building-blocks-VWiiOfrwGy) for the roadmap.

## Requirements

- Android 10 (API 29) or newer
- A Nextcloud instance running the [Crate app](https://gitea.macecloud.co.uk/macebox/crate)

## Tech stack

- Kotlin 2.x + Jetpack Compose + Material 3
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

AGPL-3.0-or-later — same as the Crate server app.
