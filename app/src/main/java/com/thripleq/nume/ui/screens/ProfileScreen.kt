package com.thripleq.nume.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.Account
import com.thripleq.nume.core.repo.PlaylistSummary
import com.thripleq.nume.core.repo.ProfileData
import com.thripleq.nume.ui.components.ExpandableShell
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.profile.ProfileUiState
import com.thripleq.nume.ui.profile.ProfileViewModel
import com.valentinilk.shimmer.shimmer

/**
 * 我的页：四颗胶囊（喜欢的音乐 / 已购 / 创建的歌单 / 收藏的歌单）。
 * 每一颗都是「胶囊 → 全屏面板」：点击任意胶囊，从胶囊位置伸展成全屏列表面板
 * （[ExpandableShell]）。喜欢的音乐 / 已购是曲目列表；创建 / 收藏是歌单网格面板，
 * 点网格内的歌单再进入该歌单的曲目列表（navigation）。
 *
 * 底部落地岛全程常驻；面板内列表不再抬岛让位，内容自然滚到岛下方。
 */
@Composable
fun ProfileScreen(
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    onWebLogin: () -> Unit,
    onOpenPlayer: () -> Unit = {},
    islandHeight: Float = 0f,
    onShellOpenChange: (Boolean) -> Unit = {},
    vm: ProfileViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    // 当前打开的面板（null = 无面板）。
    var panel by remember { mutableStateOf<ProfilePanel?>(null) }

    // 面板打开时通知上层收起底部导航（保留迷你播放条）；离开页面时复位。
    val shellOpen = panel != null
    LaunchedEffect(shellOpen) { onShellOpenChange(shellOpen) }
    DisposableEffect(Unit) { onDispose { onShellOpenChange(false) } }
    // 被点击胶囊的屏幕坐标（ExpandableShell 动画起点；点哪颗就从哪颗起跳）。
    var panelRect by remember { mutableStateOf<Rect?>(null) }
    val uid = (state as? ProfileUiState.LoggedIn)?.data?.account?.uid?.toString()
    // 胶囊壳底部让位量 = 导航岛实时高度（dp，由 PlayerCapsule 上报，含拉手+nav行+手势条 inset）。
    // 岛变高（拉播放条/操作行出现）时壳底同步下移，壳与岛融为一体、动态适配。
    val islandClearance = with(LocalDensity.current) { islandHeight.dp }

    Box(Modifier.fillMaxSize()) {
        // 避让必须放在滚动内容内部（同 TrackListScreen 的 contentPadding 做法）：
        // 放在外层 padding 会在岛背后留一条永久空白带，卡片进不去、岛像贴在画布上。
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            when (val s = state) {
                ProfileUiState.Loading -> ProfileSkeleton()
                is ProfileUiState.Error -> ErrorRow { vm.refresh() }
                // 未登录也先把完整窗口摆好：登录卡置顶，四个区块以占位呈现，
                // 结构与已登录完全一致，点击任意区块引导登录。
                ProfileUiState.LoggedOut -> LoggedOutContent(onWebLogin)
                is ProfileUiState.LoggedIn -> LoggedInContent(
                    data = s.data,
                    onOpenTracks = onOpenTracks,
                    onOpenPanel = { target, rect ->
                        panelRect = rect
                        panel = target
                    },
                )
            }
        }

        // 全屏列表面板：从被点击胶囊的位置伸展成全屏。
        panel?.let { target ->
            ProfilePanel(
                target = target,
                uid = uid,
                onOpenPlayer = onOpenPlayer,
                onOpenTracks = onOpenTracks,
                recessedBottom = islandClearance,
                capsuleRect = panelRect,
                onDismiss = { panel = null },
            )
        }
    }
}

/** 我的页可打开的全屏面板类型。 */
private sealed interface ProfilePanel {
    /** 曲目列表（喜欢的音乐 / 已购）。 */
    data class Tracks(val source: String, val id: String, val title: String) : ProfilePanel

    /** 歌单网格（创建 / 收藏）。 */
    data class Playlists(val title: String, val playlists: List<PlaylistSummary>) : ProfilePanel
}

/* ── header states ─────────────────────────────────────── */

/** 我的页骨架：与已登录内容同构——用户卡 + 四颗胶囊（内部图标/标题/尾部占位）。 */
@Composable
private fun ProfileSkeleton() {
    Column(
        Modifier
            .fillMaxWidth()
            .shimmer(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(Modifier.size(64.dp), CircleShape)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonLine(widthFraction = 0.4f, height = 18.dp)
                SkeletonLine(widthFraction = 0.22f, height = 12.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        repeat(4) {
            SkeletonCapsule()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SkeletonCapsule() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(36.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(14.dp))
        SkeletonLine(widthFraction = 0.34f, height = 16.dp)
        Spacer(Modifier.weight(1f))
        SkeletonBox(Modifier.size(24.dp), CircleShape)
    }
}

@Composable
private fun ErrorRow(onRetry: () -> Unit) {
    Column(Modifier.padding(vertical = 24.dp)) {
        Text("加载失败", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun LoggedOutContent(onLogin: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth()) {
            LoginCard(onLogin)
            Spacer(Modifier.height(16.dp))

            PlaceholderSectionRow(
                icon = Icons.Filled.Favorite,
                title = "喜欢的音乐",
                onLogin = onLogin,
            )
            PlaceholderSectionRow(
                icon = Icons.Filled.ShoppingCart,
                title = "已购",
                onLogin = onLogin,
            )

            Spacer(Modifier.height(12.dp))
            PlaceholderSectionHeader("收藏的歌单")
            Spacer(Modifier.height(8.dp))
            PlaceholderPlaylistGrid(onLogin)

            Spacer(Modifier.height(16.dp))
            PlaceholderSectionHeader("创建的歌单")
            Spacer(Modifier.height(8.dp))
            PlaceholderPlaylistGrid(onLogin)
        }
    }
}

@Composable
private fun LoginCard(onLogin: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onLogin() },
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("登录网易云", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "使用官方网页登录,解锁喜欢 / 已购 / 歌单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "登录",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PlaceholderSectionRow(
    icon: ImageVector,
    title: String,
    onLogin: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onLogin),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                "登录后查看",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun PlaceholderPlaylistGrid(onLogin: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            PlaceholderPlaylistCell(
                onLogin = onLogin,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlaceholderPlaylistCell(
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onLogin),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "未登录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth(0.8f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth(0.45f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/* ── logged-in content ─────────────────────────────────── */

@Composable
private fun LoggedInContent(
    data: ProfileData,
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    onOpenPanel: (ProfilePanel, Rect?) -> Unit,
) {
    // 强制 LocalContentColor = onSurface, 兜底所有未显式指定 color 的 Text
    // (Material You 在某些设备/壁纸下派生的 onBackground 偏深, 不指定 color
    // 的 Text 会显示成接近背景的颜色, 在深色主题下看不清)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth()) {
            UserCard(data.account)
            Spacer(Modifier.height(8.dp))

            // 四颗胶囊：点击各自打开对应全屏面板，从被点击那颗的位置撑开转场。
            ClickableCapsule(
                icon = Icons.Filled.Favorite,
                title = "喜欢的音乐",
                summary = data.likedCount.toString(),
                onClick = { rect ->
                    onOpenPanel(
                        ProfilePanel.Tracks(
                            source = "liked",
                            id = "",
                            title = "喜欢的音乐",
                        ),
                        rect,
                    )
                },
            )

            Spacer(Modifier.height(8.dp))

            ClickableCapsule(
                icon = Icons.Filled.ShoppingCart,
                title = "已购",
                summary = (data.purchasedSongCount + data.purchasedAlbums.size).toString(),
                onClick = { rect ->
                    onOpenPanel(
                        ProfilePanel.Tracks(
                            source = "purchased",
                            id = "",
                            title = "已购",
                        ),
                        rect,
                    )
                },
            )

            Spacer(Modifier.height(8.dp))

            ClickableCapsule(
                icon = Icons.Filled.List,
                title = "创建的歌单",
                summary = data.createdPlaylists.size.toString(),
                onClick = { rect ->
                    onOpenPanel(
                        ProfilePanel.Playlists(
                            title = "创建的歌单",
                            playlists = data.createdPlaylists,
                        ),
                        rect,
                    )
                },
            )

            Spacer(Modifier.height(8.dp))

            ClickableCapsule(
                icon = Icons.Filled.Star,
                title = "收藏的歌单",
                summary = data.subscribedPlaylists.size.toString(),
                onClick = { rect ->
                    onOpenPanel(
                        ProfilePanel.Playlists(
                            title = "收藏的歌单",
                            playlists = data.subscribedPlaylists,
                        ),
                        rect,
                    )
                },
            )
        }
    }
}

/**
 * 胶囊头部行：图标（secondaryContainer 圆角块）+ 标题 + 右侧 trailing。
 * 「我的」页胶囊与展开壳的标题栏共用本实现，保证颜色与文字/图形相对位置一致
 * （衔接对齐契约：壳标题栏必须与胶囊头部同源）。
 */
@Composable
private fun CapsuleHeader(
    icon: ImageVector,
    title: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit = {},
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** 可展开胶囊：头部（图标+标题+摘要+旋转箭头）。点击打开全屏面板（自带撑开起点）。 */
@Composable
private fun ClickableCapsule(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selfRect by remember { mutableStateOf<Rect?>(null) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                selfRect = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
            },
        shape = RoundedCornerShape(18.dp),
    ) {
        // vertical padding 放内部：保证 onGloballyPositioned 测到的 Card bounds
        // 就是视觉胶囊本身，展开壳收起时的终点与它精确重合。
        Column(Modifier.padding(vertical = 4.dp)) {
            CapsuleHeader(
                icon = icon,
                title = title,
                onClick = { onClick(selfRect) },
                trailing = {
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "打开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun UserCard(account: Account) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape)) {
            if (account.avatarUrl != null) {
                val context = LocalContext.current
                val model = remember(account.avatarUrl) {
                    ImageRequest.Builder(context).data(account.avatarUrl).size(128).build()
                }
                val painter = rememberAsyncImagePainter(model)
                ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
                Image(
                    painter = painter,
                    contentDescription = account.nickname,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                account.nickname,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (account.vipType > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "VIP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 未登录占位用的标题：不显示计数，与已登录区块保持同一段式。 */
@Composable
private fun PlaceholderSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 通用全屏面板：从胶囊位置（[capsuleRect]）伸展成 `surfaceContainerHighest` 胶囊壳。
 * 内容按 [target] 分派：曲目列表 → [TrackListScreen]；歌单网格 → [PlaylistGridPanel]。
 */
@Composable
private fun ProfilePanel(
    target: ProfilePanel,
    uid: String?,
    onOpenPlayer: () -> Unit,
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    recessedBottom: Dp,
    capsuleRect: Rect?,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val statusBarTopPx = with(density) { WindowInsets.statusBars.getTop(this).toFloat() }

    ExpandableShell(
        fromRect = capsuleRect,
        fullTopPx = statusBarTopPx,
        shapeCornerDp = 18.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        recessedBottom = recessedBottom,
        onDismiss = onDismiss,
        header = { onClose ->
            // 与胶囊头部同源（CapsuleHeader）：颜色/文字/图形相对位置一致，
            // 收起动画最后一帧精确对齐。trailing 换为"点此收起"提示。
            CapsuleHeader(
                icon = target.icon(),
                title = target.title(),
                onClick = onClose,
                trailing = {
                    Text(
                        "点此收起",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "收起",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
        },
        content = {
            // 圆角窟窿：列表嵌在一圈圆角内凹区域，surface 与外壳形成凹陷对比。
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                when (target) {
                    is ProfilePanel.Tracks -> {
                        val src = if (uid != null && target.source == "liked") uid else target.id
                        TrackListScreen(
                            source = target.source,
                            id = src,
                            title = target.title,
                            onBack = onDismiss,
                            onOpenPlayer = onOpenPlayer,
                            showTopBar = false,
                            // 壳顶标题栏已示集合名，封面不再重复。
                            showName = false,
                        )
                    }
                    is ProfilePanel.Playlists -> PlaylistGridPanel(
                        title = target.title,
                        playlists = target.playlists,
                        onOpenTracks = onOpenTracks,
                    )
                }
            }
        },
    )
}

private fun ProfilePanel.icon(): ImageVector = when (this) {
    is ProfilePanel.Tracks -> if (source == "liked") Icons.Filled.Favorite else Icons.Filled.ShoppingCart
    is ProfilePanel.Playlists -> Icons.Filled.List
}

private fun ProfilePanel.title(): String = when (this) {
    is ProfilePanel.Tracks -> title
    is ProfilePanel.Playlists -> title
}

/** 歌单网格面板内容：全屏懒加载网格，点格子进歌单曲目列表。 */
@Composable
private fun PlaylistGridPanel(
    title: String,
    playlists: List<PlaylistSummary>,
    onOpenTracks: (source: String, id: String, name: String) -> Unit,
) {
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无歌单",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    // LazyVerticalGrid 自带滚动，不再外包一层 verticalScroll + 全量 Column：
    // 歌单多时只组合可见格，避免每帧重排整棵树（胶囊壳展开卡顿的主因之一）。
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        items(playlists, key = { it.id }) { p ->
            PlaylistCell(
                playlist = p,
                onClick = { onOpenTracks("playlist", p.id, p.name) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlaylistCell(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            // model 整体 remember：AsyncImagePainter 以 model 为 key，避免每次重组
            // 新建 ImageRequest 重走请求分发；按 320px（160dp 封面 @2x）尺寸请求。
            val context = LocalContext.current
            val model = remember(playlist.coverUrl) {
                playlist.coverUrl?.let {
                    ImageRequest.Builder(context).data(it).size(320).build()
                }
            }
            if (model != null) {
                val painter = rememberAsyncImagePainter(model)
                ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
                Image(
                    painter = painter,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${playlist.trackCount} 首",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

