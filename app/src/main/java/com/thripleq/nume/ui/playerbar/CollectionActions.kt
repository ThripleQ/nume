package com.thripleq.nume.ui.playerbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 收藏 / 播放 / 评论 三个胶囊按钮，统一规格（等宽、同高、胶囊圆角、
 *  contentPadding 一致），列表头部与滚动浮岛共用，避免三者形状观感不一。
 *  [showText] 为 false 时只显示图标（浮岛用，更紧凑）。 */
@Composable
fun CollectionActions(
    onPlayAll: () -> Unit,
    onPlaceholderAction: () -> Unit,
    showText: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val contentPadding = PaddingValues(horizontal = 8.dp)
    // 形状/间距固定不变：等宽胶囊 + 等距排列，仅内容随 showText 增减。
    val btnModifier = Modifier.width(96.dp).height(40.dp)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(
            onClick = onPlaceholderAction,
            shape = shape,
            contentPadding = contentPadding,
            modifier = btnModifier,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            if (showText) {
                Spacer(Modifier.width(6.dp))
                Text("收藏")
            }
        }
        Button(
            onClick = onPlayAll,
            shape = shape,
            contentPadding = contentPadding,
            modifier = btnModifier,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            if (showText) {
                Spacer(Modifier.width(6.dp))
                Text("播放")
            }
        }
        FilledTonalButton(
            onClick = onPlaceholderAction,
            shape = shape,
            contentPadding = contentPadding,
            modifier = btnModifier,
        ) {
            Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
            if (showText) {
                Spacer(Modifier.width(6.dp))
                Text("评论")
            }
        }
    }
}
