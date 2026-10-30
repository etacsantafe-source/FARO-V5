plugins {
    id("faro.jvm-library")
}

dependencies {
    api(project(":kernel:contracts"))
    api(project(":delivery"))

    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.bundled)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
