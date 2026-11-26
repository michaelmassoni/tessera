pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "tessera"

include(":core:design")
include(":core:settings")
include(":core:dav")
include(":core:cast")

include(":apps:calculator")
include(":apps:clock")
include(":apps:files")
include(":apps:notes")
include(":apps:contacts")
include(":apps:gallery")
include(":apps:calendar")
include(":apps:music")
include(":apps:messages")
include(":apps:phone")
include(":apps:weather")
include(":apps:sports")
include(":apps:tasks")
include(":apps:recorder")
include(":apps:feeds")
include(":apps:podcasts")
