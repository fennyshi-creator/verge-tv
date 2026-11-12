plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.debugly.fsplayer"
    compileSdk = 36
    // libvulkan.so is in the NDK sysroot from API 24. The player .so needs it at load time.
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }
    lint {
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main") {
            jniLibs.setSrcDirs(listOf("src/main/jniLibs"))
        }
    }
}

dependencies {
}
