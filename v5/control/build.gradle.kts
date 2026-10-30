plugins {
    id("faro.jvm-library")
}

dependencies {
    api(project(":kernel:contracts"))
    implementation(libs.androidx.datastore.preferences.core)

    testImplementation(project(":kernel:runtime"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
