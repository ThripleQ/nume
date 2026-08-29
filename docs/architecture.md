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
│   CachingDataSource ★      区间缓存心脏         │
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
| 缓存模式 | 边播边缓 + 手动离线 | 播放即写缓存；另支持"下载到离线"整批。 |
| 缓存索引 | 沿用桌面端原子 JSON | `.tmp` 写入后 `rename`，防崩溃损坏；与 Netune 布局一致、可复用排障经验。 |
| 并发下载 | 完整复刻桌面端 | seek 到未缓存区间时**并行**多个 Range 下载，避免串行等待。 |
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

## 五、缓存设计（CachingDataSource，下一步实现）

对照 Netune 桌面端，移植为 Media3 的自定义 `DataSource`，供 ExoPlayer 做随机读：

- **分段缓存**：音频按固定段切分落盘 `camera/segment` 文件，命中段同步吐出，未命中段触发下载。
- **Range 下载**：请求带 `Range: bytes=...-`，OkHttp 下载缺失段；支持断点续传（已有分段不重下）。
- **真实总时长**：由 `Content-Length` + 码率推算真实时长，让进度条从开始就正确，代替桌面端
  "先探流再修正"。
- **原子索引**：每次写完分段后，`.tmp` → `rename` 原子更新 JSON 索引，崩溃自愈。
- **离线**：手动"下载到离线"整首/整列表写满分段；离线模式优先读缓存。

## 六、持久化

- Room：元数据（歌单、歌单曲目、歌词缓存、下载记录）。
- 分段缓存索引 + 音频文件：独立于 Room 的磁盘布局（与桌面端一致）。

## 七、测试 / 构建

- 本地：`./gradlew :app:assembleRelease`（JAVA_HOME 指向 Android Studio 的 JBR）。
- CI：GitHub Actions 在 push 到 `main`/`beta` 时构建并上传 debug APK。

## 八、路线图 / 当前状态

- [x] 项目骨架、主题、导航
- [x] libnetease 子模块接入 + 原生桥（NDK/CMake、transport 注入）
- [x] OkHttp transport 走通 libnetease 请求内核
- [ ] CachingDataSource（分段缓存 / Range / 原子索引 / seek 并行下载）
- [ ] Media3 接线（自定义 DataSource 挂进 ExoPlayer，验证边播边缓与冷区 seek）
- [ ] 登录二维码（另做）
- [ ] Home / 播放页 UI