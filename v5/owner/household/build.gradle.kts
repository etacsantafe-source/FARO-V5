plugins {
    id("faro.jvm-library")
}

dependencies {
    api(project(":kernel:contracts"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
