Build and Run
=============

Targets
- macOS arm64 is the primary native run target; JS compiles for purity checks.

Gradle tasks
- Run (native debug, macOS arm64): `./gradlew run`
- Build native release: `./gradlew buildMac`
- JS dev compile: `./gradlew compileDevelopmentExecutableKotlinJs`

Entry point
- Native entry is `ai.solace.klang.poc.main` (set in build.gradle.kts).

Notes
- We removed JVM deps; project is Kotlin Multiplatform (native + JS).

