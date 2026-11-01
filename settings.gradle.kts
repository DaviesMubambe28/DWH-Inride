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
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            content { includeGroupByRegex("com\\.mapbox(\\..*)?") }
            // Some Mapbox entitlements require Downloads:Read. This build-only
            // token must never be read by either app's BuildConfig.
            val downloadToken = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN")
                .orElse(providers.environmentVariable("MAPBOX_DOWNLOADS_TOKEN")).orNull
            if (!downloadToken.isNullOrBlank()) {
                credentials {
                    username = "mapbox"
                    password = downloadToken
                }
            }
        }
    }
}

rootProject.name = "DHW-inRide"
include(":core-ui", ":passenger-app", ":rider-app")
