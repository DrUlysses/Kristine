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

// Runs from the IDE keep using the database and certificate in the project folder
tasks.withType<JavaExec>().configureEach {
    environment("KRISTINE_HOME", System.getenv("KRISTINE_HOME") ?: rootDir.path)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.AppImage)
            packageName = "Kristine"
            packageVersion = libs.versions.kristine.get()
            // From suggestRuntimeModules; without java.sql the packaged app can't open its database
            modules(
                "java.instrument",
                "java.management",
                "java.sql",
                "jdk.security.auth",
                "jdk.unsupported"
            )
        }
    }
}
