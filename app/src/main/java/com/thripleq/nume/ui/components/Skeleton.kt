package com.thripleq.nume.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import com.valentinilk.shimmer.shimmer

/**
 * 骨架占位基元：一块圆角纯色矩形。
 *
 * 微光由**容器**上的 `Modifier.shimmer()` 统一提供（见 shimmer 库）——骨架屏根节点挂一次，
 * 所有子占位块共用同一次扫光，不会各转各的。本组件只负责形状与底色，故不做任何动画。
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
}

/** 文字行占位：按屏宽比例给出宽度。 */
@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = 14.dp,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    SkeletonBox(modifier.fillMaxWidth(widthFraction).height(height), shape)
}

/**
 * 图片加载中的微光占位。
 *
 * **仅**在 [painter] 处于 Loading/Empty 时组合并跑扫光动画，加载完成（Success/Error）后
 * 立即从组合中移除——因此不会像「图片下方常驻 shimmer Box」那样空转无限动画
 * （那是之前列表/网格卡顿的主要来源之一）。
 *
 * 与图片同层、放在图片之前即可：加载完成由不透明图片自然覆盖。
 */
@Composable
fun ShimmerImagePlaceholder(
    painter: AsyncImagePainter,
    modifier: Modifier = Modifier,
) {
    val state = painter.state
    if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Empty) {
        Box(
            modifier
                .shimmer()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
