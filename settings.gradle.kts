rootProject.name = "ember-ml-kotlin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.9.0")
}

// Source dependency: pull KLang directly from GitHub and map it to the
// artificial coordinates `ai.solace:klang`. We pin to a specific commit for
// reproducibility while keeping the dependency declarative in Gradle.
