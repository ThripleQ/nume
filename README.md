# Nume

**new music** — an Android music player. The mobile counterpart to the desktop player
[Netune](https://github.com/ThripleQ/netune), sharing the same NetEase Cloud data gateway,
[libnetease](https://github.com/ThripleQ/libnetease).

## Tech stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3 (dynamic color / Material You)
- **Playback**: AndroidX Media3 (ExoPlayer + Session)
- **Networking**: OkHttp — the Kotlin layer that replaces libnetease's built-in curl transport
- **Native bridge**: libnetease integrated via JNI (in progress — see roadmap)

## Project layout

```
app/
  src/main/java/com/thripleq/nume/
    MainActivity.kt        # single activity, Compose entry
    NumeApp.kt             # root UI + navigation graph
    ui/
      theme/               # Material 3 color scheme, typography
      screens/             # feature screens
  src/main/cpp/            # JNI bridge to libnetease (planned)
```

## Architecture direction

Full design: **[docs/architecture.md](docs/architecture.md)**.

libnetease acts as the data gateway exactly as it does in Netune: it produces the request
parameters (weapi/eapi encryption, device-id pool, …) and parses responses, while the actual HTTP
I/O runs in Kotlin via OkHttp. The bridge uses **transport injection**: libnetease is built without
curl (`NE_USE_CURL=OFF`) and, when it needs to send a request, calls an injected transport that
jumps over JNI to a Kotlin OkHttp layer. All real I/O lives in Kotlin; the C side keeps a single
narrow transport callback.

The core player goal — Netune's segment cache, Range-resumable downloads, parallel seek downloads,
and true-total-duration progress — is reimplemented on Android as a custom Media3
[`DataSource`](docs/architecture.md#五缓存设计cachingdatasource下一步实现): i.e. it mirrors the
desktop caching engine, just driven by ExoPlayer's random reads instead of a download scheduler.

## Building

```bash
./gradlew assembleDebug
```

CI (GitHub Actions) builds `assembleDebug` on every push to `main`/`beta` and uploads the APK.

## Roadmap

- [ ] JNI bridge + libnetease native build (CMake + NDK)
- [ ] OkHttp transport wiring through the libnetease request kernel
- [ ] Login (QR) screen
- [ ] Home / library / player screens with Media3