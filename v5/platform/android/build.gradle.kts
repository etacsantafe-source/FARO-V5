plugins {
    alias(libs.plugins.android.library)
}

android {
    enableKotlin = false
    namespace = "ar.com.faro.v5.platform.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }
}

dependencies {
    implementation(project(":kernel:contracts"))
    implementation(project(":control"))
    implementation(project(":evidence"))
    implementation(project(":delivery"))
    implementation(project(":maintenance"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appsearch.core)
    implementation(libs.androidx.appsearch.local.storage)
}
