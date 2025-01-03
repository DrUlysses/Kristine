rootProject.name = "Kristine"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven {
            url = uri("https://maven.pkg.github.com/edna-aa/sqldelight")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "Ulysses"
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("github.maven.repo.token").orNull
                            ?: error("GitHub token not found in CI or gradle.properties")
            }
            // Restrict this repository to specific versions containing "-wasm"
            content {
                includeGroup("app.cash.sqldelight") // Restrict to the group
                includeVersionByRegex(
                    "app.cash.sqldelight",
                    ".*",
                    ".*-wasm.*"
                ) // Match any artifact in the group with versions containing "-wasm"
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental")
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven {
            url = uri("https://maven.pkg.github.com/edna-aa/sqldelight")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "Ulysses"
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("github.maven.repo.token").orNull
                            ?: error("GitHub token not found in CI or gradle.properties")
            }
            // Restrict this repository to specific versions containing "-wasm"
            content {
                includeGroup("app.cash.sqldelight") // Restrict to the group
                includeVersionByRegex(
                    "app.cash.sqldelight",
                    ".*",
                    ".*-wasm.*"
                ) // Match any artifact in the group with versions containing "-wasm"
            }
        }
        google()
        mavenCentral()
    }
}

include(":composeApp")
include(":server")
