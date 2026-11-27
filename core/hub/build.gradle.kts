plugins {
    alias(libs.plugins.android.library)
}

// Bundle: the shared hub. What Android's own stores don't keep (preferred SIM per contact,
// call notes, label colours) lives in Contacts and is reached by Phone and Messages through
// a provider that only answers apps signed with the same key.
android {
    namespace = "bundle.core.hub"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":core:telephony"))
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
