package com.thripleq.nume

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
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
import com.thripleq.nume.ui.screens.PlayerScreen
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

@Serializable
object Player

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

/** Root of the Compose UI: navigation graph + floating island. */
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
    val isPlayerPage = destination?.hasRoute<Player>() == true
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

    // 列表详情页（榜单/歌单/专辑/喜欢/已购）的滚动浮岛：
    // TrackListScreen 上报三按钮是否滑出视口 + 当前壳数据，供底部岛切换为操作行。
    var listActionsOffscreen by remember { mutableStateOf(false) }
    var listCollection by remember { mutableStateOf<TrackCollection?>(null) }
    var listPlayAll by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isListDetail = destination?.hasRoute<TrackListDestination>() == true ||
        destination?.hasRoute<ChartDestination>() == true
    // 底部岛的目标高度：列表同步抬高，避免最后一项被播放条/操作浮岛遮挡。
    val islandBottomInset =
        (if (hasTrack) 60.dp else 0.dp) +
            (if (isListDetail && listActionsOffscreen) 57.dp else 0.dp)

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
                // 离开详情页时清空提升状态：避免下次进入（或进入另一歌单的首帧）误用旧数据。
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
                    onOpenPlayer = { navController.navigate(Player) },
                    onActionsOffscreen = { listActionsOffscreen = it },
                    bottomInset = islandBottomInset,
                )
            }
            composable<Search> { SearchScreen() }
            composable<Profile> {
                ProfileScreen(
                    onOpenTracks = { source, id, title ->
                        navController.navigate(TrackListDestination(source, id, title))
                    },
                    onWebLogin = { navController.navigate(WebLogin) },
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
                // 离开详情页时清空提升状态：避免下次进入（或进入另一歌单的首帧）误用旧数据。
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
                    onOpenPlayer = { navController.navigate(Player) },
                    onActionsOffscreen = { listActionsOffscreen = it },
                    bottomInset = islandBottomInset,
                )
            }
            composable<Player> { PlayerScreen() }
        }

        // 合成岛屿：导航 tab 平时可见；详情页收起导航、播放条下滑占位；
        // 列表详情页滚过头部三按钮时，播放条上移、操作浮岛从下滑入；
        // 播放页整个淡出。无曲目时只有导航行，不进详情页时整岛隐藏。
        AnimatedVisibility(
            visible = !isPlayerPage &&
                (isTabPage || hasTrack || (isListDetail && listActionsOffscreen)),
            enter = fadeIn(tween(240)) + slideInVertically(tween(240), initialOffsetY = { it }),
            exit = fadeOut(tween(260)) + slideOutVertically(tween(260), targetOffsetY = { it / 4 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(vertical = 14.dp),
        ) {
            PlayerCapsule(
                navVisible = isTabPage,
                selected = selectedTab,
                player = player,
                onSelectTab = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenPlayer = { navController.navigate(Player) },
                actionVisible = isListDetail && listActionsOffscreen,
                onPlayAll = { listPlayAll?.invoke() },
                onPlaceholderAction = {
                    android.widget.Toast.makeText(context, "开发中", android.widget.Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}
