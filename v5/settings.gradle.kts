import org.gradle.api.initialization.resolve.RepositoriesMode

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

rootProject.name = "faro-v5"

includeBuild("spec-build-logic")
includeBuild("build-logic")
include(":app")
include(":kernel:contracts")
include(":kernel:runtime")
include(":kernel:apply")
include(":control")
include(":workspace")
include(":evidence")
include(":delivery")
include(":maintenance")
include(":owner:profile")
include(":owner:finance")
include(":store:reality")
include(":store:auxiliary")
include(":store:generation")
include(":platform:android")
include(":platform:ai")
include(":platform:web")
include(":surface")
