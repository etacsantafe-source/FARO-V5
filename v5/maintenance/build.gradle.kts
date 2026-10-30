plugins {
    id("faro.jvm-library")
}

dependencies {
    implementation(project(":kernel:contracts"))
    implementation(project(":store:reality"))
    implementation(project(":evidence"))
    implementation(project(":store:auxiliary"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
