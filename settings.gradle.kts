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
        // ospchat-shared is consumed from GitHub Packages. Even public
        // packages require auth: set `gprUser` + `gprToken` in
        // ~/.gradle/gradle.properties (token = a PAT with `read:packages`),
        // or export GITHUB_ACTOR + GITHUB_TOKEN. Mirrors the publishing
        // block in ../ospchat-shared/build.gradle.kts.
        maven {
            name = "GitHubPackagesOspChatShared"
            url = uri("https://maven.pkg.github.com/tb0hdan/ospchat-shared")
            credentials {
                username =
                    (settings.providers.gradleProperty("gprUser").orNull)
                        ?: System.getenv("GITHUB_ACTOR")
                password =
                    (settings.providers.gradleProperty("gprToken").orNull)
                        ?: System.getenv("GITHUB_TOKEN")
            }
            content {
                includeGroup("com.ospchat")
            }
        }
    }
}

rootProject.name = "OSPChat"
include(":app")
