import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.salizzai.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.salizzai.app"
        minSdk = 26  // Required for PBKDF2WithHmacSHA256
        targetSdk = 35
        versionCode = 4
        versionName = "2.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Z.ai default endpoint (overridable in Settings)
        buildConfigField("String", "ZAI_BASE_URL", "\"https://api.z.ai/api/v1\"")
    }

    // ─── Release signing config ────────────────────────────────────
    // Loads keystore from environment variables (set by GitHub Actions
    // from repository secrets). Falls back to debug key for local dev.
    //
    // Required env vars:
    //   SIGNING_KEYSTORE_FILE     — path to .jks file (decoded from secret)
    //   SIGNING_KEYSTORE_PASSWORD — keystore password
    //   SIGNING_KEY_ALIAS         — alias name
    //   SIGNING_KEY_PASSWORD      — key password
    //
    // Local dev (no env vars set) → uses debug key automatically.
    // NOTE: do not write `java.io.File` fully-qualified in this script and do
    // not name these vals like the SigningConfig properties: in Gradle Kotlin
    // DSL `java` resolves to the Project's JavaPluginExtension (so
    // `java.io.File` fails with "Unresolved reference: io"), and
    // `keyAlias = keyAlias` inside create("release") self-assigns the outer
    // val ("Val cannot be reassigned"). Both broke script compilation, i.e.
    // EVERY Gradle task failed before any code was compiled.
    val keystorePath = System.getenv("SIGNING_KEYSTORE_FILE")
    val envStorePass = System.getenv("SIGNING_KEYSTORE_PASSWORD")
    val envKeyAlias  = System.getenv("SIGNING_KEY_ALIAS")
    val envKeyPass   = System.getenv("SIGNING_KEY_PASSWORD")
    val hasReleaseKeystore = keystorePath != null && File(keystorePath).exists()

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = File(keystorePath!!)
                storePassword = envStorePass
                keyAlias = envKeyAlias
                keyPassword = envKeyPass
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true   // APK Signature Scheme v3 (key rotation)
            }
        }
        println("✅ Release signing config loaded from $keystoreFile")
    } else {
        println("⚠️  No release keystore found — falling back to debug signing")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // ⚠️  CRITICAL FIX: Resource shrinking was REMOVED.
            // It was incorrectly stripping `splash_background.xml` because
            // it's only referenced from the theme's windowBackground (not
            // from Java code), causing:
            //   Resources$NotFoundException: Drawable .../splash_background
            //   with resource ID #0x7f060033
            // at app launch on real devices (Xiaomi MIUI confirmed).
            isShrinkResources = false
            isCrunchPngs = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use real release keystore if available, otherwise debug key
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isDebuggable = true
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

    // Large PNG logo files in drawable-nodpi/ should not be re-crunched
    // (causes AAPT2 errors when the source image is already optimized externally).
    androidResources {
        // No-op here for clarity, but reserves the block if we need to set crunchMode later.
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        )
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Jetpack Compose (BOM-managed)
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore (preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Security (encrypted prefs)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Markdown rendering
    // NOTE: `syntax-highlight` was removed — it was unused AND pulled in
    // `org.jetbrains:annotations-java5` which clashes with `annotations:23.0.0`
    // (duplicate class org.jetbrains.annotations.NotNull) and broke the build.
    // `ext-strikethrough` was added because MarkdownText uses StrikethroughPlugin.
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:image-coil:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")

    // Credential Manager (Google sign-in via modern API)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Debugging
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
