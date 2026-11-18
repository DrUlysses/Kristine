import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.logback.classic)
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
