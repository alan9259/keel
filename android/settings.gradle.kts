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

rootProject.name = "Keel"

include(":app")
include(":core:domain")
include(":core:copy")
include(":core:designsystem")
include(":core:data")
include(":core:platform")
include(":feature:onboarding")
include(":feature:home")
include(":feature:checkin")
