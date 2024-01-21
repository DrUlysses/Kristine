plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqlDelight)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = libs.versions.jvmTarget.get()
            }
        }
    }

    jvm()

    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    sourceSets {
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            implementation(libs.okio)
            implementation(libs.koin.core)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.driver)
        }
    }
}

android {
    namespace = "dr.ulysses.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("dr.ulysses")
        }
    }
    linkSqlite = true
}
