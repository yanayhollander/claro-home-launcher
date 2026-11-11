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
        buildConfigField(
            "String",
            "MIXPANEL_TOKEN",
            quotedBuildConfig(environmentValue("CLARO_MIXPANEL_TOKEN")),
        )
        buildConfigField(
            "String",
            "FIREBASE_APPLICATION_ID",
            quotedBuildConfig(environmentValue("CLARO_FIREBASE_APPLICATION_ID")),
        )
        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            quotedBuildConfig(environmentValue("CLARO_FIREBASE_API_KEY")),
        )
        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            quotedBuildConfig(environmentValue("CLARO_FIREBASE_PROJECT_ID")),
        )
        buildConfigField(
            "String",
            "FIREBASE_SENDER_ID",
            quotedBuildConfig(environmentValue("CLARO_FIREBASE_SENDER_ID")),
        )
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
    implementation(libs.revenuecat)
    ksp(libs.hilt.compiler)

    testImplementation(project(":core:testing"))
}
