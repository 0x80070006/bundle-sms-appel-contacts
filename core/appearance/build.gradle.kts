plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// The bundle's shared look: theme mode, colours (wallpaper, palettes, custom), pure black,
// contact-picture style and language, synced between Contacts, Phone and Messages.
// Messages compiles these same sources into its own build (see apps/messages/app/build.gradle.kts),
// so this module only uses Compose, Material 3 and the Android framework.
android {
    namespace = "bundle.core.appearance"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
