import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

plugins {
    kotlin("multiplatform") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("com.github.ben-manes.versions") version "0.51.0"
    id("maven-publish")
}


group = "ai.solace.emberml"
version = "0.1.0"

repositories {
    mavenCentral()
}

val kcoroLib = layout.projectDirectory.file("external/kcoro/core/build/lib/libkcoro.a")

val buildKcoro by tasks.registering(Exec::class) {
    group = "kcoro"
    description = "Build kcoro C static library"
    workingDir = file("external/kcoro/core")
    commandLine("make")
    inputs.dir("external/kcoro/core/src")
    inputs.dir("external/kcoro/arch")
    inputs.file("external/kcoro/core/Makefile")
    outputs.file(kcoroLib)
}

val kcoroCppBuildDir = layout.buildDirectory.dir("kcoro_cpp")
val kcoroCppLib = kcoroCppBuildDir.map { it.file("libkcoro_cpp.a") }

val configureKcoroCpp by tasks.registering(Exec::class) {
    group = "kcoro"
    description = "Configure kcoro C++ build"
    commandLine(
        "cmake",
        "-S",
        "external/kcoro_cpp",
        "-B",
        kcoroCppBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
    )
    inputs.file("external/kcoro_cpp/CMakeLists.txt")
    outputs.file(kcoroCppBuildDir.map { it.file("CMakeCache.txt") })
}

val buildKcoroCpp by tasks.registering(Exec::class) {
    group = "kcoro"
    description = "Build kcoro C++ static library"
    dependsOn(configureKcoroCpp)
    commandLine(
        "cmake",
        "--build",
        kcoroCppBuildDir.get().asFile.absolutePath,
        "--config",
        "Release",
    )
    inputs.dir("external/kcoro_cpp/src")
    inputs.dir("external/kcoro_cpp/arch")
    outputs.file(kcoroCppLib)
}

kotlin {
    // Native targets for Kotlin Native build
    linuxX64()
    macosX64()
    macosArm64 {
        binaries {
            executable("poc") {
                entryPoint = "ai.solace.klang.poc.main"
            }
        }
        val kcoroInclude = layout.projectDirectory.dir("external/kcoro/include")
        val kcoroCppInclude = layout.projectDirectory.dir("external/kcoro_cpp/include")
        val kcoroLibDir = layout.projectDirectory.dir("external/kcoro/core/build/lib")
        val kcoroCppLibDirProvider = kcoroCppBuildDir

        compilations["main"].cinterops {
            val kcoro by creating {
                definitionFile = file("src/nativeInterop/cinterop/kcoro.def")
                compilerOpts("-I${kcoroInclude.asFile.absolutePath}")
            }
            val kcoroCpp by creating {
                definitionFile = file("src/nativeInterop/cinterop/kcoro_cpp.def")
                compilerOpts(
                    "-I${kcoroInclude.asFile.absolutePath}",
                    "-I${kcoroCppInclude.asFile.absolutePath}",
                )
            }
        }

        binaries.all {
            linkerOpts(
                "-L${kcoroLibDir.asFile.absolutePath}",
                "-L${kcoroCppLibDirProvider.get().asFile.absolutePath}",
                "-lkcoro",
                "-lkcoro_cpp",
            )
            linkTaskProvider.configure {
                dependsOn(buildKcoro, buildKcoroCpp)
            }
        }
    }
    mingwX64()

    // JavaScript target
    js(IR) {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:atomicfu:0.23.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
            kotlin.srcDir("src/commonMain/kotlin")
            resources.srcDir("src/commonMain/resources")
            // No exclusions for MegaFloat.kt and MegaInteger.kt anymore
            // kotlin.exclude(
            //     "ai/solace/emberml/tensor/bitwise/MegaFloat.kt",
            //     "ai/solace/emberml/tensor/bitwise/MegaInteger.kt"
            // )
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
            kotlin.srcDir("src/commonTest/kotlin")
            resources.srcDir("src/commonTest/resources")
            kotlin.exclude(
                // No exclusions for MegaFloatTest.kt and MegaIntegerTest.kt anymore
                // "ai/solace/emberml/tensor/bitwise/MegaFloatTest.kt",
                // "ai/solace/emberml/tensor/bitwise/MegaIntegerTest.kt",
                "ai/solace/emberml/tensor/bitwise/DebugTest.kt"
                // MegaBinaryStubTest.kt is intentionally not excluded
            )
        }
        // Native source sets
        val nativeMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Native-specific dependencies
            }
        }
        val nativeTest by creating {
            dependsOn(commonTest)
            dependencies {
                // Native-specific test dependencies
            }
        }

        // Configure all native targets to use the native source sets
        val linuxX64Main by getting { dependsOn(nativeMain) }
        val linuxX64Test by getting { dependsOn(nativeTest) }
        val macosX64Main by getting { dependsOn(nativeMain) }
        val macosX64Test by getting { dependsOn(nativeTest) }
        val macosArm64Main by getting { dependsOn(nativeMain) }
        val macosArm64Test by getting { dependsOn(nativeTest) }
        val mingwX64Main by getting { dependsOn(nativeMain) }
        val mingwX64Test by getting { dependsOn(nativeTest) }

        // JavaScript source sets
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.9.0")
            }
        }
        val jsTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-js:1.9.0")
            }
        }
    }
}

tasks.withType<CInteropProcess>().configureEach {
    if (name.contains("Kcoro_cpp", ignoreCase = true)) {
        dependsOn(buildKcoroCpp)
    }
    if (name.contains("Kcoro", ignoreCase = true) && !name.contains("Kcoro_cpp", ignoreCase = true)) {
        dependsOn(buildKcoro)
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            artifactId = "ember-ml-kotlin"
        }
    }
}
