import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.firebase.crashlytics)
}

val localTelemetry = Properties().apply {
    val file = rootProject.file("telemetry.local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun telemetryValue(name: String, defaultEnvironment: String = "staging"): String {
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
    namespace = "com.clarohome.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.hollander.claro"
        minSdk = 29
        targetSdk = 37
        val requestedVersionCode = providers.gradleProperty("claro.versionCode").orNull
        val releaseVersionCode = requestedVersionCode?.toIntOrNull()
            ?: if (requestedVersionCode == null) 1 else error("claro.versionCode must be an integer")
        require(releaseVersionCode in 1..2_100_000_000) { "claro.versionCode must be between 1 and 2100000000" }
        versionCode = releaseVersionCode
        versionName = providers.gradleProperty("claro.versionName").orNull?.also {
            require(it.isNotBlank()) { "claro.versionName must not be blank" }
        } ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        configureEach {
            val defaultEnvironment = if (name == "release") "production" else "staging"
            mapOf(
                "google_app_id" to "CLARO_FIREBASE_APPLICATION_ID",
                "google_api_key" to "CLARO_FIREBASE_API_KEY",
                "project_id" to "CLARO_FIREBASE_PROJECT_ID",
                "gcm_defaultSenderId" to "CLARO_FIREBASE_SENDER_ID",
            ).forEach { (resource, variable) ->
                telemetryValue(variable, defaultEnvironment).takeIf(String::isNotBlank)?.let {
                    resValue("string", resource, it)
                }
            }
        }
        debug {
            if (providers.gradleProperty("claro.localInstrumentation").orNull == "true") {
                applicationIdSuffix = ".localtest"
            }
        }
        release {
            optimization {
                enable = true
            }
            ndk {
                debugSymbolLevel = "FULL"
            }
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
    }

    buildFeatures {
        compose = true
        resValues = true
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency")
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

val verifyTelemetryConfiguration = tasks.register("verifyTelemetryConfiguration") {
    val names = listOf(
        "CLARO_FIREBASE_APPLICATION_ID", "CLARO_FIREBASE_API_KEY",
        "CLARO_FIREBASE_PROJECT_ID", "CLARO_FIREBASE_SENDER_ID", "CLARO_MIXPANEL_TOKEN",
        "CLARO_TELEMETRY_ENVIRONMENT",
    ) + if (providers.environmentVariable("CLARO_META_ENABLED").orNull == "true") {
        listOf("CLARO_META_APP_ID", "CLARO_META_CLIENT_TOKEN")
    } else {
        emptyList()
    }
    val values = names.associateWith { telemetryValue(it) }
    val mixpanelEndpoint = telemetryValue("CLARO_MIXPANEL_SERVER_URL").ifBlank { "https://api.mixpanel.com" }
    doLast {
        val missing = values.filterValues(String::isBlank).keys
        check(missing.isEmpty()) { "Missing telemetry configuration: ${missing.joinToString()}" }
        check(values["CLARO_TELEMETRY_ENVIRONMENT"] in setOf("development", "staging", "production")) {
            "CLARO_TELEMETRY_ENVIRONMENT must be development, staging or production"
        }
        check(mixpanelEndpoint in setOf("https://api.mixpanel.com", "https://api-eu.mixpanel.com", "https://api-in.mixpanel.com")) {
            "CLARO_MIXPANEL_SERVER_URL must match a supported Mixpanel region"
        }
        values["CLARO_META_APP_ID"]?.let { appId ->
            check(appId.all { it in '0'..'9' }) { "CLARO_META_APP_ID must be numeric" }
        }
    }
}
if (providers.gradleProperty("claro.requireTelemetry").orNull == "true") {
    tasks.named("preBuild").configure { dependsOn(verifyTelemetryConfiguration) }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:design"))
    implementation(project(":core:platform"))
    implementation(project(":presentation:touch"))
    implementation(project(":presentation:classic"))
    implementation(project(":feature:onboarding-settings"))
    implementation(project(":feature:launcher-home"))
    implementation(project(":feature:communication"))
    implementation(project(":feature:safety-media-apps"))
    implementation(project(":feature:billing-observability"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    ksp(libs.hilt.compiler)

    testImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
