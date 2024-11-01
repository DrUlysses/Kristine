import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqlDelight)
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

    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.napier)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.multiplatformSettings)
            implementation(libs.sqldelight.coroutines.extension)
            implementation(libs.navigation.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.appcompat)
            implementation(libs.koin.core)
            implementation(libs.koin.android)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.accompanist.permissions)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.dash)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.session)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.common)
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.driver)
            implementation(libs.appdirs)
            implementation(libs.caprica.vlcj)
        }
        jsMain.dependencies {
            implementation(compose.html.core)
            implementation(libs.ktor.client.js)
            implementation(libs.sqldelight.webworker.driver)
            implementation(npm("sql.js", libs.versions.sqlJs.get()))
            implementation(npm("@cashapp/sqldelight-sqljs-worker", libs.versions.sqlDelight.get()))
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
        }
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("dr.ulysses.database")
            generateAsync.set(true)
        }
    }
    linkSqlite = true
}

// See https://sqlite.org/download.html for the latest wasm build version
val sqlite = 3470000

val sqliteDownload = tasks.register("sqliteDownload") {
    val fileUrl = "https://sqlite.org/2024/sqlite-wasm-$sqlite.zip" // Replace with your file URL
    val outputDir = layout.buildDirectory.dir("tmp") // Replace with your desired output path
    val outputFile = outputDir.get().asFile.resolve("sqlite-wasm-$sqlite.zip")

    // Check if file already exists
    if (outputFile.exists()) {
        // Open a connection to check for modification date
        val connection = URL(fileUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        val serverLastModified = connection.lastModified

        if (outputFile.lastModified() >= serverLastModified) {
            println("File is up-to-date, skipping download.")
            return@register
        }
    }

    outputs.file(outputFile)

    doLast {
        val url = URL(fileUrl)
        val connection = url.openConnection()
        connection.connect()

        if (!outputDir.isPresent)
            outputDir.get().asFile.mkdirs()
        url.openStream().use { input ->
            Files.copy(input, outputFile.toPath())
        }
    }
}

val sqliteUnzip = tasks.register("sqliteUnzip", Copy::class.java) {
    dependsOn(sqliteDownload)
    from(zipTree(layout.buildDirectory.dir("tmp/sqlite-wasm-$sqlite.zip"))) {
        include("sqlite-wasm-$sqlite/jswasm/**")
        exclude("**/*worker1*")

        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(2).toTypedArray())
        }
    }
    into(layout.buildDirectory.dir("sqlite"))
    includeEmptyDirs = false
}

tasks.named("jsProcessResources").configure {
    dependsOn(sqliteUnzip)
}
