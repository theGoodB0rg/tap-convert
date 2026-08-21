plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tapconvert.core.testing"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    api(project(":core:analytics"))
    api(libs.junit)
    api(libs.truth)
    api(libs.mockk)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.androidx.test.core)
    api(libs.robolectric)
}
