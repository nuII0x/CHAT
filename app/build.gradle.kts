import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val versionFile = rootProject.file("version.properties")

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

enum class ReleaseType {
    MAJOR,
    MINOR,
    SECURITY
}

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) {
    fun versionName(): String = "$major.$minor.$patch"

    fun versionCode(): Int = major * 1_000_000 + minor * 1_000 + patch

    fun bump(type: ReleaseType): SemanticVersion {
        return when (type) {
            ReleaseType.MAJOR -> copy(major = major + 1, minor = 0, patch = 0)
            ReleaseType.MINOR -> copy(minor = minor + 1, patch = 0)
            ReleaseType.SECURITY -> copy(patch = patch + 1)
        }
    }
}

fun loadSemanticVersion(file: java.io.File): SemanticVersion {
    val defaults = SemanticVersion(1, 0, 0)
    if (!file.exists()) return defaults

    val props = Properties()
    file.inputStream().use { props.load(it) }
    val major = props.getProperty("major")?.trim()?.toIntOrNull() ?: defaults.major
    val minor = props.getProperty("minor")?.trim()?.toIntOrNull() ?: defaults.minor
    val patch = props.getProperty("patch")?.trim()?.toIntOrNull() ?: defaults.patch
    return SemanticVersion(major, minor, patch)
}

fun storeSemanticVersion(file: java.io.File, version: SemanticVersion) {
    file.parentFile?.mkdirs()
    file.writeText(
        """
        major=${version.major}
        minor=${version.minor}
        patch=${version.patch}
        """.trimIndent() + "\n"
    )
}

fun parseReleaseType(raw: String): ReleaseType? {
    return when (raw.trim().lowercase()) {
        "major" -> ReleaseType.MAJOR
        "minor" -> ReleaseType.MINOR
        "security" -> ReleaseType.SECURITY
        else -> null
    }
}

val requestedReleaseType = providers.gradleProperty("releaseType")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.let { raw ->
        parseReleaseType(raw) ?: throw GradleException(
            "releaseType invalido: $raw. Use major, minor ou security."
        )
    }

val versionOnDisk = loadSemanticVersion(versionFile)
val versionForThisBuild = requestedReleaseType?.let { versionOnDisk.bump(it) } ?: versionOnDisk

if (requestedReleaseType != null) {
    val releaseTaskRequested = gradle.startParameter.taskNames.any { task ->
        task.contains("release", ignoreCase = true)
    }
    if (!releaseTaskRequested) {
        throw GradleException(
            "releaseType so pode ser usado com tarefas de release, como assembleRelease ou installRelease."
        )
    }
}

android {
    namespace = "com.null0x.chat"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.null0x.chat"
        minSdk = 24
        targetSdk = 34
        versionCode = versionForThisBuild.versionCode()
        versionName = versionForThisBuild.versionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }

        release {
            signingConfig = signingConfigs.getByName("release")

            isMinifyEnabled = false
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

if (requestedReleaseType != null) {
    val persistVersion = tasks.register("persistReleaseVersion") {
        group = "versioning"
        description = "Persiste a nova versao semanticamente versionada no arquivo do projeto."
        doLast {
            storeSemanticVersion(versionFile, versionForThisBuild)
            println("Versao atualizada para ${versionForThisBuild.versionName()}")
        }
    }

    tasks.named("preBuild") {
        dependsOn(persistVersion)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.material3)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("info.guardianproject:tor-android:0.4.8.18")
    implementation("org.pgpainless:pgpainless-core:1.7.6")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
