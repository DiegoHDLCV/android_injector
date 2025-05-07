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
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Aquí se pueden incluir repositorios locales (flatDir) si es necesario:
        flatDir { dirs("shared-libs", "app/libs", "manufacturer/libs") }
    }
}

rootProject.name = "android_injector"
include(":app")
include(":utils")
include(":persistence")
include(":manufacturer")
include(":config")
