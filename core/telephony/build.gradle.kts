plugins {
    alias(libs.plugins.android.library)
}

// Phone-number and text helpers shared by the bundle's apps, so a number matches the same
// contact in the call log, the contacts list and a conversation.
android {
    namespace = "bundle.core.telephony"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
