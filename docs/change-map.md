# 改动点速查表（change map）

目标：想改一件事时，先查这一行，就知道该动哪个文件，不用翻整个工程。

本表和导航地图（navigation-map.md）是正反两张表：那边回答"这东西在哪"，这边回答"想改 X 去改哪"。

---

## 1. 界面：页面长什么样

| 我想… | 去改 | 备注 |
|---|---|---|
| 改某个页面的文字 / 颜色 / 间距 / 按钮 | `ui/screens/<屏>Screen.kt` | 改界面基本只动这一个文件 |
| 改全 app 的配色 / 明暗主题 | `ui/theme/Color.kt`、`ui/theme/Theme.kt` | 换品牌色、跟系统昼夜 |
| 改字号、字重、字体 | `ui/theme/Type.kt` | |
| 改底部导航胶囊：加删 tab、换图标、改顺序 | `NumeApp.kt` | 导航目的地也集中在这一个文件 |
| 改点某处跳到哪个页面 | `NumeApp.kt` | 跳转逻辑只在这里 |

## 2. 数据：列表里有什么、怎么排

| 我想… | 去改 |
|---|---|
| 排行榜页显示哪些榜单、什么顺序 | `ui/library/LibraryViewModel.kt` |
| 单榜曲目列表的排序 / 筛选 | `ui/chart/ChartDetailViewModel.kt` |
| 数据从哪取、怎么转换（网络规则） | `core/repo/ChartRepository.kt` |
| 网络请求 / 签名 / 解析（基本不用动） | `core/net/` |

## 2.5 我的 / 登录 / 账号数据

| 我想… | 去改 |
|---|---|
| “我的”页区块（喜欢 / 已购 / 歌单）怎么摆、点哪跳哪 | `ui/screens/ProfileScreen.kt` |
| “我的”页状态：登录态 + 各区块数据加载 | `ui/profile/ProfileViewModel.kt` |
| 登录对话框（Cookie 粘贴 / 短信验证码） | `ui/screens/ProfileScreen.kt` 内 `LoginDialog` |
| 账号 / 喜欢 / 已购 / 歌单的数据获取与解析 | `core/repo/ProfileRepository.kt` |
| 曲目列表页（喜欢 / 已购 / 歌单 / 专辑） | `ui/screens/TrackListScreen.kt` + `ui/profile/TrackListViewModel.kt` |
| 登录 / 验证码接口（JNI op 30/31、cookie 导入） | `app/src/main/cpp/libnetease_jni.c` + `core/net/NeteaseOp.kt` |

## 3. 播放：点歌、进度、后台、缓存

| 我想… | 去改 |
|---|---|
| 开始播放的入口 / 行为 | `core/playback/PlaybackLauncher.kt` |
| 播放状态（当前歌、暂停 / 继续、进度）给界面用 | `core/playback/PlayerHolder.kt` |
| 后台播放通知 / 服务生命周期 | `core/playback/PlaybackService.kt` |
| 边播边缓存的策略 | `core/playback/PlaybackCache.kt` |

## 4. 装配与其他

| 我想… | 去改 |
|---|---|
| 谁注入了谁、换个实现 | `di/AppModule.kt` |
| 应用启动时做的初始化 | `NumeApplication.kt`、`MainActivity.kt` |

---

## 兜底：不确定该动哪时

1. 先全局搜（`Ctrl+Shift+F`）你要改的功能名，例如"排行"、"缓存"、"进度"。
2. 还找不到，按"它是什么"定桶：界面→`ui/`，数据 / 播放 / 网络→`core/`，装配→`di/`（详见 navigation-map.md）。
3. 改完记得同步本表与导航地图——`docs/` 是唯一事实源。
