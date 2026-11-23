import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Bundle: package prefix shared by the three apps (gradle.properties at the monorepo root).
val appIdPrefix = providers.gradleProperty("bundle.appIdPrefix").get()

// Bundle: one signing key for every app of the bundle (signing.properties at the monorepo
// root, or BUNDLE_* environment variables in CI). Without it, builds use the debug key.
val bundleSigning: Map<String, String?>? = rootProject.file("signing.properties").let { f ->
    if (f.canRead()) {
        val p = Properties().apply { f.inputStream().use { load(it) } }
        mapOf(
            "storeFile" to rootProject.file(p.getProperty("storeFile")).path,
            "storePassword" to p.getProperty("storePassword"),
            "keyAlias" to p.getProperty("keyAlias"),
            "keyPassword" to p.getProperty("keyPassword"),
        )
    } else System.getenv("BUNDLE_KEYSTORE_FILE")?.let {
        mapOf(
            "storeFile" to it,
            "storePassword" to System.getenv("BUNDLE_KEYSTORE_PASSWORD"),
            "keyAlias" to System.getenv("BUNDLE_KEY_ALIAS"),
            "keyPassword" to System.getenv("BUNDLE_KEY_PASSWORD"),
        )
    }
}

android {
    namespace = "app.dialtone.phone"
    // Material 3 Expressive (1.5 alpha) requires compileSdk 37 and AGP 9.1+.
    compileSdk = 37

    defaultConfig {
        // Bundle: <prefix>.phone (upstream: app.dialtone.phone).
        applicationId = "$appIdPrefix.phone"
        minSdk = 29 // RoleManager.ROLE_DIALER
        targetSdk = 36
        // CI passes the run number / tag so every build installs over the previous one.
        versionCode = System.getenv("DIALTONE_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("DIALTONE_VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        if (bundleSigning != null) {
            create("release") {
                storeFile = file(bundleSigning.getValue("storeFile")!!)
                storePassword = bundleSigning["storePassword"]
                keyAlias = bundleSigning["keyAlias"]
                keyPassword = bundleSigning["keyPassword"]
                // Bundle: every signature scheme, so any installer (old file managers included)
                // accepts the APK.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink, optimize and obfuscate; drop unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Unit tests run on the plain JVM: Android framework calls return defaults instead of
    // throwing, so code that touches them (e.g. date helpers) can still be loaded.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES",
                "META-INF/{AL2.0,LGPL2.1}",
            )
        }
    }
}

// AGP 9 compiles Kotlin itself ("built-in Kotlin"); JVM target follows compileOptions.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    // Bundle: shared theme, avatars and phone-number helpers.
    implementation(project(":core:design"))
    implementation(project(":core:telephony"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Visual voicemail engine
    implementation(libs.apache.mime4j.core)
    implementation(libs.apache.mime4j.dom)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
