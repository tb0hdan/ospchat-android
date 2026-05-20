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
        // Local development: an unpublished `ospchat-shared` SNAPSHOT or
        // staging version produced by `make publish-local` over in
        // ../ospchat-shared is picked up here. Listed first so it's
        // available for dev cycles; published releases on GitHub Packages
        // still win because Gradle picks the highest matching version
        // across all configured repos.
        mavenLocal()
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
