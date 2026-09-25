package com.thripleq.nume.di

import android.content.Context
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NumeNative
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** Hilt wiring for the libnetease data gateway. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGateway(@ApplicationContext context: Context): NetEaseGateway {
        // libnetease 启动契约：Android 宿主拿不到 NE_API_BASE 环境变量，
        // 必须显式设置 API base（request.h 的嵌入宿主要求）。
        NumeNative.setApiBase("https://music.163.com")
        // Point libnetease's cookie jar at an app-private file on first use.
        NumeNative.setCookieFile(
            File(context.filesDir, NETEASE_COOKIE_FILE).absolutePath,
        )
        return NetEaseGateway()
    }

    private const val NETEASE_COOKIE_FILE = "netease_cookies.json"
}