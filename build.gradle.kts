// Top-level build file. AGP 9 provides built-in Kotlin, so no kotlin-android
// plugin is applied; the Compose compiler plugin is still applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose) apply false
}