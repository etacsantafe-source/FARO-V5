plugins {
    id("faro.jvm-library")
}

dependencies {
    api(project(":kernel:contracts"))
    api(project(":owner:profile"))
    api(project(":owner:finance"))

    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.bundled)

    testImplementation(project(":kernel:runtime"))
    testImplementation(project(":kernel:apply"))
    testImplementation(project(":workspace"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
