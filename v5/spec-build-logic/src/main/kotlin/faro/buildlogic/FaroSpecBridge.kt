package faro.buildlogic

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object FaroSpecBridge {
    private const val CAPABILITY_PATH = "docs/v5/V5_CAPABILITY_TAXONOMY.md"
    private const val OPERATION_PATH = "docs/v5/V5_OPERATION_CONTRACT_INVENTORY.md"
    private const val REFREEZE_PATH = "docs/v5/V5_RG06C_07_GLOBAL_REFREEZE.md"

    private val capabilityRegex = Regex("""\bN-\d{3}\b""")
    private val operationIdRegex = Regex("""[a-z0-9_]+(?:\.[a-z0-9_]+)+""")
    private val profileOrder = listOf(
        "READ_ONLY",
        "REALITY_MUTATION",
        "AUXILIARY_MUTATION",
        "OBSERVATIONAL_WRITE",
        "EXTERNAL_EFFECT",
        "MAINTENANCE_EFFECT",
    )

    private data class OperationRow(
        val capabilityId: String,
        val operationId: String,
        val executor: String,
        val profile: String,
        val disposition: String,
    )

    private data class BaselineOperationRow(
        val capabilityId: String,
        val operationId: String,
        val executor: String,
        val profile: String,
        val exposure: String,
        val disposition: String,
    )

    fun generate(
        taxonomyFile: File,
        operationInventoryFile: File,
        refreezeFile: File,
        generatorVersion: String,
    ): String {
        val taxonomy = taxonomyFile.readText()
        val inventory = operationInventoryFile.readText()
        val refreeze = refreezeFile.readText()

        val capabilities = parseCapabilities(taxonomy)
        val operations = parseOperations(inventory)

        check(capabilities.size == capabilities.toSet().size) {
            "Spec Bridge: duplicate capability identity in taxonomy family membership"
        }
        check(operations.size == operations.map { it.operationId }.toSet().size) {
            "Spec Bridge: duplicate OperationId"
        }
        val capabilitySet = capabilities.toSet()
        operations.forEach { operation ->
            check(operation.capabilityId in capabilitySet) {
                "Spec Bridge: ${operation.operationId} references unknown capability ${operation.capabilityId}"
            }
        }

        val expectedCapabilityCount = Regex("""(?m)^-\s+(\d+)\s+capabilities;""")
            .find(refreeze)?.groupValues?.get(1)?.toInt()
            ?: error("Spec Bridge: refreeze capability count missing")
        val expectedOperationCount = Regex("""(?m)^-\s+(\d+)\s+OperationContracts;""")
            .find(refreeze)?.groupValues?.get(1)?.toInt()
            ?: error("Spec Bridge: refreeze operation count missing")
        val authorityRevision = Regex("""Decisión:\s+\*\*(D-\d+)\*\*""")
            .find(refreeze)?.groupValues?.get(1)
            ?: error("Spec Bridge: authority revision missing")
        val declaredProfileMatch = Regex(
            """profiles\s+\*\*(\d+)/(\d+)/(\d+)/(\d+)/(\d+)/(\d+)\*\*"""
        ).find(inventory) ?: error("Spec Bridge: declared profile counts missing")
        val declaredProfiles = profileOrder.mapIndexed { index, profile ->
            profile to declaredProfileMatch.groupValues[index + 1].toInt()
        }.toMap()
        val actualProfiles = profileOrder.associateWith { profile ->
            operations.count { it.profile == profile }
        }

        check(capabilities.size == expectedCapabilityCount) {
            "Spec Bridge: capability count ${capabilities.size} != refreeze $expectedCapabilityCount"
        }
        check(operations.size == expectedOperationCount) {
            "Spec Bridge: operation count ${operations.size} != refreeze $expectedOperationCount"
        }
        check(actualProfiles == declaredProfiles) {
            "Spec Bridge: profile counts $actualProfiles != declared $declaredProfiles"
        }

        return buildString {
            appendLine("# FARO V5 GENERATED SPEC BASELINE — DO NOT EDIT")
            appendLine("generator_version\t$generatorVersion")
            appendLine("authority_revision\t$authorityRevision")
            appendLine("source_blob\t$CAPABILITY_PATH\t${gitBlobSha(taxonomyFile)}")
            appendLine("source_blob\t$OPERATION_PATH\t${gitBlobSha(operationInventoryFile)}")
            appendLine("source_blob\t$REFREEZE_PATH\t${gitBlobSha(refreezeFile)}")
            appendLine("capability_count\t${capabilities.size}")
            appendLine("operation_count\t${operations.size}")
            profileOrder.forEach { profile ->
                appendLine("profile_count\t$profile\t${actualProfiles.getValue(profile)}")
            }
            appendLine("[capabilities]")
            capabilities.sorted().forEach { capability ->
                appendLine("capability\t$capability")
            }
            appendLine("[operations]")
            operations.sortedBy { it.operationId }.forEach { operation ->
                val exposure = if (operation.disposition == "INTERNAL_ONLY") {
                    "INTERNAL_ONLY"
                } else {
                    "BINDABLE"
                }
                appendLine(
                    listOf(
                        "operation",
                        operation.capabilityId,
                        operation.operationId,
                        operation.executor,
                        operation.profile,
                        exposure,
                        operation.disposition,
                    ).joinToString("\t")
                )
            }
        }
    }

    fun generateRuntimeCatalogKotlin(
        baselineFile: File,
        contractMetadataVersion: String,
    ): String {
        val lines = baselineFile.readLines()
        val capabilities = lines
            .filter { it.startsWith("capability\t") }
            .map { it.substringAfter('\t') }
        val operations = lines
            .filter { it.startsWith("operation\t") }
            .map { line ->
                val cells = line.split('\t')
                check(cells.size == 7) { "Spec Bridge runtime: malformed operation row: $line" }
                BaselineOperationRow(
                    capabilityId = cells[1],
                    operationId = cells[2],
                    executor = cells[3],
                    profile = cells[4],
                    exposure = cells[5],
                    disposition = cells[6],
                )
            }

        val expectedCapabilities = lines.firstOrNull { it.startsWith("capability_count\t") }
            ?.substringAfter('\t')?.toInt()
            ?: error("Spec Bridge runtime: capability_count missing")
        val expectedOperations = lines.firstOrNull { it.startsWith("operation_count\t") }
            ?.substringAfter('\t')?.toInt()
            ?: error("Spec Bridge runtime: operation_count missing")
        val expectedProfiles = lines
            .filter { it.startsWith("profile_count\t") }
            .associate { line ->
                val cells = line.split('\t')
                check(cells.size == 3) { "Spec Bridge runtime: malformed profile_count row: $line" }
                cells[1] to cells[2].toInt()
            }

        check(capabilities.size == expectedCapabilities) {
            "Spec Bridge runtime: capability count mismatch"
        }
        check(operations.size == expectedOperations) {
            "Spec Bridge runtime: operation count mismatch"
        }
        check(capabilities.size == capabilities.toSet().size) {
            "Spec Bridge runtime: duplicate capability"
        }
        check(operations.size == operations.map { it.operationId }.toSet().size) {
            "Spec Bridge runtime: duplicate operation"
        }
        val capabilitySet = capabilities.toSet()
        operations.forEach { operation ->
            check(operation.capabilityId in capabilitySet) {
                "Spec Bridge runtime: unknown capability for ${operation.operationId}"
            }
            check(operation.profile in profileOrder) {
                "Spec Bridge runtime: unknown profile for ${operation.operationId}"
            }
            check(operation.exposure == "BINDABLE" || operation.exposure == "INTERNAL_ONLY") {
                "Spec Bridge runtime: unknown exposure for ${operation.operationId}"
            }
        }
        val actualProfiles = profileOrder.associateWith { profile ->
            operations.count { it.profile == profile }
        }
        check(actualProfiles == expectedProfiles) {
            "Spec Bridge runtime: profile counts $actualProfiles != baseline $expectedProfiles"
        }

        return buildString {
            appendLine("package ar.com.faro.v5.kernel.runtime")
            appendLine()
            appendLine("import ar.com.faro.v5.kernel.contracts.*")
            appendLine()
            appendLine("/** Generated from the verified FARO V5 spec baseline. Do not edit. */")
            appendLine("internal object GeneratedRuntimeContractCatalog {")
            appendLine("    const val CONTRACT_METADATA_VERSION = \"${kotlinString(contractMetadataVersion)}\"")
            appendLine()
            appendLine("    fun create(): ContractCatalog = ContractCatalog.of(")
            appendLine("        capabilities = listOf(")
            capabilities.sorted().forEach { capability ->
                appendLine(
                    "            CapabilityDescriptor(CapabilityId(\"${kotlinString(capability)}\")),"
                )
            }
            appendLine("        ),")
            appendLine("        operations = listOf(")
            operations.sortedBy { it.operationId }.forEach { operation ->
                val fingerprint = contractFingerprint(operation, contractMetadataVersion)
                appendLine("            OperationContractDescriptor(")
                appendLine("                operationId = OperationId(\"${kotlinString(operation.operationId)}\"),")
                appendLine("                capabilityId = CapabilityId(\"${kotlinString(operation.capabilityId)}\"),")
                appendLine("                canonicalExecutor = ExecutorId(\"${kotlinString(operation.executor)}\"),")
                appendLine("                executionProfile = ExecutionProfile.${operation.profile},")
                appendLine("                contractVersion = ContractVersion(CONTRACT_METADATA_VERSION),")
                appendLine("                contractFingerprint = ContractFingerprint(\"$fingerprint\"),")
                appendLine("                exposure = ContractExposure.${operation.exposure},")
                appendLine("            ),")
            }
            appendLine("        ),")
            appendLine("    )")
            appendLine("}")
        }
    }

    private fun contractFingerprint(
        operation: BaselineOperationRow,
        contractMetadataVersion: String,
    ): String {
        val canonical = listOf(
            "faro-contract-descriptor",
            contractMetadataVersion,
            operation.operationId,
            operation.capabilityId,
            operation.executor,
            operation.profile,
            operation.exposure,
        ).joinToString("\n")
        return sha256(canonical)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun kotlinString(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

    private fun parseCapabilities(markdown: String): List<String> {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { it.startsWith("## 3.") }
        val end = lines.indices.firstOrNull { index ->
            index > start && lines[index].startsWith("## 4.")
        } ?: -1
        check(start >= 0 && end > start) {
            "Spec Bridge: canonical taxonomy family section missing"
        }

        val result = mutableListOf<String>()
        var index = start
        while (index < end) {
            if (lines[index].trim() == "Incluye:") {
                var memberLine = index + 1
                while (memberLine < end && lines[memberLine].isBlank()) memberLine++
                check(memberLine < end) { "Spec Bridge: empty family membership" }
                val ids = capabilityRegex.findAll(lines[memberLine]).map { it.value }.toList()
                check(ids.isNotEmpty()) { "Spec Bridge: family without capability identities" }
                result += ids
            }
            index++
        }
        return result
    }

    private fun parseOperations(markdown: String): List<OperationRow> {
        val profileSet = profileOrder.toSet()
        return markdown.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) return@mapNotNull null
            val cells = trimmed.removePrefix("|").removeSuffix("|")
                .split("|")
                .map(::cleanCell)
            if (cells.size != 6) return@mapNotNull null
            if (!Regex("""N-\d{3}""").matches(cells[0])) return@mapNotNull null
            if (!operationIdRegex.matches(cells[1])) return@mapNotNull null
            if (cells[3] !in profileSet) return@mapNotNull null
            check(cells[2].isNotBlank()) { "Spec Bridge: blank executor for ${cells[1]}" }
            check(cells[4].isNotBlank()) { "Spec Bridge: blank disposition for ${cells[1]}" }
            OperationRow(
                capabilityId = cells[0],
                operationId = cells[1],
                executor = cells[2],
                profile = cells[3],
                disposition = cells[4],
            )
        }.toList()
    }

    private fun cleanCell(value: String): String =
        value.trim().replace("`", "").replace("**", "").trim()

    private fun gitBlobSha(file: File): String {
        val bytes = file.readBytes()
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray(StandardCharsets.UTF_8))
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
