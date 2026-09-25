package com.thripleq.nume.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

/**
 * 封面 + 底部渐变遮罩 + 左下角名字（可选元信息）。
 *
 * 横滑大封面卡（起点）与「大封面折进列表」的 banner 头（终点）**共用同一份**——
 * 这是展开/收起对齐契约的关键：p=0 时 banner 恰好等于卡片，收起尾帧精确复位。
 * 按 480px 请求封面，兼顾从卡片（116dp）长到满宽（360dp）的清晰度。
 *
 * @param meta 名字下方的元信息（可多行）；卡片不传 → 仅名字，与旧版一致
 * @param showName 是否显示名字；调用方已在别处显示标题时（如壳顶标题栏）可传 false 避免重复
 * @param scrimTop 渐变遮罩起始位置（0..1，越大遮罩越短）；banner 需承载多行文字故可调高
 * @param scrimAlpha 渐变底部黑度（0..1），保证文字可读
 */
@Composable
fun BigCoverVisual(
    coverUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    showName: Boolean = true,
    scrimTop: Float = 0.5f,
    scrimAlpha: Float = 0.66f,
) {
    Box(modifier) {
        val context = LocalContext.current
        val model = remember(coverUrl) {
            coverUrl?.let { ImageRequest.Builder(context).data(it).size(480).build() }
        }
        if (model != null) {
            // 占位微光仅在加载中组合，加载完成即移除（不再常驻无限扫光）。
            val painter = rememberAsyncImagePainter(model)
            ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
            Image(
                painter = painter,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        scrimTop to Color.Transparent,
                        1f to Color.Black.copy(alpha = scrimAlpha),
                    ),
                ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        ) {
            if (showName) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!meta.isNullOrBlank()) {
                if (showName) Spacer(Modifier.height(4.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
