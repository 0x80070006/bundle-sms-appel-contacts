plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Core modules can be consumed by an included build (apps/messages, later) as bundle.core:<name>.
subprojects {
    if (path.startsWith(":core:")) group = "bundle.core"
}
