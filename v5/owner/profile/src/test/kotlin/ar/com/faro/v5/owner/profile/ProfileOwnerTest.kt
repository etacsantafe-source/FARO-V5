package ar.com.faro.v5.owner.profile

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.profile.api.*
import ar.com.faro.v5.owner.profile.spi.ProfilePersistencePort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProfileOwnerTest {
    @Test
    fun displayNameIsMinimalTrimmedAndBounded() {
        assertEquals("Diego", DeclaredDisplayName.of("  Diego  ").value)
        assertThrows(IllegalArgumentException::class.java) {
            DeclaredDisplayName.of("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeclaredDisplayName.of("x".repeat(DeclaredDisplayName.MAX_LENGTH + 1))
        }
    }

    @Test
    fun snapshotPublishesStableProfileDependencyStamp() {
        val snapshot = ProfileSnapshot(
            displayName = DeclaredDisplayName.of("Diego"),
            revision = 7,
            provenance = ProfileFieldProvenance.DECLARED_BY_USER,
        )

        assertEquals(
            DependencyStamp(
                authorityBoundary = AuthorityBoundaryId("reality.profile"),
                dependencyKey = DependencyKey("profile:declared"),
                observedValue = DependencyStampValue("revision:7"),
            ),
            snapshot.dependencyStamp(),
        )
    }

    @Test
    fun payloadRoundTripsDeclaredNameWithoutOtherPreferences() {
        val update = ProfileDeclaredUpdate(DeclaredDisplayName.of("Diego"))
        val payload = ProfileDeclaredUpdatePayload.payload(update)

        assertEquals(ProfileDeclaredUpdatePayload.FORMAT_ID, payload.formatId)
        assertEquals(update, ProfileDeclaredUpdatePayload.decode(payload))
    }

    @Test
    fun readerDelegatesWithoutMutation() {
        val persistence = RecordingPersistence()
        val reader = ProfileReader(persistence)

        assertEquals(persistence.snapshot, reader.read())
        assertEquals(1, persistence.reads)
        assertEquals(0, persistence.writes)
    }

    @Test
    fun updateBindingUsesCanonicalMetadataAndSameTransactionScope() {
        val persistence = RecordingPersistence()
        val binding = ProfileUpdateDeclaredBinding(persistence)
        val scope = scope("tx-profile")
        val prepared = prepared("Diego")

        val result = binding.execute(prepared, scope)

        assertEquals(OperationBindingExecutionResult.Completed, result)
        assertEquals(ProfileOperations.UPDATE_DECLARED, binding.descriptor.operationId)
        assertEquals(ProfileOperations.EXECUTOR_ID, binding.descriptor.canonicalExecutor)
        assertEquals(ExecutionProfile.REALITY_MUTATION, binding.descriptor.executionProfile)
        assertSame(scope, persistence.lastScope)
        assertEquals("Diego", persistence.snapshot.displayName!!.value)
        assertEquals(1, persistence.writes)
    }

    @Test
    fun invalidPayloadRejectsBeforePersistenceWrite() {
        val persistence = RecordingPersistence()
        val prepared = prepared("Diego").copy(
            payload = CanonicalPayload.fromEncoded(
                "wrong-format",
                "Diego".toByteArray(),
            )
        )

        val result = ProfileUpdateDeclaredBinding(persistence)
            .execute(prepared, scope("tx-profile"))

        assertEquals(
            OperationBindingExecutionResult.Rejected("profile_payload_invalid"),
            result,
        )
        assertEquals(0, persistence.writes)
    }

    @Test
    fun revalidationRejectsMalformedPreparedPayload() {
        val prepared = prepared("Diego").copy(
            payload = CanonicalPayload.fromEncoded(
                ProfileDeclaredUpdatePayload.FORMAT_ID,
                ByteArray(0),
            )
        )

        assertEquals(
            OperationRevalidationResult.Rejected(
                OperationRevalidationFailure.PRECONDITION_FAILED,
                "profile_payload_invalid",
            ),
            ProfileUpdateDeclaredRevalidation().revalidate(prepared),
        )
    }

    private fun prepared(name: String) = PreparedOperation(
        contract = OperationContractDescriptor(
            operationId = ProfileOperations.UPDATE_DECLARED,
            capabilityId = ProfileOperations.CAPABILITY_ID,
            canonicalExecutor = ProfileOperations.EXECUTOR_ID,
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("fixture"),
            contractFingerprint = ContractFingerprint("fixture-profile-update"),
            exposure = ContractExposure.BINDABLE,
        ),
        payload = ProfileDeclaredUpdatePayload.payload(
            ProfileDeclaredUpdate(DeclaredDisplayName.of(name))
        ),
        dependencyStamps = DependencyStampSet.of(
            ProfileSnapshot(
                displayName = null,
                revision = 0,
                provenance = null,
            ).dependencyStamp()
        ),
        idempotencyKey = IdempotencyKey("profile-intent"),
    )

    private fun scope(id: String) = object : RealityTransactionScope {
        override val transactionId = TransactionId(id)
    }

    private class RecordingPersistence : ProfilePersistencePort {
        var snapshot = ProfileSnapshot(
            displayName = null,
            revision = 0,
            provenance = null,
        )
        var reads = 0
        var writes = 0
        var lastScope: RealityTransactionScope? = null

        override fun read(): ProfileSnapshot {
            reads += 1
            return snapshot
        }

        override fun updateDeclared(
            displayName: DeclaredDisplayName,
            transactionScope: RealityTransactionScope,
        ): ProfileSnapshot {
            writes += 1
            lastScope = transactionScope
            snapshot = ProfileSnapshot(
                displayName = displayName,
                revision = snapshot.revision + 1,
                provenance = ProfileFieldProvenance.DECLARED_BY_USER,
            )
            return snapshot
        }
    }
}
