# Nume

**new music** — an Android music player. The mobile counterpart to the desktop player
[Netune](https://github.com/ThripleQ/netune), sharing the same NetEase Cloud data gateway,
[libnetease](https://github.com/ThripleQ/libnetease).

## Tech stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3 (dynamic color / Material You)
- **Playback**: AndroidX Media3 (ExoPlayer + Session)
- **Networking**: OkHttp — the Kotlin layer that replaces libnetease's built-in curl transport
- **Native bridge**: libnetease integrated via JNI + transport injection

## Project layout

```
app/
  src/main/java/com/thripleq/nume/
    MainActivity.kt        # single activity, Compose entry (@AndroidEntryPoint)
    NumeApp.kt             # root UI + type-safe navigation graph
    di/                    # Hilt wiring (AppModule provides the gateway)
    ui/                    # Compose screens + per-feature ViewModels/UiState
    core/                  # net (libnetease gateway), repo, playback (Media3)
  src/main/cpp/            # JNI bridge to libnetease (CMake + NDK)
```

## Documentation

- **[docs/architecture.md](docs/architecture.md)** — 架构地图（分层/数据流/决策/路线图），唯一事实源
- **[docs/navigation-map.md](docs/navigation-map.md)** — 找东西的心智地图（三桶归位 / 命名 / 怎么搜）
- **[docs/composing-code.md](docs/composing-code.md)** — 编码纪律（依赖规矩 / 职责守恒 / type-safe 导航）

## Architecture direction

Full design: **[docs/architecture.md](docs/architecture.md)**.

libnetease acts as the data gateway exactly as it does in Netune: it produces the request
parameters (weapi/eapi encryption, device-id pool, …) and parses responses, while the actual HTTP
I/O runs in Kotlin via OkHttp. The bridge uses **transport injection**: libnetease is built without
curl (`NE_USE_CURL=OFF`) and, when it needs to send a request, calls an injected transport that
jumps over JNI to a Kotlin OkHttp layer. All real I/O lives in Kotlin; the C side keeps a single
narrow transport callback.

Playback and caching lean on Media3 end-to-end: `ExoPlayer` + `MediaSessionService` for playback,
`SimpleCache`+`CacheDataSource` for format-agnostic byte caching (we chose to depend on Media3
rather than hand-roll Netune's segment cache — see the architecture doc for the trade-off).

## Building

```bash
./gradlew assembleDebug
```

CI (GitHub Actions) builds `assembleDebug` on every push to `main`/`beta` and uploads the APK.

## Roadmap

Status and next steps live in **[docs/architecture.md](docs/architecture.md) → 路线图**.