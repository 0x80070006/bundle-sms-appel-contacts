// Root build of the bundle: shared core modules plus the Contacts and Phone apps.
// Messages (apps/messages) is a separate Gradle build (its own wrapper, catalog and
// dependency verification, kept close to GrapheneOS Messaging); see README.md.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Bundle"

include(":core:appearance", ":core:design", ":core:hub", ":core:sync", ":core:telephony")

include(":contacts")
project(":contacts").projectDir = file("apps/contacts/app")

include(":phone")
project(":phone").projectDir = file("apps/phone/app")
