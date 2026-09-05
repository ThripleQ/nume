package com.thripleq.nume

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.thripleq.nume.core.playback.PlayerHolder
import com.thripleq.nume.core.repo.TrackCollection
import com.thripleq.nume.ui.playerbar.BottomTab
import com.thripleq.nume.ui.playerbar.PlayerCapsule
import com.thripleq.nume.ui.playerbar.rememberHasTrack
import com.thripleq.nume.ui.profile.ProfileViewModel
import com.thripleq.nume.ui.profile.TrackListUiState
import com.thripleq.nume.ui.profile.TrackListViewModel
import com.thripleq.nume.ui.screens.HomeScreen
import com.thripleq.nume.ui.screens.LibraryScreen
import com.thripleq.nume.ui.screens.PlayerSheet
import com.thripleq.nume.ui.screens.ProfileScreen
import com.thripleq.nume.ui.screens.SearchScreen
import com.thripleq.nume.ui.screens.TrackListScreen
import com.thripleq.nume.ui.screens.WebLoginScreen
import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. Navigation lives only in [NumeApp]. */
@Serializable
object Home

@Serializable
object Library

@Serializable
object Search

@Serializable
object Profile

/** [TrackListScreen] for a chart: chart id IS a playlist id. */
@Serializable
data class ChartDestination(val chartId: String, val name: String)

/**
 * Generic track list opened from the Profile tab: liked tracks, purchases, a
 * playlist or an album (see [TrackListSource] for the `source` values).
 */
@Serializable
data class TrackListDestination(val source: String, val id: String, val title: String)

/** Full-screen WebView login (official NetEase login page, Kanade-style). */
@Serializable
object WebLogin

/**
 * Root of the Compose UI: navigation graph + docked island + player sheet.
 *
 * 落地常驻胶囊岛：全宽贴底、顶部圆角、去白描边、surfaceContainerHighest。
 * 岛内 = 顶部拉手 + 播放状态栏（可收起）+ 底部导航行；拉手上拉 → 全屏播放页。
 * 播放页用 [PlayerSheet]（ExpandableShell 从岛向上拉开成沉浸全屏），覆盖岛本身。
 */
@Composable
fun NumeApp() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    val player = remember { PlayerHolder.get(context) }
    // 只订阅"是否有曲目"（低频），不订阅 250ms 进度轮询，避免 NavHost 层随播放进度重组。
    val hasTrack = rememberHasTrack(player)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val isTabPage = destination?.hasRoute<Home>() == true ||
        destination?.hasRoute<Search>() == true ||
        destination?.hasRoute<Profile>() == true
    val currentTab = when {
        destination?.hasRoute<Home>() == true -> BottomTab.ExploreTab
        destination?.hasRoute<Search>() == true -> BottomTab.SearchTab
        destination?.hasRoute<Profile>() == true -> BottomTab.ProfileTab
        else -> null
    }
    // 详情页时保持进入前的 tab，收起/展开动画中高亮 pill 不闪到探索。
    var lastTab by remember { mutableStateOf(BottomTab.ExploreTab) }
    LaunchedEffect(currentTab) {
        if (currentTab != null) lastTab = currentTab
    }
    val selectedTab = currentTab ?: lastTab

    // 列表详情页（榜单/歌单/专辑/喜欢/已购）的滚动操作行：
    // TrackListScreen 上报三按钮是否滑出视口，供岛内切换为操作行。
    var listActionsOffscreen by remember { mutableStateOf(false) }
    var listCollection by remember { mutableStateOf<TrackCollection?>(null) }
    var listPlayAll by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isListDetail = destination?.hasRoute<TrackListDestination>() == true ||
        destination?.hasRoute<ChartDestination>() == true

    // 播放页（全屏覆盖层）开关：由岛上拉 / 播放条点击触发。
    var playerSheetOpen by remember { mutableStateOf(false) }
    // 播放页跟手展开进度（null=内部动画收尾；Float=手势实时驱动）。
    var playerSheetProgress by remember { mutableStateOf<Float?>(null) }
    // 岛内播放状态栏是否展开：拉手上拉分段拉出，默认隐藏（无曲目也有播放条空壳）。
    var playerBarVisible by remember { mutableStateOf(false) }

    // 岛实时高度（dp）：PlayerCapsule 上报，供展开壳底部让位、动态适配岛高变化。
    var islandHeightDp by remember { mutableStateOf(0f) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<Home> { HomeScreen() }
            composable<Library> {
                LibraryScreen(
                    onOpenChart = { id, name ->
                        navController.navigate(ChartDestination(chartId = id, name = name))
                    },
                )
            }
            composable<ChartDestination> { entry ->
                val args = entry.toRoute<ChartDestination>()
                val listVm: TrackListViewModel = hiltViewModel()
                val listState by listVm.uiState.collectAsStateWithLifecycle()
                val listCol = (listState as? TrackListUiState.Ready)?.collection
                LaunchedEffect(listCol) { listCollection = listCol }
                LaunchedEffect(listCol) {
                    listPlayAll = { listCol?.let(listVm::onPlayAll) }
                }
                DisposableEffect(Unit) {
                    onDispose {
                        listCollection = null
                        listPlayAll = null
                        listActionsOffscreen = false
                    }
                }
                TrackListScreen(
                    source = "chart",
                    id = args.chartId,
                    title = args.name,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { playerSheetOpen = true },
                    onActionsOffscreen = { listActionsOffscreen = it },
                )
            }
            composable<Search> { SearchScreen() }
            composable<Profile> {
                ProfileScreen(
                    onOpenTracks = { source, id, title ->
                        navController.navigate(TrackListDestination(source, id, title))
                    },
                    onWebLogin = { navController.navigate(WebLogin) },
                    onOpenPlayer = { playerSheetOpen = true },
                    islandHeight = islandHeightDp,
                )
            }
            composable<WebLogin> {
                // 复用 Profile tab 的 ViewModel（同一 backstack entry 作用域）：
                // WebLogin 页持有的是临时 entry 自己的实例，登录成功 loadProfile
                // 只会更新临时实例，返回即销毁，Profile 页不会自动刷新。
                val profileVm: ProfileViewModel = hiltViewModel(
                    viewModelStoreOwner = navController.getBackStackEntry<Profile>(),
                )
                WebLoginScreen(
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    vm = profileVm,
                )
            }
            composable<TrackListDestination> { entry ->
                val args = entry.toRoute<TrackListDestination>()
                val listVm: TrackListViewModel = hiltViewModel()
                val listState by listVm.uiState.collectAsStateWithLifecycle()
                val listCol = (listState as? TrackListUiState.Ready)?.collection
                LaunchedEffect(listCol) { listCollection = listCol }
                LaunchedEffect(listCol) {
                    listPlayAll = { listCol?.let(listVm::onPlayAll) }
                }
                DisposableEffect(Unit) {
                    onDispose {
                        listCollection = null
                        listPlayAll = null
                        listActionsOffscreen = false
                    }
                }
                TrackListScreen(
                    source = args.source,
                    id = args.id,
                    title = args.title,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { playerSheetOpen = true },
                    onActionsOffscreen = { listActionsOffscreen = it },
                )
            }
        }

        // 落地常驻岛：永远组合在底部（播放页是全屏不透明覆盖层，打开时盖住岛、
        // 收起时无缝露出它——不用 AnimatedVisibility 进出场动画，否则收起播放页时
        // 岛自带的滑入动画会和播放页的缩回动画打架，观感错乱）。
        PlayerCapsule(
            selected = selectedTab,
            player = player,
            playerBarVisible = playerBarVisible,
            onTogglePlayerBar = { playerBarVisible = !playerBarVisible },
            onSelectTab = { tab ->
                navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onPullUp = {
                // 上拉开播放页：定格全屏。进度交给内部动画收尾（从当前进度续跑到 1）。
                playerBarVisible = true
                playerSheetOpen = true
                playerSheetProgress = null
            },
            onSheetProgress = { p ->
                // 跟手：进度>0 显示播放页壳并驱动其几何；
                // =0 是回弹 settle：保留壳，progress=0 驱动壳滑下，PlayerSheet 完成后卸载。
                playerSheetOpen = true
                playerSheetProgress = p
            },
            actionVisible = isListDetail && listActionsOffscreen,
            onPlayAll = { listPlayAll?.invoke() },
            onPlaceholderAction = {
                android.widget.Toast.makeText(context, "开发中", android.widget.Toast.LENGTH_SHORT).show()
            },
            onIslandHeightChange = { islandHeightDp = it },
            sheetOpen = playerSheetOpen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )

        // 全屏播放页：从底部跟手升起成沉浸全屏（覆盖岛）。盖在最上层。
        // 跟手时 progress 驱动壳位移（1:1 跟手）；定格后走内部动画续跑到全屏。
        if (playerSheetOpen) {
            PlayerSheet(
                onDismiss = {
                    playerSheetOpen = false
                    playerSheetProgress = null
                },
                progress = playerSheetProgress,
            )
        }
    }
}
