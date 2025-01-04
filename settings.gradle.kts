rootProject.name = "Kristine"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
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
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
include(":server")
