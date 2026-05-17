# Crate — Android

Native Android companion for [Crate](https://github.com/megamaced/crate), a personal physical media cataloguing app for Nextcloud.

> **100 % AI-written.** Every line of source, every test, every CI workflow, this README, and almost every commit message in this repository was written by [Claude Code](https://www.anthropic.com/claude-code) under direction from a human reviewer. No code in this repository was hand-typed.

## Screenshots

<p align="center">
  <img src="screenshots/home.png" width="200" alt="Home screen" />
  <img src="screenshots/collection-music.png" width="200" alt="Music collection" />
  <img src="screenshots/collection-games.png" width="200" alt="Games collection" />
</p>
<p align="center">
  <img src="screenshots/add-item.png" width="200" alt="Add item" />
  <img src="screenshots/playlists.png" width="200" alt="Playlists" />
  <img src="screenshots/search.png" width="200" alt="Search" />
</p>

## Status

Released — v1.0.0. Signed APKs are published on the [Releases page](https://github.com/megamaced/crate-android/releases/latest); in-place upgrades from one signed release to the next are supported.

The app checks GitHub for newer releases on launch (no more than once per 24h) and posts a notification when one is available. Tapping it opens the release page in the browser so you can download and install the new APK.

## Features

- **Home feed** — Item of the Day, Recently Added, Most Valuable, and per-category recent items
- **Collection browsing** — browse by category (Music, Films, Books, Games, Comics) with format chip filters and sorting
- **Add & edit items** — full form with category-specific formats, year, barcode, label, and artwork
- **Barcode scanning** — scan barcodes with the camera (ZXing)
- **External metadata search** — Discogs (music), TMDB (films), Open Library (books), RAWG (games), ComicVine (comics)
- **Artwork** — pick artwork from your device or pull it from external search results
- **Playlists** — create, edit, and delete playlists; add/remove items
- **Search** — search your own collection or external sources
- **Sharing** — share collections with other Nextcloud users; view collections shared with you
- **Offline support** — full offline cache with Room; background delta sync via WorkManager
- **Owned / Wanted** — track items you own vs. items on your wishlist
- **Material You** — dynamic colour theming on Android 12+ with dark mode support

## Requirements

- Android 10 (API 29) or newer
- A Nextcloud instance running the [Crate server app](https://github.com/megamaced/crate)

## Installation

Download the latest signed APK from the [Releases page](https://github.com/megamaced/crate-android/releases/latest) and install it. You will need to allow "install from unknown sources" for your browser the first time. Subsequent updates install in place over the existing app — the in-app update notification points you back to the Releases page when a new version is published.

## Tech stack

- Kotlin 2.x + Jetpack Compose + Material 3 (with Material You dynamic colour on Android 12+)
- Hilt for dependency injection
- Retrofit 2 + OkHttp 5 + kotlinx.serialization
- Room 2.7 for offline cache
- Coil 3 for image loading
- ZXing for barcode scanning
- WorkManager for background delta sync

## Building

```
./gradlew assembleDebug
```

Debug APK is written to `app/build/outputs/apk/debug/`.

For signed release builds, see [docs/SIGNING.md](docs/SIGNING.md).

## License

[AGPL-3.0-or-later](LICENSE) — same as the Crate server app and Nextcloud itself.
