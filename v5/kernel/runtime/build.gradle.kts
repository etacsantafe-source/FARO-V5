plugins {
    id("faro.jvm-library")
}

val generateRuntimeContractCatalog = rootProject.tasks.named("generateRuntimeContractCatalog")

kotlin {
    sourceSets.getByName("main").kotlin.srcDir(generateRuntimeContractCatalog)
}

dependencies {
    api(project(":kernel:contracts"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
