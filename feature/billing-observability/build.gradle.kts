plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.clarohome.feature.billing.observability"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies { api(project(":core:domain")) }
