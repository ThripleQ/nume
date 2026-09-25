package com.thripleq.nume

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.metrics.performance.JankStats
import com.thripleq.nume.ui.theme.NumeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var jankStats: JankStats? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(): keeps the system splash up until the
        // first Compose frame, then hands off to Theme.Nume (postSplashScreenTheme).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        installJankStats()
        setContent {
            // 禁用 Material You 动态取色: 在某些设备/壁纸下 dynamicDarkColorScheme
            // 派生的 onBackground/onSurface 偏深, 导致未指定 color 的 Text 在深色主题
            // 下显示成接近背景的深色, 看不见. 用我们验证过的 DarkColorScheme.
            NumeTheme(dynamicColor = false) {
                NumeApp()
            }
        }
    }

    /**
     * Frame-jank telemetry via JankStats. In debug it logs every janky frame to
     * logcat; the listener is the single hook to forward to a backend in release.
     * Tracking is enabled only while the activity is resumed to avoid foreground
     * work when the UI is not visible.
     */
    private fun installJankStats() {
        val stats = JankStats.createAndTrack(window) { frame ->
            if (BuildConfig.DEBUG && frame.isJank) {
                Log.w("JankStats", "jank ${frame.frameDurationUiNanos / 1_000_000}ms")
            }
        }
        jankStats = stats
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                stats.isTrackingEnabled = true
            }

            override fun onPause(owner: LifecycleOwner) {
                stats.isTrackingEnabled = false
            }
        })
    }
}
