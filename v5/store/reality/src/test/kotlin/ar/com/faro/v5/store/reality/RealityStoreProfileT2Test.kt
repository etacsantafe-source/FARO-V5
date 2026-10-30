package ar.com.faro.v5.store.reality

import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.profile.api.DeclaredDisplayName
import ar.com.faro.v5.owner.profile.api.ProfileFieldProvenance
import ar.com.faro.v5.owner.profile.api.ProfileSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RealityStoreProfileT2Test {
    @Test
    fun committedProfileWritePersistsAcrossRealFileReopen() {
        val db = Files.createTempDirectory("faro-v5-reality").resolve("reality.db")

        RealityStore(db.toString()).use { store ->
            val result = store.run(TransactionId("tx-profile-1")) { scope ->
                val snapshot = store.updateDeclared(
                    DeclaredDisplayName.of("Diego"),
                    scope,
                )
                AtomicApplyTransactionDecision.Commit(snapshot)
            }

            assertEquals("Diego", result.displayName!!.value)
            assertEquals(1L, result.revision)
            assertEquals(ProfileFieldProvenance.DECLARED_BY_USER, result.provenance)
            assertEquals(RealityStore.SCHEMA_VERSION, store.schemaVersion())
            assertEquals("ok", store.integrityCheck())
        }

        RealityStore(db.toString()).use { reopened ->
            val snapshot = reopened.read()
            assertEquals("Diego", snapshot.displayName!!.value)
            assertEquals(1L, snapshot.revision)
            assertEquals(ProfileFieldProvenance.DECLARED_BY_USER, snapshot.provenance)
            assertEquals(RealityStore.SCHEMA_VERSION, reopened.schemaVersion())
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test
    fun rollbackDecisionRestoresProfileState() {
        RealityStore(":memory:").use { store ->
            seed(store, "Diego")

            val result = store.run(TransactionId("tx-profile-rollback")) { scope ->
                store.updateDeclared(
                    DeclaredDisplayName.of("Facundo"),
                    scope,
                )
                AtomicApplyTransactionDecision.Rollback("rolled-back")
            }

            assertEquals("rolled-back", result)
            val snapshot = store.read()
            assertEquals("Diego", snapshot.displayName!!.value)
            assertEquals(1L, snapshot.revision)
        }
    }

    @Test
    fun thrownFailureRollsBackProfileState() {
        RealityStore(":memory:").use { store ->
            seed(store, "Diego")

            assertThrows(IllegalStateException::class.java) {
                store.run(TransactionId("tx-profile-failure")) { scope ->
                    store.updateDeclared(
                        DeclaredDisplayName.of("Facundo"),
                        scope,
                    )
                    error("fail after owner mutation")
                }
            }

            val snapshot = store.read()
            assertEquals("Diego", snapshot.displayName!!.value)
            assertEquals(1L, snapshot.revision)
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test
    fun profileWriteOutsideStoreTransactionIsRejected() {
        RealityStore(":memory:").use { store ->
            val fakeScope = object : RealityTransactionScope {
                override val transactionId = TransactionId("fake")
            }

            assertThrows(IllegalStateException::class.java) {
                store.updateDeclared(
                    DeclaredDisplayName.of("Diego"),
                    fakeScope,
                )
            }

            assertNull(store.read().displayName)
            assertEquals(0L, store.read().revision)
        }
    }

    @Test
    fun nestedRealityTransactionIsRejectedAndOuterTransactionRollsBack() {
        RealityStore(":memory:").use { store ->
            assertThrows(IllegalStateException::class.java) {
                store.run(TransactionId("outer")) { outerScope ->
                    store.updateDeclared(
                        DeclaredDisplayName.of("Diego"),
                        outerScope,
                    )
                    store.run(TransactionId("inner")) {
                        AtomicApplyTransactionDecision.Commit(Unit)
                    }
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }

            assertNull(store.read().displayName)
            assertEquals(0L, store.read().revision)
        }
    }

    @Test
    fun dependencyStampReadsRealProfileRevision() {
        RealityStore(":memory:").use { store ->
            assertEquals(
                DependencyStampValue("revision:0"),
                store.current(
                    ProfileSnapshot.PROFILE_AUTHORITY,
                    ProfileSnapshot.PROFILE_DECLARED_KEY,
                ),
            )

            seed(store, "Diego")

            assertEquals(
                DependencyStampValue("revision:1"),
                store.current(
                    ProfileSnapshot.PROFILE_AUTHORITY,
                    ProfileSnapshot.PROFILE_DECLARED_KEY,
                ),
            )
            assertNull(
                store.current(
                    AuthorityBoundaryId("reality.other"),
                    DependencyKey("other:key"),
                )
            )
        }
    }

    @Test
    fun applyAttemptIdentityIsStableAndDurableAcrossReopen() {
        val db = Files.createTempDirectory("faro-v5-attempt").resolve("reality.db")
        val preparedPlan = plan(PlanFingerprint("plan-profile-1"))
        var generated = 0

        val firstIdentity = RealityStore(
            fileName = db.toString(),
            transactionIdFactory = {
                generated += 1
                TransactionId("tx-generated-$generated")
            },
        ).use { store ->
            val first = store.applyAttemptIdentities.resolve(preparedPlan)
            val second = store.applyAttemptIdentities.resolve(preparedPlan)

            assertEquals(first, second)
            assertEquals(1, generated)
            assertEquals(
                ApplyAttemptSnapshot(
                    transactionId = first.transactionId,
                    planFingerprint = preparedPlan.planFingerprint,
                    state = ApplyAttemptJournalState.PREPARED,
                ),
                store.applyAttemptLookup.find(first.transactionId),
            )
            assertNull(store.executionReceiptLookup.find(first.transactionId))
            first
        }

        RealityStore(
            fileName = db.toString(),
            transactionIdFactory = {
                error("existing durable attempt must be reused")
            },
        ).use { reopened ->
            assertEquals(
                firstIdentity,
                reopened.applyAttemptIdentities.resolve(preparedPlan),
            )
            assertEquals(
                ApplyAttemptJournalState.PREPARED,
                reopened.applyAttemptLookup
                    .find(firstIdentity.transactionId)!!
                    .state,
            )
            assertNull(
                reopened.executionReceiptLookup.find(firstIdentity.transactionId)
            )
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test
    fun applyCommitBundlePersistsReceiptOutboxAndCommittedAttemptAcrossReopen() {
        val db = Files.createTempDirectory("faro-v5-commit-bundle").resolve("reality.db")
        val preparedPlan = plan(PlanFingerprint("plan-commit-bundle"))

        val identity = RealityStore(
            fileName = db.toString(),
            transactionIdFactory = { TransactionId("tx-commit-bundle") },
            receiptIdFactory = { ReceiptId("receipt-commit-bundle") },
        ).use { store ->
            val resolved = store.applyAttemptIdentities.resolve(preparedPlan)
            val effect = outboxEffect("effect-commit-bundle", resolved.transactionId)

            val receipt = store.run(resolved.transactionId) { scope ->
                store.updateDeclared(DeclaredDisplayName.of("Diego"), scope)
                AtomicApplyTransactionDecision.Commit(
                    store.commit(scope, resolved, listOf(effect))
                )
            }

            assertEquals(ReceiptId("receipt-commit-bundle"), receipt.receiptId)
            assertEquals(effect, store.readOutboxEffect(effect.effectId))
            assertEquals(
                ApplyAttemptJournalState.COMMITTED,
                store.applyAttemptLookup.find(resolved.transactionId)!!.state,
            )
            resolved
        }

        RealityStore(db.toString()).use { reopened ->
            assertEquals("Diego", reopened.read().displayName!!.value)
            assertEquals(
                ExecutionReceipt(
                    ReceiptId("receipt-commit-bundle"),
                    identity.transactionId,
                    identity.planFingerprint,
                ),
                reopened.executionReceiptLookup.find(identity.transactionId),
            )
            assertEquals(
                ApplyAttemptJournalState.COMMITTED,
                reopened.applyAttemptLookup.find(identity.transactionId)!!.state,
            )
            assertEquals(
                outboxEffect("effect-commit-bundle", identity.transactionId),
                reopened.readOutboxEffect(EffectId("effect-commit-bundle")),
            )
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test
    fun failureAfterCommitBundleRollsBackOwnerReceiptOutboxAndCommittedState() {
        RealityStore(
            fileName = ":memory:",
            transactionIdFactory = { TransactionId("tx-rollback-bundle") },
            receiptIdFactory = { ReceiptId("receipt-rollback-bundle") },
        ).use { store ->
            val preparedPlan = plan(PlanFingerprint("plan-rollback-bundle"))
            val identity = store.applyAttemptIdentities.resolve(preparedPlan)
            val effect = outboxEffect("effect-rollback-bundle", identity.transactionId)

            assertThrows(IllegalStateException::class.java) {
                store.run(identity.transactionId) { scope ->
                    store.updateDeclared(DeclaredDisplayName.of("Diego"), scope)
                    store.commit(scope, identity, listOf(effect))
                    error("fail after commit artifacts before SQLite COMMIT")
                }
            }

            assertNull(store.read().displayName)
            assertEquals(
                ApplyAttemptJournalState.PREPARED,
                store.applyAttemptLookup.find(identity.transactionId)!!.state,
            )
            assertNull(store.executionReceiptLookup.find(identity.transactionId))
            assertNull(store.readOutboxEffect(effect.effectId))
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test
    fun schemaV2MigratesToV3PreservingPreparedAttempt() {
        val db = Files.createTempDirectory("faro-v5-migrate-v2").resolve("reality.db")
        seedSchemaV2(db.toString())

        RealityStore(db.toString()).use { store ->
            assertEquals(RealityStore.SCHEMA_VERSION, store.schemaVersion())
            assertEquals("Diego", store.read().displayName!!.value)
            assertEquals(
                ApplyAttemptSnapshot(
                    TransactionId("tx-v2-prepared"),
                    PlanFingerprint("plan-v2-prepared"),
                    ApplyAttemptJournalState.PREPARED,
                ),
                store.applyAttemptLookup.find(TransactionId("tx-v2-prepared")),
            )
            assertNull(store.readOutboxEffect(EffectId("missing")))
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test
    fun differentPlanFingerprintGetsDifferentDurableAttempt() {
        var generated = 0
        RealityStore(
            fileName = ":memory:",
            transactionIdFactory = {
                generated += 1
                TransactionId("tx-$generated")
            },
        ).use { store ->
            val first = store.applyAttemptIdentities.resolve(
                plan(PlanFingerprint("plan-a"))
            )
            val second = store.applyAttemptIdentities.resolve(
                plan(PlanFingerprint("plan-b"))
            )

            assertEquals(TransactionId("tx-1"), first.transactionId)
            assertEquals(TransactionId("tx-2"), second.transactionId)
            assertEquals(2, generated)
        }
    }

    @Test
    fun schemaV1MigratesToV2WithoutLosingProfileTruth() {
        val db = Files.createTempDirectory("faro-v5-migrate").resolve("reality.db")
        seedSchemaV1(db.toString())

        RealityStore(
            fileName = db.toString(),
            transactionIdFactory = { TransactionId("tx-after-migration") },
        ).use { store ->
            assertEquals(RealityStore.SCHEMA_VERSION, store.schemaVersion())
            assertEquals("Diego", store.read().displayName!!.value)
            assertEquals(7L, store.read().revision)

            val identity = store.applyAttemptIdentities.resolve(
                plan(PlanFingerprint("plan-after-migration"))
            )
            assertEquals(
                ApplyAttemptJournalState.PREPARED,
                store.applyAttemptLookup.find(identity.transactionId)!!.state,
            )
            assertEquals("ok", store.integrityCheck())
        }
    }

    private fun seed(
        store: RealityStore,
        name: String,
    ) {
        store.run(TransactionId("seed-" + name)) { scope ->
            store.updateDeclared(
                DeclaredDisplayName.of(name),
                scope,
            )
            AtomicApplyTransactionDecision.Commit(Unit)
        }
    }

    private fun seedSchemaV1(fileName: String) {
        val connection = BundledSQLiteDriver().open(fileName)
        try {
            connection.execSQL(
                """
                CREATE TABLE profile_state (
                    singleton_id INTEGER NOT NULL PRIMARY KEY CHECK(singleton_id = 1),
                    display_name TEXT NOT NULL DEFAULT '',
                    provenance TEXT NOT NULL DEFAULT '',
                    revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0),
                    CHECK(
                        (display_name = '' AND provenance = '')
                        OR
                        (display_name <> '' AND provenance = 'DECLARED_BY_USER')
                    )
                )
                """.trimIndent()
            )
            connection.prepare(
                """
                INSERT INTO profile_state(
                    singleton_id,
                    display_name,
                    provenance,
                    revision
                ) VALUES (1, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, "Diego")
                statement.bindText(2, ProfileFieldProvenance.DECLARED_BY_USER.name)
                statement.bindLong(3, 7L)
                statement.step()
            }
            connection.execSQL("PRAGMA user_version = 1")
        } finally {
            connection.close()
        }
    }

    private fun seedSchemaV2(fileName: String) {
        seedSchemaV1(fileName)
        val connection = BundledSQLiteDriver().open(fileName)
        try {
            connection.execSQL(
                """
                CREATE TABLE apply_attempt (
                    transaction_id TEXT NOT NULL PRIMARY KEY,
                    plan_fingerprint TEXT NOT NULL UNIQUE,
                    state TEXT NOT NULL CHECK(state IN ('PREPARED', 'COMMITTED')),
                    UNIQUE(transaction_id, plan_fingerprint)
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE execution_receipt (
                    receipt_id TEXT NOT NULL PRIMARY KEY,
                    transaction_id TEXT NOT NULL UNIQUE,
                    plan_fingerprint TEXT NOT NULL,
                    FOREIGN KEY(transaction_id, plan_fingerprint)
                        REFERENCES apply_attempt(transaction_id, plan_fingerprint)
                        ON UPDATE RESTRICT
                        ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            connection.prepare(
                """
                INSERT INTO apply_attempt(
                    transaction_id,
                    plan_fingerprint,
                    state
                ) VALUES (?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, "tx-v2-prepared")
                statement.bindText(2, "plan-v2-prepared")
                statement.bindText(3, ApplyAttemptJournalState.PREPARED.name)
                statement.step()
            }
            connection.execSQL("PRAGMA user_version = 2")
        } finally {
            connection.close()
        }
    }

    private fun outboxEffect(
        effectId: String,
        transactionId: TransactionId,
    ) = OutboxEffectIntent(
        effectId = EffectId(effectId),
        sourceTransactionId = transactionId,
        idempotencyKey = IdempotencyKey("idem-" + effectId),
        payload = CanonicalPayload.fromEncoded(
            "effect-v1",
            effectId.toByteArray(),
        ),
    )

    private fun plan(fingerprint: PlanFingerprint): PreparedPlanView {
        val descriptor = OperationContractDescriptor(
            operationId = OperationId("profile.update_declared"),
            capabilityId = CapabilityId("N-001"),
            canonicalExecutor = ExecutorId("profile"),
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("1"),
            contractFingerprint = ContractFingerprint("fixture-contract"),
            exposure = ContractExposure.BINDABLE,
        )
        val applyPlan = ApplyPlan.of(
            ApplyPlanNode(
                nodeId = PlanNodeId("profile-node"),
                preparedOperation = PreparedOperation(
                    contract = descriptor,
                    payload = CanonicalPayload.fromEncoded(
                        "profile-fixture-v1",
                        "displayName=Diego".toByteArray(),
                    ),
                    dependencyStamps = DependencyStampSet.of(
                        DependencyStamp(
                            authorityBoundary = ProfileSnapshot.PROFILE_AUTHORITY,
                            dependencyKey = ProfileSnapshot.PROFILE_DECLARED_KEY,
                            observedValue = DependencyStampValue("revision:0"),
                        )
                    ),
                    idempotencyKey = IdempotencyKey("profile-intent"),
                ),
                dependsOn = emptySet(),
            )
        )
        return object : PreparedPlanView {
            override val applyPlan = applyPlan
            override val planFingerprint = fingerprint
        }
    }
}
