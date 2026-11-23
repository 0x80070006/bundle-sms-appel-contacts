plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// The bundle's design system: theme, font, avatars and grouped-list shapes, shared by
// Contacts, Phone and (later) Messages so the three apps look like one.
android {
    namespace = "bundle.core.design"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    // Bundle: the cartoon avatars, a resource folder Messages uses too (avatars/generate.py).
    sourceSets.getByName("main").res.srcDir("avatars/res")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(project(":core:telephony"))
    api(project(":core:appearance"))
}
