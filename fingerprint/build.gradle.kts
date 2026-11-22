import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Chromaprint fingerprints from Rust (rusty-chromaprint, Symphonia decoding) for the JVM and Android, instead of fpcalc
plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.oxikt)
}

uniffi {
    generateFromLibrary {
        packageName = "dr.ulysses.fingerprint"
    }
}

kotlin {
    android {
        namespace = "dr.ulysses.fingerprint"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // The OxiKT runtime's inline functions are JVM 17 bytecode
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
        }
    }

    jvm()

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmTarget.get()))
    }
}
