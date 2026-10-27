import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val localSecrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun configuredValue(name: String): String {
    val legacyKey = if (name == "SUPABASE_ANON_KEY") "SUPABASE_PUBLISHABLE_KEY" else name
    return providers.gradleProperty(name).orNull
        ?: localProperties.getProperty(name)?.takeIf(String::isNotBlank)
        ?: localProperties.getProperty(legacyKey)?.takeIf(String::isNotBlank)
        ?: localSecrets.getProperty(name)?.takeIf(String::isNotBlank)
        ?: localSecrets.getProperty(legacyKey).orEmpty()
}

android {
    namespace = "zm.co.dhwinride.passenger"
    compileSdk = 37

    defaultConfig {
        applicationId = "zm.co.dhwinride.passenger"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${configuredValue("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${configuredValue("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "MAPBOX_PUBLIC_TOKEN", "\"${configuredValue("MAPBOX_PUBLIC_TOKEN")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
}
