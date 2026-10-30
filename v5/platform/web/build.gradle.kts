plugins {
    id("faro.jvm-library")
}

dependencies {
    api(project(":evidence"))
    implementation(project(":kernel:contracts"))
    implementation(project(":control"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
