plugins {
    id("faro.jvm-library")
}

dependencies {
    api(project(":kernel:contracts"))

    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.bundled)

    testImplementation(project(":kernel:runtime"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
