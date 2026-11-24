plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dr.ulysses"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dr.ulysses.androidApp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        //noinspection ExpiredTargetSdkVersion coz Google Play (see gradle/libs.versions.toml)
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = libs.versions.kristine.get()
        ndk {
            // The Rust library's ABIs; each other one would only add yt-dlp's Python and ffmpeg
            abiFilters += providers.gradleProperty("androidAbis").orNull?.split(",")
                ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // yt-dlp's Python and ffmpeg run from the extracted native library folder
        jniLibs {
            useLegacyPackaging = true
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.android.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.android.jvmTarget.get())
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.koin.android)
    implementation(libs.koin.core)
}
