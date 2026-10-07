import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// ---------------------------------------------------------------------------
// Versioning
// Release builds receive their version from the git tag:
//   ./gradlew assembleRelease -PappVersionName=1.2.3 -PappVersionCode=1002003
// (see .github/workflows/release.yml). Local builds fall back to 0.1.0.
// ---------------------------------------------------------------------------
fun versionCodeFrom(name: String): Int {
    val core = name.substringBefore('-').split('.')
    val major = core.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = core.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = core.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 1_000_000 + minor * 1_000 + patch
}

val appVersionName: String = (findProperty("appVersionName") as String?)
    ?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() } ?: "0.1.0"
val appVersionCode: Int = (findProperty("appVersionCode") as String?)?.trim()?.toIntOrNull()
    ?: versionCodeFrom(appVersionName)

// ---------------------------------------------------------------------------
// Signing
// The release keystore is read from keystore.properties (git-ignored, local use)
// or from environment variables (CI: KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD).
// When neither is present the release build falls back to the debug key so the
// project still builds; such an APK must never be distributed.
// ---------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile: File? = signingValue("storeFile", "KEYSTORE_FILE")?.let { path ->
    val f = File(path)
    if (f.isAbsolute) f else rootProject.file(path)
}
val hasReleaseKeystore: Boolean = releaseStoreFile?.exists() == true

if (!hasReleaseKeystore) {
    logger.warn("[LRRViewer] No release keystore configured - release builds will be signed with the DEBUG key.")
}

android {
    namespace = "com.sus7898.lrrviewer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sus7898.lrrviewer"
        minSdk = 28
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // GitHub repository that hosts the releases used by the in-app updater.
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"${findProperty("updateRepoOwner") ?: "SUS7898"}\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"${findProperty("updateRepoName") ?: "lanraragi-app"}\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
                // minSdk 28 => v1 (JAR) signing is unnecessary; v2/v3 are what Android actually verifies.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }

    // Do not embed the Google-Play-only encrypted dependency metadata block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        sarifReport = true
        htmlReport = true
        xmlReport = false
        // Also print findings to the console so they are visible in CI logs without downloading artifacts.
        textReport = true
        textOutput = File("stdout")
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Room schema JSON is exported here so that future AutoMigrations can reference older versions.
// The files are generated by a build; commit app/schemas/** from a local build after changing entities.
room {
    schemaDirectory("$projectDir/schemas")
}

// Dependency locking lets the security workflow write app/gradle.lockfile so that Trivy can
// scan the exact dependency graph that ships in the APK (see .github/workflows/security.yml).
// Only the release classpaths are resolved on purpose: locking every configuration would also
// list Gradle/AGP build tooling (protobuf, netty, ...) that never ends up on the device and
// produces false-positive vulnerability findings.
dependencyLocking {
    lockAllConfigurations()
}

tasks.register("lockReleaseDependencies") {
    description = "Resolves the release classpaths; run with --write-locks to produce gradle.lockfile for vulnerability scanning."
    notCompatibleWithConfigurationCache("Resolves configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) { "$path must be run with --write-locks" }
    }
    doLast {
        val shipped = setOf("releaseRuntimeClasspath", "releaseCompileClasspath")
        configurations.filter { it.name in shipped }.forEach { cfg ->
            cfg.resolve()
            logger.lifecycle("lockReleaseDependencies: resolved ${cfg.name} (${cfg.resolvedConfiguration.resolvedArtifacts.size} artifacts)")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.telephoto.zoomable.image.coil3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.reorderable)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
