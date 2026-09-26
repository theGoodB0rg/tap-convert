import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use(::load)
}
fun admob(key: String, testDefault: String): String =
    (localProps.getProperty(key) as String?)?.takeIf { it.isNotBlank() } ?: testDefault

android {
    namespace = "com.tapconvert.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tapconvert.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        manifestPlaceholders["admobAppId"] =
            admob("admob.appId", "ca-app-pub-3940256099942544~3347511713")
        buildConfigField(
            "String", "ADMOB_BANNER_ID",
            "\"${admob("admob.banner", "ca-app-pub-3940256099942544/6300978111")}\""
        )
        buildConfigField(
            "String", "ADMOB_INTERSTITIAL_ID",
            "\"${admob("admob.interstitial", "ca-app-pub-3940256099942544/1033173712")}\""
        )
        buildConfigField(
            "String", "ADMOB_REWARDED_ID",
            "\"${admob("admob.rewarded", "ca-app-pub-3940256099942544/5224354917")}\""
        )
    }

    signingConfigs {
        create("release") {
            val storeFileProp = localProps["signing.storeFile"] as String?
            val storePasswordProp = localProps["signing.storePassword"] as String?
            val keyAliasProp = localProps["signing.keyAlias"] as String?
            val keyPasswordProp = localProps["signing.keyPassword"] as String?
            if (storeFileProp != null && storePasswordProp != null &&
                keyAliasProp != null && keyPasswordProp != null
            ) {
                storeFile = file(storeFileProp)
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.jvmArgs("-Xmx3072m", "-XX:+EnableDynamicAgentLoading")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:analytics"))
    implementation(project(":core:database"))
    implementation(project(":core:ads"))
    implementation(project(":core:billing"))
    implementation(project(":feature:image-engine"))
    implementation(project(":feature:pdf-engine"))
    implementation(project(":feature:media-engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Ads + consent
    implementation(libs.play.services.ads)
    implementation(libs.ump.user.messaging)

    // Media3
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

tasks.register<Copy>("copyDebugApkToDist") {
    description = "Copies generated debug APK to the root dist/ folder"
    group = "distribution"
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "TapConvert-debug.apk" }
}

tasks.register<Copy>("copyReleaseApkToDist") {
    description = "Copies generated release APK to the root dist/ folder"
    group = "distribution"
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk")
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "TapConvert-release.apk" }
}

tasks.register<Copy>("copyReleaseAabToDist") {
    description = "Copies generated release AAB bundle to the root dist/ folder"
    group = "distribution"
    from(layout.buildDirectory.dir("outputs/bundle/release"))
    include("*.aab")
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "TapConvert-release.aab" }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("copyDebugApkToDist")
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("copyReleaseApkToDist")
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    finalizedBy("copyReleaseAabToDist")
}
