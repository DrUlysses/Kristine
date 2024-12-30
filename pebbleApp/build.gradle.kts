import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import java.util.*

val platforms = listOf("basalt", "aplite", "chalk", "common", "diorite", "emery")
val pebblePlatform: String? by project

plugins {
    id("de.undercouch.download") version "5.6.0"
    alias(libs.plugins.multiplatform)
}

kotlin {
    // The whole is made for fun. Use C instead - it's more efficient.
    linuxArm32Hfp("native") {
        binaries.executable()
        compilations["main"].cinterops {
            val pebbleInterop by creating {
                definitionFile = file("src/nativeInterop/cinterop/${pebblePlatform ?: "basalt"}.def")
            }
        }
    }
}

val libDestination = layout.buildDirectory.dir("interopLib").get().apply { asFile.mkdirs() }

val downloadTask = tasks.create<Download>("download") {
    src("https://github.com/aveao/PebbleArchive/raw/refs/heads/master/SDKCores/sdk-core-4.3.tar.bz2")
    dest(libDestination.file("lib.tar.bz2"))
    overwrite(false)
}

val unpackTask = tasks.create("unpackSdk") {
    val markerFile = libDestination.file("$name.completed")

    dependsOn(downloadTask)

    inputs.dir(libDestination)
    outputs.file(markerFile)

    doLast {
        delete(markerFile)
        logger.lifecycle("Extracting SDK to $libDestination...")
        delete(libDestination.file("library"))
        copy {
            from({ tarTree(resources.bzip2(libDestination.file("lib.tar.bz2"))) })
            into(libDestination.file("library"))
            includeEmptyDirs = false
            eachFile {
                path = path.split("/", limit = 2)[1]
            }
        }
        delete(libDestination.file("lib.tar.bz2"))
        markerFile.asFile.writeText("generated ${Date()}")
    }
}

val defDir = projectDir.resolve("src/nativeInterop/cinterop").apply { mkdirs() }

val generateDefFiles = tasks.register("generateDefFiles") {
    group = "interop setup"
    description = "Generates .def files for Pebble platforms."

    if (defDir.listFiles().isEmpty())
        dependsOn(unpackTask)

    inputs.dir(libDestination)
    outputs.dir(defDir)

    doLast {
        platforms.forEach { platform ->
            val platformDir = libDestination.dir("library/pebble").asFile.resolve(platform)
            val defFile = defDir.resolve("$platform.def")

            if (!platformDir.exists()) {
                println("Warning: Directory $platformDir does not exist. Skipping $platform.")
                return@forEach
            }

            println("Generating .def file for $platform...")

            val includeDir = platformDir.resolve("include")
            val headerFiles = includeDir.walkTopDown()
                .filter {
                    it.isFile &&
                            it.extension == "h" &&
                            it.name != "pebble_worker.h"
                }
                .map { "    ${it.relativeTo(includeDir).path.replace("\\", "/")}" }
                .joinToString(" \\\n")

            defFile.writeText(
                """
allowedOverloadsForCFunctions = true

# Compiler options
compilerOpts = -I${platformDir.absolutePath}/include

# Linker options
linkerOpts = -L${platformDir.absolutePath}/lib -lpebble

# Headers to include
headers = $headerFiles
                """.trimIndent()
            )
        }

        println("Generated .def files in ${defDir.absolutePath}.")
    }
}

tasks.withType<CInteropProcess> {
    dependsOn(generateDefFiles)
}

tasks.withType<Wrapper> {
    gradleVersion = "8.10"
    distributionType = Wrapper.DistributionType.BIN
}
