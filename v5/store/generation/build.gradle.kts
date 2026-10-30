plugins {
    id("faro.jvm-library")
}

dependencies {
    implementation(project(":maintenance"))
    implementation(project(":workspace"))
    implementation(project(":store:reality"))
    implementation(project(":store:auxiliary"))
    implementation(project(":evidence"))

    // Native SQLite fixture access is test-only; production generation stays SQL-driver-free.
    testImplementation(libs.androidx.sqlite)
    testImplementation(libs.androidx.sqlite.bundled)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
