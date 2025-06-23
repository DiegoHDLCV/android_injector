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
        flatDir {
            dirs("project_local_aars") // Apunta a la carpeta que creaste
            dirs("shared-libs", "app/libs", "manufacturer/libs")

            // dirs("project_local_aars", "shared-libs", "app/libs", ...) // Si tienes otras
        }
    }
}

rootProject.name = "android_injector"
include(":app")
include(":utils")
include(":persistence")
include(":manufacturer")
include(":config")
include(":communication")
include(":format")

