# Offline Music (Kotlin/Native Android)

100% Kotlin app: free catalog (Audius + Jamendo) + your MP3/MP4 uploads with crop/rename + desktop Spotify link (v2).

- **Storage:** local only (`filesDir/tracks/` + Room index). No server.
- **Offline rule:** WiFi-only downloads by default (unmetered WiFi/Ethernet). Download on WiFi, listen offline at saved quality.
- **Legal:** only Audius-downloadable / Jamendo / your own files are persisted. Spotify + non-downloadable streams are preview/stream-only.
- **Keys (no pay):** Audius needs none. Jamendo read API needs only the public `client_id`
  (default baked in; override via repo secret `JAMENDO_CLIENT_ID` or `-Pjamendo.clientId=…`).
  The Jamendo `client_secret` is never used and must never be committed.
  SoundCloud API is deferred — it now requires paid Artist Pro for a key.

## Get the APK (your emulator)

No local SDK needed: push to GitHub and Actions builds it.

1. Push this folder to the `offline-music-kotlin` repo (CI runs on push).
2. GitHub → repo → **Actions** → latest `build-apk` run → **Artifacts** → `offline-music-debug-apk` → download zip.
3. Install on the emulator: drag-drop the APK onto the emulator window, or
   `adb install app-debug.apk`.

## Build locally (optional)

Needs JDK 17 + Android SDK 34:

```bash
gradle :app:assembleDebug
# apk at app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

```
app/src/main/java/com/offlinemusic/app/
  MainActivity.kt          tabs + mini player (Media3 PlayerView)
  music/Models.kt          SearchTrack
  music/Api.kt             Audius + Jamendo Retrofit clients
  music/LibraryStore.kt    Room DB (metadata; bytes live in filesDir/tracks/)
  music/Net.kt             WiFi-only gate via ConnectivityManager
  music/TrimAudio.kt       decode-anything → slice → 16-bit WAV (same sample rate)
  music/Player.kt          shared ExoPlayer
  music/MainViewModel.kt   search/download/import state
```

## Roadmap

- v2: Spotify PKCE link (browse + control, streaming-only badge), playlists/artwork grid,
  foreground playback service for background audio, SoundCloud BYOK.
