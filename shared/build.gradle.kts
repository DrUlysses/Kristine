@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqlDelight)
}

kotlin {
    // Shared library; app entry points live in :androidApp, :desktopApp and :webApp
    android {
        namespace = "dr.ulysses.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(libs.versions.android.jvmTarget.get())
        }
    }

    jvm()

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmTarget.get()))
    }

    wasmJs {
        browser()
    }
    applyDefaultHierarchyTemplate {
        common {
            // Android and desktop share the JVM-only code: Ktor server, UDP discovery, jaudiotagger
            group("jvmCommon") {
                withCompilations {
                    it.platformType == KotlinPlatformType.jvm || it.platformType == KotlinPlatformType.androidJvm
                }
            }
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
                optIn("kotlin.time.ExperimentalTime")
            }
        }
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.util)
            implementation(libs.compose.material3.adaptive.navigation.suite)
            implementation(libs.androidx.navigation.compose)
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
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.sqldelight.coroutines.extension)
            implementation(libs.androidx.material3.adaptive)
        }
        getByName("jvmCommonMain").dependencies {
            implementation(libs.jthink.jaudiotagger)
            implementation(libs.kotlinx.io.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.network.tls.certificates)
        }
        androidMain.dependencies {
            implementation(libs.accompanist.permissions)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.dash)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.ui)
            implementation(libs.koin.android)
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            implementation(libs.appdirs)
            implementation(libs.caprica.vlcj)
            implementation(libs.sqldelight.driver)
        }
        wasmJsMain {
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.sqldelight.webworker.driver)
            implementation(npm("sql.js", libs.versions.sqlJs.get()))
            implementation(npm("@cashapp/sqldelight-sqljs-worker", libs.versions.sqlDelight.get()))
        }
    }
}

compose.resources {
    // desktopApp's main.kt uses Res for the window icon
    publicResClass = true
}

// shortcut: shared has no wasm Compose UI tests, and this check demands an executable binary in a library; re-enable when wasm UI tests are added
tasks.named { it == "checkComposeUiTestConfigurationForWasmJs" }.configureEach { enabled = false }

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
