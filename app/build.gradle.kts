import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.serialization)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "com.thripleq.nume"
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.thripleq.nume"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true

        // libnetease is compiled without curl; the app installs an OkHttp
        // transport over JNI at runtime (see jni_glue.c / NumeTransport).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DNE_USE_CURL=OFF")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget("17")
        }
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            // Builds libnetease (submodule) without curl + our JNI glue as one
            // shared lib. See src/main/cpp/CMakeLists.txt.
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging.resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.kotlinx.coroutines.android)

    // core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose UI / Material 3
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // lifecycle + navigation
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // type-safe navigation serializer
    implementation(libs.kotlinx.serialization.json)

    // media playback (Media3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    // networking — the Kotlin/OkHttp layer that replaces libnetease's curl
    implementation(libs.okhttp)

    // image loading
    implementation(libs.coil.compose)

    // dependency injection (Hilt)
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    // Hilt 2.59.2 bundles a kotlin-metadata-jvm that tops out at metadata 2.3.0;
    // Kotlin 2.4.x emits 2.4.0 metadata and would abort the processor. Bump the
    // metadata reader explicitly until a Hilt release tracks Kotlin 2.4.
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.10")
}