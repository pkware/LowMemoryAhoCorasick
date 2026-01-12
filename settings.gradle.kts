dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.develocity") version "3.19.2"
}

val isCiServer = System.getenv().containsKey("CI")

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree.set("yes")
        publishing.onlyIf { _ -> false }
        if (isCiServer) {
            tag("CI")
        }
    }
}
