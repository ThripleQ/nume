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

libnetease acts as the data gateway exactly as it does in Netune: it produces the request
parameters (weapi/eapi encryption, QR login, device-id pool, …) while the actual HTTP I/O runs
in Kotlin via OkHttp. Kotlin owns UI, playback (Media3), and networking; the C core is reached
through a thin JNI layer. Because the C→Kotlin approach avoids reverse JNI calls, the bridge stays
one-directional and simple.

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