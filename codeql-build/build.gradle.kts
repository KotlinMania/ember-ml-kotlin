import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity
import java.util.Properties

plugins {
    base
}

val parentProperties =
    Properties().also { properties ->
        val parentPropertiesFile = layout.projectDirectory.file("../gradle.properties").asFile
        if (parentPropertiesFile.isFile) {
            parentPropertiesFile.inputStream().use(properties::load)
        }
    }

fun propertyValue(
    name: String,
    default: String,
): String = providers.gradleProperty(name).orNull ?: parentProperties.getProperty(name) ?: default

val codeqlKotlinVersion = propertyValue("codeql.kotlin.version", "2.3.21")
val codeqlLanguageVersion =
    propertyValue(
        "kotlin.languageVersion",
        codeqlKotlinVersion.split('.').take(2).joinToString("."),
    )
val codeqlApiVersion = propertyValue("kotlin.apiVersion", codeqlLanguageVersion)
val jvmToolchainVersion = propertyValue("jvm.toolchain", "21")
val androidCompileSdk = propertyValue("android.compileSdk", "34")
val codeqlKotlinCommonSourceSetNames =
    propertyValue("project.codeql.kotlinCommonSourceSets", "commonMain").toSourceSetList()
val commonOptIns =
    listOf(
        "kotlin.time.ExperimentalTime",
        "kotlin.concurrent.atomics.ExperimentalAtomicApi",
        "kotlin.ExperimentalUnsignedTypes",
    )
val defaultCodeqlSourceClasspath =
    listOf(
        "org.jetbrains.kotlin:kotlin-stdlib:$codeqlKotlinVersion",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
    ).joinToString(",")

val codeqlKotlinc by configurations.creating
val codeqlSourceClasspath by configurations.creating
val codeqlAndroidAar by configurations.creating

dependencies {
    add("codeqlKotlinc", "org.jetbrains.kotlin:kotlin-compiler-embeddable:$codeqlKotlinVersion")

    propertyValue("project.dependencies.codeqlSourceClasspath", defaultCodeqlSourceClasspath)
        .splitToSequence(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { add("codeqlSourceClasspath", it) }

    propertyValue("project.dependencies.codeqlAndroidAar", "")
        .splitToSequence(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { add("codeqlAndroidAar", it) }
}

fun String.toSourceSetList(): List<String> =
    splitToSequence(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

fun androidJar(): File {
    val candidateRoots =
        listOfNotNull(
            providers.environmentVariable("ANDROID_HOME").orNull,
            providers.environmentVariable("ANDROID_SDK_ROOT").orNull,
            layout.projectDirectory.dir("../.android-sdk").asFile.absolutePath,
        )
    val androidJar =
        candidateRoots
            .map { File(it).resolve("platforms/android-$androidCompileSdk/android.jar") }
            .firstOrNull { it.isFile }
    return requireNotNull(androidJar) {
        "Android CodeQL extraction requires platforms/android-$androidCompileSdk/android.jar under " +
            "ANDROID_HOME, ANDROID_SDK_ROOT, or ../.android-sdk"
    }
}

fun registerCodeqlCompileTask(
    taskName: String,
    sourceSetNames: List<String>,
    includeAndroidClasspath: Boolean,
) {
    tasks.register<JavaExec>(taskName) {
        description =
            "Compile ${sourceSetNames.joinToString(",")} Kotlin sources " +
                "with kotlinc $codeqlKotlinVersion for CodeQL Java/Kotlin extraction."
        group = "verification"
        classpath(codeqlKotlinc)
        mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")

        val sourceRoot = layout.projectDirectory.dir("..")
        val outDir = layout.buildDirectory.dir("classes/kotlin/$taskName")
        val aarExtractDir = layout.buildDirectory.dir("codeql/android-aar/$taskName")
        val commonSources =
            files(
                codeqlKotlinCommonSourceSetNames.map { sourceSetName ->
                    fileTree(sourceRoot.dir("src/$sourceSetName/kotlin")) { include("**/*.kt") }
                },
            )
        val sources =
            files(
                sourceSetNames.map { sourceSetName ->
                    fileTree(sourceRoot.dir("src/$sourceSetName/kotlin")) { include("**/*.kt") }
                },
            )

        inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(commonSources).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(codeqlSourceClasspath).withNormalizer(ClasspathNormalizer::class.java)
        inputs.files(codeqlAndroidAar).withNormalizer(ClasspathNormalizer::class.java)
        outputs.dir(outDir)
        outputs.dir(aarExtractDir)

        onlyIf("selected CodeQL Kotlin source sets contain sources") {
            val commonSourceFiles = commonSources.files
            val sourceFiles = sources.files
            if (commonSourceFiles.isEmpty() || sourceFiles.isEmpty()) {
                logger.lifecycle(
                    "Skipping $taskName: no Kotlin sources found for common source sets " +
                        "${codeqlKotlinCommonSourceSetNames.joinToString(",")} or target source sets " +
                        sourceSetNames.joinToString(","),
                )
                false
            } else {
                true
            }
        }

        doFirst {
            outDir.get().asFile.mkdirs()
            val extractedJars =
                codeqlAndroidAar.resolve().mapNotNull { aar ->
                    val extractTarget = aarExtractDir.get().asFile.resolve(aar.nameWithoutExtension)
                    extractTarget.mkdirs()
                    copy {
                        from(zipTree(aar))
                        include("classes.jar")
                        into(extractTarget)
                    }
                    extractTarget.resolve("classes.jar").takeIf { it.exists() }
                }
            val androidClasspath = if (includeAndroidClasspath) listOf(androidJar()) else emptyList()
            val fullClasspath =
                (codeqlSourceClasspath.resolve() + extractedJars + androidClasspath)
                    .joinToString(File.pathSeparator) { it.absolutePath }
            val commonSourceFiles = commonSources.files.toMutableList()
            val sourceFiles = sources.files.toMutableList()
            args =
                listOf(
                    "-d",
                    outDir.get().asFile.absolutePath,
                    "-classpath",
                    fullClasspath,
                    "-jvm-target",
                    jvmToolchainVersion,
                    "-no-stdlib",
                    "-no-reflect",
                    "-language-version",
                    codeqlLanguageVersion,
                    "-api-version",
                    codeqlApiVersion,
                    "-Xmulti-platform",
                    "-Xcommon-sources=${commonSourceFiles.joinToString(",") { it.absolutePath }}",
                    "-Xexpect-actual-classes",
                ) + commonOptIns.flatMap { listOf("-opt-in", it) } + sourceFiles.map { it.absolutePath }
        }
    }
}

registerCodeqlCompileTask(
    "codeqlCompileJvm",
    propertyValue("project.codeql.kotlinSourceSets", "commonMain,jvmMain").toSourceSetList(),
    includeAndroidClasspath = false,
)

registerCodeqlCompileTask(
    "codeqlCompileAndroid",
    propertyValue("project.codeql.androidKotlinSourceSets", "commonMain,androidMain").toSourceSetList(),
    includeAndroidClasspath = true,
)
