plugins {
    alias(libs.plugins.android.library)
}

// The engine of the Nextcloud contact sync: end-to-end encryption, a small WebDAV client, the
// three-way merge and the sync run itself. It knows nothing about Android's contact provider
// (the app plugs that in through LocalStore), so all of it is tested on the JVM.
android {
    namespace = "bundle.core.sync"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.org.json)
}
