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

rootProject.name = "ClaroHomeLauncher"

include(
    ":app",
    ":core:model",
    ":core:domain",
    ":core:data",
    ":core:design",
    ":core:platform",
    ":core:testing",
    ":presentation:touch",
    ":presentation:classic",
    ":feature:onboarding-settings",
    ":feature:launcher-home",
    ":feature:communication",
    ":feature:safety-media-apps",
    ":feature:billing-observability",
)
