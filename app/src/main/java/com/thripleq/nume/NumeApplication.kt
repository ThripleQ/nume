package com.thripleq.nume

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** App entry point; Hilt generates the dependency graph rooted here. */
@HiltAndroidApp
class NumeApplication : Application()