@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.compose.hot.reload)
}

composeCompiler {
    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider {
                compilerOptions {
                    jvmTarget = JvmTarget.fromTarget(libs.versions.android.jvmTarget.get())
                }
            }
        }
    }

    jvm()

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmTarget.get()))
    }

    wasmJs {
        binaries.executable()
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(project.rootDir.path)
                        add(project.projectDir.path)
                    }
                }
            }
        }
    }
    applyDefaultHierarchyTemplate {
        common {
            withWasmJs()
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }
        commonMain.dependencies {
            implementation(compose.components.resources)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.bundles.ktor.client)
            implementation(libs.bundles.ktor.server)
            implementation(libs.coil.compose)
            implementation(libs.coil.ktor)
            implementation(libs.filekit.compose)
            implementation(libs.kermit)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatformSettings)
            implementation(libs.napier)
            implementation(libs.navigation.compose)
            implementation(libs.sqldelight.coroutines.extension)
        }
        androidMain.dependencies {
            implementation(libs.accompanist.permissions)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.dash)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.ui)
            implementation(libs.jthink.jaudiotagger)
            implementation(libs.koin.android)
            implementation(libs.koin.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.spotify.api.kotlin.core)
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.common)
            implementation(compose.desktop.currentOs)
            implementation(libs.appdirs)
            implementation(libs.caprica.vlcj)
            implementation(libs.jthink.jaudiotagger)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.spotify.api.kotlin.core)
            implementation(libs.sqldelight.driver)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.sqldelight.webworker.driver)
            implementation(npm("sql.js", libs.versions.sqlJs.get()))
            implementation(devNpm("copy-webpack-plugin", libs.versions.webPackPlugin.get()))
        }
    }
}

android {
    val projMinSdk = libs.versions.android.minSdk.get().toInt()
    val projCompileSdk = libs.versions.android.compileSdk.get().toInt()
    val projTargetSdk = libs.versions.android.targetSdk.get().toInt()
    namespace = "dr.ulysses"
    compileSdk = projCompileSdk

    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/resources")
        resources.srcDirs("src/commonMain/resources")
    }

    defaultConfig {
        applicationId = "dr.ulysses.androidApp"
        minSdk = projMinSdk
        targetSdk = projTargetSdk
        versionCode = 1
        versionName = libs.versions.kristine.get()

        // Add placeholder values for Spotify OAuth redirect
        manifestPlaceholders["redirectHostName"] = "localhost"
        manifestPlaceholders["redirectSchemeName"] = "kristine"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Kristine"
            packageVersion = libs.versions.kristine.get()
            linux {
                modules("jdk.security.auth")
            }
        }
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName = "dr.ulysses.database"
            generateAsync = true
            verifyMigrations = false
        }
    }
    linkSqlite = true
}

configurations.all {
    resolutionStrategy {
        force(libs.androidx.core.ktx) // Force a specific version
    }
}
