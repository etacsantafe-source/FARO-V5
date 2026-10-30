import faro.buildlogic.FaroSpecBridge
import org.gradle.api.tasks.PathSensitivity

plugins {
    base
}

val specGeneratorVersion = "1"
val repositoryRoot = rootProject.projectDir.parentFile
val taxonomyAuthority = repositoryRoot.resolve("docs/v5/V5_CAPABILITY_TAXONOMY.md")
val operationAuthority = repositoryRoot.resolve("docs/v5/V5_OPERATION_CONTRACT_INVENTORY.md")
val refreezeAuthority = repositoryRoot.resolve("docs/v5/V5_RG06C_07_GLOBAL_REFREEZE.md")
val checkedInBaseline = layout.projectDirectory.file("spec/generated/contract-baseline.tsv")
val generatedBaseline = layout.buildDirectory.file("generated/faro-spec/contract-baseline.tsv")
val runtimeCatalogOutput = layout.buildDirectory.dir("generated/faro-spec/runtime-kotlin")
val contractMetadataVersion = "metadata-v1"

val generateSpecBaseline = tasks.register("generateSpecBaseline") {
    group = "verification"
    description = "Derives the FARO V5 contract baseline from frozen Markdown authorities."

    inputs.files(taxonomyAuthority, operationAuthority, refreezeAuthority)
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("generatorVersion", specGeneratorVersion)
    outputs.file(generatedBaseline)

    doLast {
        val output = generatedBaseline.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            FaroSpecBridge.generate(
                taxonomyFile = taxonomyAuthority,
                operationInventoryFile = operationAuthority,
                refreezeFile = refreezeAuthority,
                generatorVersion = specGeneratorVersion,
            )
        )
    }
}

val verifySpecBaselineFresh = tasks.register("verifySpecBaselineFresh") {
    group = "verification"
    description = "Fails if the checked-in FARO V5 generated contract baseline is stale or inconsistent."
    dependsOn(generateSpecBaseline)

    inputs.file(checkedInBaseline).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(generatedBaseline).withPathSensitivity(PathSensitivity.RELATIVE)

    doLast {
        val expectedFile = checkedInBaseline.asFile
        check(expectedFile.isFile) {
            "Spec Bridge: checked-in baseline missing at ${expectedFile.path}"
        }
        val expected = expectedFile.readText()
        val actual = generatedBaseline.get().asFile.readText()
        check(expected == actual) {
            "Spec Bridge: generated baseline is stale. Regenerate from current V5 authorities before continuing."
        }
        println("FARO_V5_SPEC_BASELINE_PASS")
    }
}

val generateRuntimeContractCatalog = tasks.register("generateRuntimeContractCatalog") {
    group = "build"
    description = "Generates the JVM runtime catalog from the verified FARO V5 spec baseline."
    dependsOn(verifySpecBaselineFresh)

    inputs.file(checkedInBaseline).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("contractMetadataVersion", contractMetadataVersion)
    outputs.dir(runtimeCatalogOutput)

    doLast {
        val outputDir = runtimeCatalogOutput.get().asFile
        outputDir.deleteRecursively()
        val output = outputDir.resolve(
            "ar/com/faro/v5/kernel/runtime/GeneratedRuntimeContractCatalog.kt"
        )
        output.parentFile.mkdirs()
        output.writeText(
            FaroSpecBridge.generateRuntimeCatalogKotlin(
                baselineFile = checkedInBaseline.asFile,
                contractMetadataVersion = contractMetadataVersion,
            )
        )
    }
}

tasks.named("check") {
    dependsOn(verifySpecBaselineFresh)
}
