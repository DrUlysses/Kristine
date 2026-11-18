@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    wasmJs {
        binaries.executable()
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    // Serve sources to debug inside browser
                    static(project.rootDir.path)
                    static(project.projectDir.path)
                }
            }
        }
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.shared)
            implementation(libs.compose.ui)
            implementation(libs.koin.core)
            implementation(devNpm("copy-webpack-plugin", libs.versions.webPackPlugin.get()))
            // used by webpack.config.d/config.js; no longer hoisted from Kotlin's npm tooling
            implementation(devNpm("terser-webpack-plugin", libs.versions.terserWebpackPlugin.get()))
        }
    }
}
