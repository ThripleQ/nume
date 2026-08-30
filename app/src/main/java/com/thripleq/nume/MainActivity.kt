package com.thripleq.nume

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thripleq.nume.ui.theme.NumeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 禁用 Material You 动态取色: 在某些设备/壁纸下 dynamicDarkColorScheme
            // 派生的 onBackground/onSurface 偏深, 导致未指定 color 的 Text 在深色主题
            // 下显示成接近背景的深色, 看不见. 用我们验证过的 DarkColorScheme.
            NumeTheme(dynamicColor = false) {
                NumeApp()
            }
        }
    }
}