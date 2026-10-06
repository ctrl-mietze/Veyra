import java.util.Properties
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing material. CI passes it through environment variables; a local build
// can keep it in keystore/keystore.properties instead (that file is gitignored).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingProperty(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

// The base version, and the only place either number is written by hand. A release tag is
// `v$appVersionBase` and both workflows read this literal out of this file, so it has to stay a
// plain string here rather than being assembled from somewhere else.
val appVersionBase = "1.0.0"

// An offset under the version code, not a version of its own: the code is this plus the clock, and the
// only rule is that it may be raised and never lowered - lowering it would put a new build below an
// installed one and Android would refuse the install.
val appVersionCodeBase = 13

// The clock the version code is derived from, read through a value source so the reading counts as
// a build configuration input. Reading the clock directly is not enough: configuration cache
// entries outlive builds and store the value, so a local rebuild that changed only source files
// was handed the previous build's clock and reused its version code — two different APKs under one
// identity. Being a configuration input means a changed reading invalidates the entry, so every
// build reconfigures; that reconfiguration is the price of a version code that is unique per build.
abstract class BuildClockValueSource : ValueSource<Long, ValueSourceParameters.None> {
    override fun obtain(): Long = System.currentTimeMillis()
}

// A version code that only ever grows, on every machine that builds this. A per-CI run counter
// would not be comparable with a local build, and Android refuses to install a lower version code
// over a higher one, which would break installing a local build over a CI build (or the reverse),
// so the number is seconds since 2026-01-01 UTC: unique per build everywhere and always larger
// than the build before it.
val appVersionCode =
    appVersionCodeBase +
        (providers.of(BuildClockValueSource::class) {}.get() / 1000L - 1_767_225_600L).toInt()

// Which build this is: the CI run that produced it, or the local commit it was built from. Two
// builds of the same version are otherwise indistinguishable on the phone, which is what this is
// for: Settings shows it and every run log starts with it.
val buildCommit: String? = System.getenv("GITHUB_SHA")
    ?.trim()
    ?.take(7)
    ?.takeIf { it.isNotEmpty() }
    ?: runCatching {
        providers.exec {
            commandLine("git", "rev-parse", "--short=7", "HEAD")
        }.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()
val buildLabel = listOfNotNull(
    System.getenv("GITHUB_RUN_NUMBER")?.takeIf { it.isNotBlank() }?.let { "ci.$it" } ?: "local",
    buildCommit,
).joinToString(".")
val appVersionName = "$appVersionBase+$buildLabel"

android {
    namespace = "ctrl.mietze.veyraroot"
    compileSdk = 37

    defaultConfig {
        // Veyra owns both its Android install identity and source namespace. The one intentional
        // exception is a tiny NativeProbe compatibility shim for the legacy JNI symbol names exported
        // by libs25u_native.so; all app components, actions, resources and new code use Veyra's namespace.
        applicationId = "ctrl.mietze.veyraroot"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // VERSION_BASE is what the update check compares against a release tag; the build label is
        // the same string the version name carries, for showing on its own.
        buildConfigField("String", "VERSION_BASE", "\"$appVersionBase\"")
        buildConfigField("String", "BUILD_LABEL", "\"$buildLabel\"")
        buildConfigField("boolean", "RELEASE_HARDENED", "false")

        ndk {
            abiFilters += "arm64-v8a"
        }

    }

    buildFeatures {
        compose = true
        buildConfig = true
    }


    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        create("release") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            val storeFilePath = signingProperty("KEYSTORE_FILE", "storeFile")
            if (storeFilePath != null) {
                storeFile = rootProject.file(storeFilePath)
                storeType = signingProperty("KEYSTORE_TYPE", "storeType") ?: "PKCS12"
                storePassword = signingProperty("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingProperty("KEY_ALIAS", "keyAlias")
                keyPassword = signingProperty("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Official local releases use this device's persistent Android key. Its certificate is
            // the same signer Veyra has used for the distributed APKs. The key file itself is never
            // checked into Git; CI can still replace this with the configured release signer.
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = false
            buildConfigField("boolean", "RELEASE_HARDENED", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-release.pro",
            )
        }
        debug {
            // Signed with the repository key when it is configured, and with the stock debug key when
            // it is not - so a developer without the keystore still builds a debug APK, and every build
            // this project distributes shares one signature: CI debug, CI release, tagged releases and
            // a local build all update over each other.
            //
            // This was the debug key alone, which is generated per machine and therefore different on
            // every CI runner: a debug APK built there could not be installed over the previous one, or
            // over the signed release APK, without an uninstall - so the debug APK published with a
            // pre-release was an artifact nobody could test with.
            signingConfigs.getByName("release").storeFile?.let { signingConfig = signingConfigs.getByName("release") }
        }
    }

    // CI may provide the dedicated release key. Local hardened builds intentionally fall back to
    // this machine's persistent debug signer, whose certificate is the established Veyra signer.
    // ReleaseGuard still pins the certificate at runtime, so a differently signed repack will exit.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha24")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.materialkolor:material-kolor:4.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Pairing with the device's own wireless debugging speaks the ADB protocol's TLS, which needs a
    // client certificate and the `adb` ALPN; Conscrypt as shipped cannot be asked for that shape, so
    // the TLS client, the certificate builder and the SPAKE2 pairing exchange come from Bouncy Castle.
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    implementation("org.bouncycastle:bctls-jdk18on:1.80")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // Local JVM tests otherwise get Android's stub org.json, whose methods throw "not mocked", so
    // SupportManifest, which deliberately uses org.json, could not be tested on its real semantics.
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
