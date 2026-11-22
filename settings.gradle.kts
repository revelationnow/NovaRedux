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

rootProject.name = "NovaRedux"
include(":app")
// NovaRedux Connect, the companion app that goes online for NovaRedux.
include(":connect")
include(":baselineprofile")
