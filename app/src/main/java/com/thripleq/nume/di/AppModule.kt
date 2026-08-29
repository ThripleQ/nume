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
        // Point libnetease's cookie jar at an app-private file on first use.
        NumeNative.setCookieFile(
            File(context.filesDir, NETEASE_COOKIE_FILE).absolutePath,
        )
        return NetEaseGateway()
    }

    private const val NETEASE_COOKIE_FILE = "netease_cookies.json"
}