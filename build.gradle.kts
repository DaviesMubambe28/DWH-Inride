plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

subprojects {
    plugins.withId("com.android.application") {
        val android = extensions.getByType<com.android.build.api.dsl.ApplicationExtension>()
        val signingNames = listOf(
            "DHWINRIDE_RELEASE_KEYSTORE_PATH", "DHWINRIDE_RELEASE_STORE_PASSWORD",
            "DHWINRIDE_RELEASE_KEY_ALIAS", "DHWINRIDE_RELEASE_KEY_PASSWORD",
        )
        val signingProperties = java.util.Properties().apply {
            rootProject.file("secrets.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
        }
        val signingValues = signingNames.associateWith {
            providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank)
                ?: signingProperties.getProperty(it).orEmpty()
        }
        val signingReady = signingValues.values.all(String::isNotBlank)
        if (signingReady) {
            val pilotSigning = android.signingConfigs.create("pilotRelease") {
                storeFile = rootProject.file(signingValues.getValue("DHWINRIDE_RELEASE_KEYSTORE_PATH"))
                storePassword = signingValues.getValue("DHWINRIDE_RELEASE_STORE_PASSWORD")
                keyAlias = signingValues.getValue("DHWINRIDE_RELEASE_KEY_ALIAS")
                keyPassword = signingValues.getValue("DHWINRIDE_RELEASE_KEY_PASSWORD")
            }
            android.buildTypes.named("release") { signingConfig = pilotSigning }
        }
        android.buildTypes.named("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(android.getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        val validatePilotRelease = tasks.register("validatePilotRelease") {
            group = "verification"
            description = "Refuse unsigned pilot artifacts or missing Firebase app configuration."
            doLast {
                val missing = signingNames.filter { signingValues.getValue(it).isBlank() }
                check(missing.isEmpty()) { "Pilot release signing is incomplete. Set: ${missing.joinToString()}. Never put values in source control." }
                check(rootProject.file(signingValues.getValue("DHWINRIDE_RELEASE_KEYSTORE_PATH")).isFile) {
                    "The configured pilot keystore file is unavailable."
                }
                check(file("google-services.json").isFile || file("src/release/google-services.json").isFile) {
                    "Provide this app's real Firebase google-services.json in an ignored configuration path."
                }
                val publicProperties = java.util.Properties().apply {
                    listOf("secrets.properties", "local.properties").forEach { path ->
                        rootProject.file(path).takeIf { it.isFile }?.inputStream()?.use(::load)
                    }
                }
                fun publicValue(name: String, legacy: String = name): String =
                    providers.gradleProperty(name).orNull
                        ?: providers.environmentVariable(name).orNull
                        ?: providers.environmentVariable(legacy).orNull
                        ?: publicProperties.getProperty(name)
                        ?: publicProperties.getProperty(legacy).orEmpty()
                val endpointValid = runCatching {
                    val uri = java.net.URI(publicValue("SUPABASE_URL"))
                    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
                }.getOrDefault(false)
                check(endpointValid) { "A real HTTPS Supabase URL is required for the pilot release." }
                val publicKey = publicValue("SUPABASE_ANON_KEY", "SUPABASE_PUBLISHABLE_KEY")
                val anonymousJwt = runCatching {
                    val payload = String(java.util.Base64.getUrlDecoder().decode(publicKey.split('.')[1]))
                    (groovy.json.JsonSlurper().parseText(payload) as? Map<*, *>)?.get("role") == "anon"
                }.getOrDefault(false)
                check(publicKey.startsWith("sb_publishable_") || anonymousJwt) {
                    "Use a Supabase publishable/anon key. Server and service-role keys must never enter an APK."
                }
                check(publicValue("MAPBOX_PUBLIC_TOKEN").startsWith("pk.")) {
                    "A Mapbox public token is required; secret tokens must never enter an APK."
                }
            }
        }
        tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validatePilotRelease) }
    }
}
