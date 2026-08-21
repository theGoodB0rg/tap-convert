plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
}

allprojects {
    val modulePath = if (path == ":") "root" else path.trimStart(':').replace(':', '/')
    layout.buildDirectory.set(file("C:/Users/HP/.gradle/builds/TapConvert/$modulePath"))

    tasks.withType<Test>().configureEach {
        maxHeapSize = "2048m"
    }
}
