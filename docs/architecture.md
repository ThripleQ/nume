# Nume 架构

Nume 是桌面端播放器 [Netune](https://github.com/ThripleQ/Netune) 的安卓版，
共享同一个网易云数据网关 [libnetease](https://github.com/ThripleQ/libnetease)。
核心目标是：**把 Netune 桌面端的缓存能力（分段缓存、Range 断点续传、seek 冷区并行下载、真实总时长）
原样搬进安卓**，同时让 UI / 播放 / 网络完全由 Kotlin 掌控。

---

## 一、分层总览

```
┌───────────────────────────────────────────────┐
│ UI 层 · Jetpack Compose + Material 3           │
│   登录 · 探索/搜索 · 我的 · 详情列表 · 播放页   │
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

## 六、播放 UI（PlayerDock 合体 + 通用伸展壳）

播放界面不是独立页面，而是**常驻底部 dock 与全屏播放页合体**在 `ui/playerbar/PlayerDock.kt`：

- **单组件、单状态**：一份 `PlayerDockState` + 官方 `AnchoredDraggableState`，三档
  `Closed / Half / Full`；`progress = offset / travelPx ∈ [0,2]`（0=收在迷你条胶囊、1=悬浮卡、
  2=盖满全屏），**只在 draw 阶段（graphicsLayer）读，绝不驱动挂载/组合**（铁律）。
- **两段式几何**：`[0,1]` 胶囊原位展开成悬浮卡（dock 仍可见可点），`[1,2]` 卡片放大盖满全屏
  （dock 淡出）；几何/圆角在 draw 阶段用 `graphicsLayer` + `drawWithContent(clipPath)` 画实心卡，
  零重组、不透底。
- **手势**：迷你条上滑 1:1 跟手、松手按位置/速度吸附；点迷你条/列表项 `open(toFull=true)` 直达全屏；
  收起箭头/返回键回落。
- **单布局连续形变**：卡片与全屏共用一套骨架（封面→标题→歌手→弹性空白→进度→控制），随 `sc`
  连续插值；全屏专属动作行/分割线/功能胶囊行以「高度+alpha」长出，播放键由裸图标长成
  `primaryContainer` 圆；标题/歌手全程在封面下方。
- **状态与真相源**：`rememberPlayerState`（元数据/播放态，低频）与 `rememberPlayerPosition`
  （进度，高频）拆开订阅，避免 250ms 轮询触发整页重组；播放控制走 `PlayerHolder`。
- **通用伸展壳**：`ui/components/ExpandableShell.kt` 提供「胶囊 → 全屏面板」的通用过渡
  （我的页「喜欢的音乐」等复用），动画/尾帧/BackHandler 内聚，并约定与起点胶囊同构的对齐契约。
- **列表操作行**：`ui/playerbar/CollectionActions.kt` 提供收藏/播放/评论三按钮，列表头部与
  滚动浮岛共用；`TrackListScreen` 上报三按钮是否滑出视口，供 dock 切换为操作行。

## 七、持久化

- Room(后续)：元数据（歌单、歌单曲目、歌词缓存、下载记录）。
- Media3 `SimpleCache` 自带哈希索引 + `StandaloneDatabaseProvider` 落库，独立于应用 UI 数据。

## 八、构建 / 性能 / 诊断

- **变体**：`assembleDebug`（开发用，无 R8/AOT）· `assembleRelease`（发布用，开 R8 +
  `isShrinkResources`）。
- **性能 A/B 一律用 release**：debug 的 Compose（无 R8/AOT、debuggable）UI 线程约慢 1.5–1.6×，
  足以让最简列表在 90Hz（预算 11.1ms）掉帧。真机实测 release 90th≈12–14ms / janky≈1%，
  debug 18–22ms / 3–9%。
- **R8 keep 规则**：`app/proguard-rules.pro` 保住 JNI 按「名字」引用的类/成员
  （`NumeNative` / `NumeTransport` / `NumeTransportOut` / `ApiResult`），否则原生传输运行期崩。
- **Baseline Profile**：`:baselineprofile` 模块（官方 `androidx.baselineprofile` + macrobenchmark），
  `./gradlew :app:generateReleaseBaselineProfile` 产出安装期 AOT profile；部分 ROM（如 vivo）的
  安装拦截会挡住 UTP 自动安装，需换设备/模拟器生成。
- **诊断工具**：Compose 编译器报告（`app/build/compose-reports|metrics`，查稳定性/可跳过性）、
  JankStats（`MainActivity`，按生命周期启停）、LeakCanary / OkHttp 日志 / StrictMode（仅 debug）、
  `Theme.Nume.Starting` 冷启动 splash。
- **构建提速**：`gradle.properties` 开 configuration cache + build cache。
- CI：GitHub Actions 在 push 到 `main`/`beta` 时构建并上传 debug APK。

## 九、路线图 / 当前状态

- [x] 项目骨架、主题、导航
- [x] libnetease 子模块接入 + 原生桥（NDK/CMake、transport 注入）
- [x] OkHttp transport 走通 libnetease 请求内核
- [x] Media3 字节缓存（SimpleCache + CacheDataSource）接进 ExoPlayer
- [x] 真机播放链路：榜单 → 曲目 → 签名 URL → 缓存 → 出声
- [x] 队列播放（整榜 ⏮/⏭/连播）
- [x] 前台服务 + 系统媒体通知
- [x] Hilt 依赖注入（AppModule 提供 gateway；Repository/PlaybackLauncher 注入）
- [x] ViewModel + UiState(StateFlow) 引入；屏幕只订阅 UiState、发事件，不再自取数据
- [x] type-safe 导航（@Serializable destination，目的地集中定义，替代字符串路径）
- [x] 我的页：登录（Cookie 粘贴 / 短信验证码）+ 喜欢的音乐 + 已购 + 收藏/创建的歌单
- [ ] 登录二维码（另做）
- [x] 播放页打磨（PlayerDock 合体：迷你条↔卡片↔全屏两段式、单布局连续形变、随机/循环接 ExoPlayer）
- [ ] 探索(Home) / 搜索页（目前仍是占位，纯展示）
- [ ] 离线下载（DownloadManager）
