import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint)
}

buildscript {
    dependencies {
        classpath(libs.hilt.gradle.plugin)
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.ksp.gradle.plugin)
    }
}

val ktlintCliVersion: String = the<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("ktlint")
    .get()
    .requiredVersion

configure<KtlintExtension> {
    version.set(ktlintCliVersion)

    filter {
        exclude("**/build/**")
    }
}

subprojects {
    // Bundle: -Pbundle.javaToolchain=21 builds every module with that JDK (for example the one
    // bundled in Android Studio) when no JDK 17 is installed. Without it, upstream's 17 is kept.
    providers.gradleProperty("bundle.javaToolchain").orNull?.let { version ->
        afterEvaluate {
            extensions.findByType<JavaPluginExtension>()?.toolchain?.languageVersion?.set(
                JavaLanguageVersion.of(version.toInt()),
            )
        }
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<KtlintExtension> {
        version.set(ktlintCliVersion)

        filter {
            exclude("**/build/**")
        }
    }
}
