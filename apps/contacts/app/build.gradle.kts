import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// version.txt: line 1 is the version code, line 2 the version name.
// Bundle: the app lives in apps/contacts/app, so version.txt is one level up.
val versionLines = file("../version.txt").readLines().map { it.trim() }

// Bundle: package prefix shared by the three apps (gradle.properties at the monorepo root).
val appIdPrefix = providers.gradleProperty("bundle.appIdPrefix").get()
val appId = "$appIdPrefix.contacts"

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
    namespace = "app.people.contacts"
    // Material 3 Expressive (1.5) needs compileSdk 37 and AGP 9.1+.
    compileSdk = 37

    defaultConfig {
        // Bundle: <prefix>.contacts (upstream: app.people.contacts).
        applicationId = appId
        minSdk = 31 // Wallpaper colours
        targetSdk = 36
        versionCode = versionLines[0].toInt()
        versionName = versionLines[1]
        ndk { abiFilters += "arm64-v8a" }
        // The app's own account type (it holds the birthday calendar): the app id, as in the old app.
        resValue("string", "account_type", appId)
        manifestPlaceholders["bundleVersion"] = providers.gradleProperty("bundle.version").get()
    }

    // Unit tests run on the computer, not a phone. Android calls the tested code happens to make
    // (number formatting, say) return empty defaults instead of failing.
    testOptions { unitTests.isReturnDefaultValues = true }

    signingConfigs {
        if (bundleSigning != null) {
            create("bundle") {
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("bundle") ?: signingConfigs.getByName("debug")
        }
        // Test builds: quick to build and inspectable, with their own app id so they install
        // beside the release.
        create("dev") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "account_type", "$appId.dev")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}

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
    implementation(project(":core:sync"))
    implementation(project(":core:hub"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    // QR codes for sharing Your info.
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
}

// APKs are named contacts-dev.apk / contacts-release.apk, as in the app this one replaces.
base.archivesName.set("contacts")

// A failing unit test prints what was expected and what came out, so CI can show it.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}
