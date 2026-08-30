package com.thripleq.nume.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.Account
import com.thripleq.nume.core.repo.Album
import com.thripleq.nume.core.repo.PlaylistSummary
import com.thripleq.nume.ui.profile.ProfileData
import com.thripleq.nume.ui.profile.ProfileUiState
import com.thripleq.nume.ui.profile.ProfileViewModel

/**
 * 我的 tab：未登录时点登录卡片直接打开官方网页登录；登录后展示
 * 用户卡片 + 四个区块（喜欢的音乐 / 已购 / 收藏的歌单 / 创建的歌单）。
 */
@Composable
fun ProfileScreen(
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    onWebLogin: () -> Unit,
) {
    val vm: ProfileViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        when (val s = state) {
            ProfileUiState.Loading -> LoadingRow(busy)
            is ProfileUiState.Error -> ErrorRow { vm.refresh() }
            ProfileUiState.LoggedOut -> LoggedOutHeader(busy, onWebLogin)
            is ProfileUiState.LoggedIn -> LoggedInContent(
                data = s.data,
                onOpenTracks = onOpenTracks,
            )
        }
    }
}

/* ── header states ─────────────────────────────────────── */

@Composable
private fun LoadingRow(busy: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("加载中…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun LoggedOutHeader(busy: Boolean, onLogin: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(enabled = !busy) { onLogin() },
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("登录网易云", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "使用官方网页登录，同步喜欢 / 已购 / 歌单",
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

/* ── logged-in content ─────────────────────────────────── */

@Composable
private fun LoggedInContent(
    data: ProfileData,
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
) {
    // 强制 LocalContentColor = onSurface, 兜底所有未显式指定 color 的 Text
    // (Material You 在某些设备/壁纸下派生的 onBackground 偏深, 不指定 color
    // 的 Text 会显示成接近背景的颜色, 在深色主题下看不清)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            UserCard(data.account)
            Spacer(Modifier.height(16.dp))

            SectionRow(
                icon = Icons.Filled.Favorite,
                title = "喜欢的音乐",
                count = data.likedCount,
                onClick = { onOpenTracks("liked", data.account.uid.toString(), "喜欢的音乐") },
            )
            SectionRow(
                icon = Icons.Filled.ShoppingCart,
                title = "已购",
                count = data.purchasedSongCount + data.purchasedAlbums.size,
                onClick = { onOpenTracks("purchased", "", "已购") },
            )

            if (data.purchasedAlbums.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "已购专辑",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                data.purchasedAlbums.forEach { album ->
                    AlbumRow(album) {
                        onOpenTracks("album", album.id, album.name)
                    }
                }
            }

            if (data.createdPlaylists.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionHeader("创建的歌单", data.createdPlaylists.size)
                Spacer(Modifier.height(8.dp))
                PlaylistGrid(data.createdPlaylists) { id, name ->
                    onOpenTracks("playlist", id, name)
                }
            }

            if (data.subscribedPlaylists.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionHeader("收藏的歌单", data.subscribedPlaylists.size)
                Spacer(Modifier.height(8.dp))
                PlaylistGrid(data.subscribedPlaylists) { id, name ->
                    onOpenTracks("playlist", id, name)
                }
            }
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
                AsyncImage(
                    model = Uri.parse(account.avatarUrl),
                    contentDescription = account.nickname,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
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

@Composable
private fun SectionRow(
    icon: ImageVector,
    title: String,
    count: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
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
                modifier = Modifier.weight(1f),
            )
            Text(
                "$count",
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
private fun SectionHeader(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistGrid(
    playlists: List<PlaylistSummary>,
    onClick: (id: String, name: String) -> Unit,
) {
    // Non-lazy two-column grid: playlists are bounded in number, and a lazy
    // grid must not nest inside the outer scrollable Column.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        playlists.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p ->
                    PlaylistCell(
                        playlist = p,
                        onClick = { onClick(p.id, p.name) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
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
                AsyncImage(
                    model = model,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
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

@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            if (album.coverUrl != null) {
                AsyncImage(
                    model = Uri.parse(album.coverUrl),
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                album.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            album.artist.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

