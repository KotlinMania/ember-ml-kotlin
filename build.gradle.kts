plugins {
    kotlin("multiplatform") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("com.github.ben-manes.versions") version "0.51.0"
    id("maven-publish")
}


group = "ai.solace.ember"
version = "0.1.0"

repositories {
    mavenLocal()
    mavenCentral()
}

val kcoroLib = layout.projectDirectory.file("external/kcoro/lab/mirror/core/build/lib/libkcoro.a")

kotlin {
    // Native targets for Kotlin Native build
    linuxX64()
    macosX64()
    macosArm64 {
        binaries {
            executable("poc") {
                entryPoint = "ai.solace.klang.poc.main"
            }
            executable("limbBench") {
                entryPoint = "ai.solace.ember.bench.main"
            }
            executable("heapBench") {
                entryPoint = "ai.solace.ember.bench.heapBenchMain"
            }
        }
    }
    mingwX64()

    // JavaScript target (disabled until we add a Node N-API/WASM addon to supply
    // zero-copy C-layout buffers; JS GC heap is too small for parity today).
    /*
    js(IR) {
        browser()
        nodejs()
    }
    */

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:atomicfu:0.23.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("ai.solace:klang:0.7.1")
            }
            kotlin.srcDir("src/commonMain/kotlin")
            resources.srcDir("src/commonMain/resources")
            kotlin.exclude(
                "ai/solace/ember/backend/metal/**",
                "ai/solace/ember/backend/storage/**",
                "ai/solace/ember/actors/**",
                "ai/solace/ember/nn/**",
                "ai/solace/ember/ops/**",
                "ai/solace/ember/tensor/**",
                "ai/solace/ember/training/**",
                "ai/solace/ember/utils/**",
                "ai/solace/emberml/**",
            )
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
            kotlin.setSrcDirs(listOf("src/commonTest/kotlin/ai/solace/limbengine"))
            resources.setSrcDirs(emptyList<File>())
        }
        // Native source sets
        val nativeMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Native-specific dependencies
            }
            kotlin.srcDir("src/nativeMain/kotlin")
        }
        val nativeTest by creating {
            dependsOn(commonTest)
            dependencies {
                // Native-specific test dependencies
            }
            kotlin.setSrcDirs(emptyList<File>())
        }

        // Configure all native targets to use the native source sets
        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }
        val linuxX64Test by getting { dependsOn(nativeTest) }
        val macosX64Main by getting {
            dependsOn(nativeMain)
        }
        val macosX64Test by getting { dependsOn(nativeTest) }
        val macosArm64Main by getting {
            dependsOn(nativeMain)
            kotlin.setSrcDirs(emptyList<File>())
        }
        val macosArm64Test by getting { dependsOn(nativeTest) }
        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }
        val mingwX64Test by getting { dependsOn(nativeTest) }

        // JavaScript source sets (JS target currently disabled in kotlin { } to prioritize native)
    }
}

// Native test verbosity can be enabled ad-hoc via CLI if needed:
//   ./gradlew macosArm64Test --info --rerun-tasks \
//      -Dkotlin.tests.verbose=true

// Convenience task: run the Kotlin/Native test binary directly with a simple, verbose logger
// so per-test names and PASS/FAIL lines are printed to the console.
tasks.register<Exec>("nativeTestVerbose") {
    description = "Run macOS arm64 native tests with verbose logger"
    group = "verification"
    dependsOn("linkDebugTestMacosArm64")
    doFirst {
        val bin = layout.projectDirectory.file("build/bin/macosArm64/debugTest/test.kexe").asFile
        if (!bin.exists()) throw GradleException("Native test binary not found: $bin. Run linkDebugTestMacosArm64 first.")
        commandLine(bin.absolutePath, "--ktest_logger=SIMPLE")
    }
}

// Policy: forbid handled exceptions in tests. Fail build if tests contain try/catch or
// exception-wrapping helpers (assertFails, runCatching, etc.).
// Toggle with -PallowHandledExceptionsInTests=true to bypass, if needed temporarily.
tasks.register("forbidExceptionsInTests") {
    group = "verification"
    description = "Fails if tests contain try/catch or exception-wrapping helpers"
    doLast {
        val allow = (project.findProperty("allowHandledExceptionsInTests") as String?)?.toBoolean() == true
        if (allow) return@doLast

        // Only scan active test sources (we currently point commonTest to this subdir)
        val testRoots = listOf(
            layout.projectDirectory.dir("src/commonTest/kotlin/ai/solace/limbengine"),
        )
        val forbidden = listOf(
            "\\btry\\s*\\{",
            "\\bcatch\\s*\\(",
            "\\bassertFails\\b",
            "\\bassertFailsWith\\b",
            "\\brunCatching\\s*\\(",
            "\\bResult\\.runCatching\\s*\\(",
        ).map { Regex(it) }

        val offenders = mutableListOf<String>()
        testRoots.forEach { root ->
            if (!root.asFile.exists()) return@forEach
            root.asFile.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    if (forbidden.any { it.containsMatchIn(text) }) {
                        offenders += file.relativeTo(layout.projectDirectory.asFile).path
                    }
                }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Handled exceptions are forbidden in tests. Offending files:\n" + offenders.joinToString("\n")
            )
        }
    }
}

tasks.named("macosArm64Test").configure { dependsOn("forbidExceptionsInTests") }
tasks.named("nativeTestVerbose").configure { dependsOn("forbidExceptionsInTests") }

publishing {
    publications {
        withType<MavenPublication> {
            artifactId = "ember-ml-kotlin"
        }
    }
}

tasks.register("test") {
    dependsOn("macosArm64Test")
}
