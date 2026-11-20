import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

fun environmentValue(name: String): String =
    providers.environmentVariable(name).orNull.orEmpty()

fun quotedBuildConfig(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val localTelemetry = Properties().apply {
    listOf("telemetry.local.properties", "local.properties").forEach { name ->
        val file = rootProject.file(name)
        if (file.isFile) file.inputStream().use { load(it) }
    }
}

fun telemetryValue(name: String, defaultEnvironment: String): String {
    val environment = providers.environmentVariable("CLARO_TELEMETRY_ENVIRONMENT").orNull
        ?: providers.gradleProperty("claro.telemetryEnvironment").orNull ?: defaultEnvironment
    if (name == "CLARO_TELEMETRY_ENVIRONMENT") return environment
    val localTest = defaultEnvironment == "staging" &&
        providers.gradleProperty("claro.localInstrumentation").orNull == "true"
    check(!localTest || environment != "production") { "Local-test builds must not use production telemetry" }
    val key = if (localTest && name in setOf("CLARO_FIREBASE_APPLICATION_ID", "CLARO_FIREBASE_API_KEY")) {
        name.replace("CLARO_FIREBASE_", "CLARO_FIREBASE_LOCALTEST_")
    } else name
    return providers.environmentVariable(key).orNull ?: providers.environmentVariable(name).orNull
        ?: localTelemetry.getProperty("$environment.$key").orEmpty()
}

android {
    namespace = "com.clarohome.feature.billing.observability"
    compileSdk = 37
    defaultConfig {
        minSdk = 29
        buildConfigField(
            "String",
            "REVENUECAT_PUBLIC_KEY",
            quotedBuildConfig(environmentValue("CLARO_REVENUECAT_PUBLIC_KEY")),
        )
        buildConfigField("String", "META_APP_ID", quotedBuildConfig(environmentValue("CLARO_META_APP_ID")))
        buildConfigField("String", "META_CLIENT_TOKEN", quotedBuildConfig(environmentValue("CLARO_META_CLIENT_TOKEN")))
        buildConfigField("boolean", "META_ENABLED", (environmentValue("CLARO_META_ENABLED") == "true").toString())
    }
    buildTypes.configureEach {
        val defaultEnvironment = if (name == "release") "production" else "staging"
        mapOf(
            "MIXPANEL_TOKEN" to "CLARO_MIXPANEL_TOKEN",
            "FIREBASE_APPLICATION_ID" to "CLARO_FIREBASE_APPLICATION_ID",
            "FIREBASE_API_KEY" to "CLARO_FIREBASE_API_KEY",
            "FIREBASE_PROJECT_ID" to "CLARO_FIREBASE_PROJECT_ID",
            "FIREBASE_SENDER_ID" to "CLARO_FIREBASE_SENDER_ID",
            "TELEMETRY_ENVIRONMENT" to "CLARO_TELEMETRY_ENVIRONMENT",
        ).forEach { (field, variable) ->
            buildConfigField("String", field, quotedBuildConfig(telemetryValue(variable, defaultEnvironment)))
        }
        buildConfigField("String", "MIXPANEL_SERVER_URL", quotedBuildConfig(
            telemetryValue("CLARO_MIXPANEL_SERVER_URL", defaultEnvironment).ifBlank { "https://api.mixpanel.com" },
        ))
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:design"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.mixpanel)
    implementation(libs.meta.core)
    implementation(libs.revenuecat)
    ksp(libs.hilt.compiler)

    testImplementation(project(":core:testing"))
}
