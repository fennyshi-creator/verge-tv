import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.xxxx.emby_tv"
    compileSdk {
        version = release(36)
    }
    lint {
        checkReleaseBuilds = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "com.xxxx.emby_tv"
        minSdk = 23
        targetSdk = 36
        versionName = "2.0.66"
        versionCode = 266
//        versionCode = 92

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9 内置 Kotlin：jvmTarget 自动与 targetCompatibility 对齐，无需 kotlinOptions

    androidComponents {
        beforeVariants(selector().withBuildType("release")) { builder ->
            (builder as HasHostTestsBuilder)
                .hostTests[HostTestBuilder.UNIT_TEST_TYPE]
                ?.enable = true
        }
    }
    buildTypes {
        debug {
            // Same debug certificate as every previous sideload, so 2.0.35
            // installs over 2.0.34 without uninstalling.
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":fsplayer"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3)

    implementation("androidx.compose.ui:ui:1.10.0")
    // 必须有这个，它定义了 BoxScope 和基础 Layout
    implementation("androidx.compose.foundation:foundation-layout:1.10.0")

    implementation("androidx.compose.foundation:foundation:1.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")

    implementation("androidx.tv:tv-foundation:1.0.0-alpha11")
    implementation("androidx.tv:tv-material:1.0.1")


    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // 3. Media3 ExoPlayer (2025年最新稳定版 1.5.1)
    // 该版本优化了 4K 杜比视界 (Dolby Vision) 的硬件协商逻辑
    val media3Version = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version") // 用于媒体控制台同步
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
    // 增加 extractor 以增强对 MKV 内置字幕(ASS/SSA/PGS)的解析能力
//    implementation("androidx.media3:media3-extractor:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")

    // 引用你刚才在 settings 里定义的本地模块
//    implementation(project(":decoder_ffmpeg"))

    // 4. 图片加载 (使用 Coil 3)
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // Brotli compression support
    implementation("com.squareup.okhttp3:okhttp-brotli:5.0.0-alpha.14")

    // App Update
    implementation("io.github.azhon:appupdate:4.3.6")

    // 添加AppCompat依赖以解决资源找不到的问题
    implementation("androidx.appcompat:appcompat:1.7.0")
        // JSON处理
    implementation("com.google.code.gson:gson:2.10.1")
    // SharedPreferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // Baseline profiles are packaged for release/profileable builds, not debug.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // QR Code
    implementation("com.google.zxing:core:3.5.2")

    // NanoHTTPD
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    testImplementation("junit:junit:4.13.2")

    implementation("androidx.compose.ui:ui-tooling-preview:1.10.0")
    screenshotTestImplementation("androidx.compose.ui:ui-tooling:1.10.0")
    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha16")
}