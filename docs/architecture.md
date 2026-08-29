# Nume 架构

Nume 是桌面端播放器 [Netune](https://github.com/ThripleQ/netune) 的安卓版，
共享同一个网易云数据网关 [libnetease](https://github.com/ThripleQ/libnetease)。
核心目标是：**把 Netune 桌面端的缓存能力（分段缓存、Range 断点续传、seek 冷区并行下载、真实总时长）
原样搬进安卓**，同时让 UI / 播放 / 网络完全由 Kotlin 掌控。

---

## 一、分层总览

```
┌───────────────────────────────────────────────┐
│ UI 层 · Jetpack Compose + Material 3           │
│   登录(后续) · Home(歌单/专辑/搜索) · Player    │
│   ViewModel ←→ UiState                         │
└───────────────────┬───────────────────────────┘
                    ▼
┌───────────────────────────────────────────────┐
│ 核心层 · Core                                    │
│   Repository               数据聚合/编排         │
│   Media3 ExoPlayer + MediaSessionService 播放  │
│   SimpleCache + CacheDataSource ★  字节缓存    │
│   libnetease (JNI 数据网关)  签名/加密/API 解析   │
│   OkHttp                   真正收发 HTTP         │
└───────────────────┬───────────────────────────┘
                    ▼
┌───────────────────────────────────────────────┐
│ 持久化层 · Persistence                          │
│   Room DB         元数据(歌单/歌词/下载表)      │
│   分段缓存索引      原子 JSON(.tmp→rename)      │
│   磁盘缓存         分段音频 + 封面图            │
└───────────────────────────────────────────────┘
```

## 二、两条数据流

- **元数据路径**（歌单 / 歌词 / 封面 / 账号）：
  `Repository → NetEaseGateway → libnetease(JNI 签名与解析) → OkHttp → 网易云 API`，
  结果落 Room，供 UI 复用。
- **播放 / 缓存路径**（核心亮点）：
  `Player → Media3 ExoPlayer → CachingDataSource → 分段缓存 + 原子索引`。
  未命中时 CachingDataSource 触发 **Range 分段下载**（走 libnetease 生成的签名 URL + OkHttp），
  写分段、原子更新索引，并把数据流回 ExoPlayer。

## 三、关键决策

| 项 | 决策 | 说明 |
|---|---|---|
| 缓存模式 | Media3 `SimpleCache` + `CacheDataSource` | 采用官方引擎（原 ExoPlayer 新家）做字节缓存：编码无关、按字节随机访问；不再手写分段/索引，桌面端“分段+原子索引”经验仅作理解参考 |
| 索引/并发 | `SimpleCache` 内部索引 + LRU evictor | SimpleCache 自管哈希索引（崩溃更不易损坏），LRU 限额 512MiB；Range 分段与 seek 冷区由 CacheDataSource/官方能力承接 |
| JNI 边界 | 传输注入 | libnetease 照常跑高层调用，仅在发 HTTP 一刻经注册的 transport 回调到 Kotlin(OkHttp)。 |
| libnetease 接入 | git submodule 钉版 | 单一事实源，`git pull` 升级即可。 |
| 线程契约 | 串行派发 | request-kernel 全局单线程，`NetEaseGateway` 用单派发 + 锁串行所有调用。 |

## 四、原生桥（libnetease，当前已落地）

libnetease 以 `NE_USE_CURL=OFF` 编译，**不依赖 curl**。所有请求照常走 C 端高层服务函数
（`ne_search` / `ne_song_url_v1` / `ne_lyric` …），只是真正发送时由注入的 transport 承接。

- `libnetease_jni.c`：`JNI_OnLoad` 保存 `JavaVM` + 注册 natives；安装 transport
  （C→Kotlin 的 `NumeTransport.httpRequest` shim），组 `ne_http_resp`；一张 op 派发表覆盖 29 个服务函数。
- Kotlin `core/net/`：`NumeNative`(JNI 声明)、`NumeTransport`(OkHttp 同步发 + 收 Set-Cookie)、
  `ApiResult` / `NumeTransportOut`、`NeteaseOp`(与 C 对齐的 op 码)、`NetEaseGateway`(串行派发)。
- Cookie jar 由 `NumeApplication` 接 `filesDir/netease_cookies.json`，`setApiBase` 可显式覆盖默认
  `https://music.163.com`。

> 澄清：这不是"零反向回调"。C 侧保留了**一个窄的 transport 回调**（仅 HTTP 发送原语），
> 但所有真实网络 I/O 仍在 OkHttp，Set-Cookie 吸收与加解密留在 C，边界干净、便于调试。

## 五、缓存设计（Media3 字节缓存，当前已落地）

缓存不再手写，而是用 Media3 官方引擎（原 ExoPlayer）的 `SimpleCache` + `CacheDataSource`：

- **编码无关**：按字节缓存，mp3/aac/flac/wav 一律走同一管道，ExoPlayer 原生解码。
- **Range/seek**：命中段本地直读，未命中段由 `CacheDataSource` 走上游 Range 请求；seek 冷区由官方能力接管。
- **LRU evictor**：`LeastRecentlyUsedCacheEvictor`，配额 512MiB。
- **队列播放**：点一首歌以整榜为队列，`Player.setMediaItems(...)` 让 ⏮/⏭/自动连播生效。
- **离线**(后续)：Media3 提供 `DownloadManager`，接入后支持“下载到离线”整批。

## 六、持久化

- Room(后续)：元数据（歌单、歌单曲目、歌词缓存、下载记录）。
- Media3 `SimpleCache` 自带哈希索引 + `StandaloneDatabaseProvider` 落库，独立于应用 UI 数据。

## 七、测试 / 构建

- 本地：`./gradlew :app:assembleRelease`（JAVA_HOME 指向 Android Studio 的 JBR）。
- CI：GitHub Actions 在 push 到 `main`/`beta` 时构建并上传 debug APK。

## 八、路线图 / 当前状态

- [x] 项目骨架、主题、导航
- [x] libnetease 子模块接入 + 原生桥（NDK/CMake、transport 注入）
- [x] OkHttp transport 走通 libnetease 请求内核
- [x] Media3 字节缓存（SimpleCache + CacheDataSource）接进 ExoPlayer
- [x] 真机播放链路：榜单 → 曲目 → 签名 URL → 缓存 → 出声
- [x] 队列播放（整榜 ⏮/⏭/连播）
- [x] 前台服务 + 系统媒体通知
- [x] Hilt 依赖注入（AppModule 提供 gateway；Repository/PlaybackLauncher 注入）
- [x] ViewModel + UiState(StateFlow) 引入；屏幕只订阅 UiState、发事件，不再自取数据
- [ ] 登录二维码（另做）
- [ ] Home / 搜索 / 播放页打磨
- [ ] 离线下载（DownloadManager）