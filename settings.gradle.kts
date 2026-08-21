pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "TapConvert"

include(":app")
include(":core:common")
include(":core:model")
include(":core:analytics")
include(":core:testing")
include(":core:database")
include(":core:ads")
include(":feature:image-engine")
include(":feature:pdf-engine")
include(":feature:media-engine")




