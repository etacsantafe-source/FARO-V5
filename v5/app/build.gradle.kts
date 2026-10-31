plugins {
    id("com.android.application")
}

val p5PhysicalApplicationId =
    providers.gradleProperty("faroP5PhysicalApplicationId").orNull
val p5PhysicalVersionCode =
    providers.gradleProperty("faroP5PhysicalVersionCode").orNull?.toInt()
val p5PhysicalVersionName =
    providers.gradleProperty("faroP5PhysicalVersionName").orNull
val p6PhysicalApplicationId =
    providers.gradleProperty("faroP6PhysicalApplicationId").orNull
val s0PhysicalApplicationId =
    providers.gradleProperty("faroS0PhysicalApplicationId").orNull
val s1A5PhysicalApplicationId =
    providers.gradleProperty("faroS1A5PhysicalApplicationId").orNull
val s1A6PhysicalApplicationId =
    providers.gradleProperty("faroS1A6PhysicalApplicationId").orNull

android {
    namespace = "ar.com.faro.v5.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "ar.com.faro.alpha"
        if (listOfNotNull(
                p5PhysicalApplicationId,
                p6PhysicalApplicationId,
                s0PhysicalApplicationId,
                s1A5PhysicalApplicationId,
                s1A6PhysicalApplicationId
            ).size > 1) {
            error("P5, P6, S0, S1.A5 and S1.A6 physical application IDs are mutually exclusive")
        }
        if (p5PhysicalApplicationId != null) {
            applicationId = p5PhysicalApplicationId
        }
        if (p6PhysicalApplicationId != null) {
            applicationId = p6PhysicalApplicationId
        }
        manifestPlaceholders["faroAppLabel"] = "@string/app_name"
        if (s0PhysicalApplicationId != null) {
            applicationId = s0PhysicalApplicationId
            manifestPlaceholders["faroAppLabel"] = "FARO S0 TEST"
        }
        if (s1A5PhysicalApplicationId != null) {
            applicationId = s1A5PhysicalApplicationId
            manifestPlaceholders["faroAppLabel"] = "FARO S1 A5 TEST"
        }
        if (s1A6PhysicalApplicationId != null) {
            applicationId = s1A6PhysicalApplicationId
            manifestPlaceholders["faroAppLabel"] = "FARO S1 A6 TEST"
        }
        minSdk = 29
        targetSdk = 36
        versionCode = 66
        if (p5PhysicalVersionCode != null) {
            versionCode = p5PhysicalVersionCode
        }
        versionName = "5.0.0-dev"
        if (p5PhysicalVersionName != null) {
            versionName = p5PhysicalVersionName
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".v5dev"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        resValues = true
    }
}

dependencies {
    implementation(project(":kernel:contracts"))
    implementation(project(":kernel:runtime"))
    implementation(project(":kernel:apply"))
    implementation(project(":surface"))
    implementation(project(":control"))
    implementation(project(":delivery"))
    implementation(project(":store:reality"))
    implementation(project(":evidence"))
    implementation(project(":store:auxiliary"))
    implementation(project(":platform:android"))
    implementation(project(":platform:web"))
    implementation(project(":maintenance"))
    implementation(project(":store:generation"))
    implementation(project(":workspace"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.okhttp)

    androidTestImplementation(project(":control"))
    androidTestImplementation(libs.androidx.biometric)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Android host JVM tests need Linux JNI, not the Android AAR.
// Test-only substitution: production Android and T4 classpaths stay untouched.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.sqlite:sqlite-bundled:2.7.1"))
            .using(module("androidx.sqlite:sqlite-bundled-jvm:2.7.1"))
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
