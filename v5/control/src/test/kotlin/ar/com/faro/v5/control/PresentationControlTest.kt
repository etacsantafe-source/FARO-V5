package ar.com.faro.v5.control

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.*
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PresentationControlTest {
    @Test
    fun readOnlyDefaultDoesNotMaterializePreferenceFile() = runBlocking {
        val dir = Files.createTempDirectory("faro-v5-control-read")
        val file = dir.resolve("presentation.preferences_pb").toFile()
        assertFalse(file.exists())

        PresentationPreferenceStore(file).use { store ->
            val read = PresentationReadExecutor(store)
            assertEquals(PresentationSnapshot.DEFAULT, read.execute())
            assertEquals(PresentationSnapshot.DEFAULT, read.execute())
        }

        assertFalse(file.exists())
    }

    @Test
    fun themeSetPersistsReopensAndReceiptLikeRetryIsRecognized() = runBlocking {
        val dir = Files.createTempDirectory("faro-v5-control-theme")
        val file = dir.resolve("presentation.preferences_pb").toFile()
        lateinit var prepared: PreparedOperation

        PresentationPreferenceStore(file).use { store ->
            val executor = PresentationControlExecutor(store)
            prepared = prepareThemeSet(
                store = store,
                executor = executor,
                themeMode = PresentationThemeMode.NIGHT,
                idempotencyKey = "theme-night-1",
            )

            val first = executor.executeThemeSet(prepared)
            assertTrue(first is PresentationThemeExecution.Applied)
            val firstOutcome =
                (first as PresentationThemeExecution.Applied).outcome
            assertTrue(firstOutcome is ThemeSetOutcome.Committed)
            firstOutcome as ThemeSetOutcome.Committed
            assertFalse(firstOutcome.replayed)
            assertTrue(firstOutcome.changed)
            assertEquals(PresentationThemeMode.NIGHT, firstOutcome.snapshot.themeMode)
            assertEquals(1L, firstOutcome.snapshot.revision)
        }

        assertTrue(file.exists())

        PresentationPreferenceStore(file).use { reopened ->
            val read = PresentationReadExecutor(reopened).execute()
            assertEquals(PresentationThemeMode.NIGHT, read.themeMode)
            assertEquals(false, read.revealAmounts)
            assertEquals(1L, read.revision)

            val replay = PresentationControlExecutor(reopened)
                .executeThemeSet(prepared)
            assertTrue(replay is PresentationThemeExecution.Applied)
            val replayOutcome =
                (replay as PresentationThemeExecution.Applied).outcome
            assertTrue(replayOutcome is ThemeSetOutcome.Committed)
            replayOutcome as ThemeSetOutcome.Committed
            assertTrue(replayOutcome.replayed)
            assertEquals(1L, replayOutcome.snapshot.revision)
        }
    }

    @Test
    fun stalePreparedThemeDoesNotOverwriteNewerControlState() = runBlocking {
        val dir = Files.createTempDirectory("faro-v5-control-stale")
        val file = dir.resolve("presentation.preferences_pb").toFile()

        PresentationPreferenceStore(file).use { store ->
            val executor = PresentationControlExecutor(store)
            val stale = prepareThemeSet(
                store = store,
                executor = executor,
                themeMode = PresentationThemeMode.NIGHT,
                idempotencyKey = "stale-night",
            )
            val current = prepareThemeSet(
                store = store,
                executor = executor,
                themeMode = PresentationThemeMode.DAY,
                idempotencyKey = "current-day",
            )

            val currentResult = executor.executeThemeSet(current)
            assertTrue(currentResult is PresentationThemeExecution.Applied)
            val staleResult = executor.executeThemeSet(stale)
            assertTrue(staleResult is PresentationThemeExecution.Applied)
            assertTrue(
                (staleResult as PresentationThemeExecution.Applied).outcome
                    is ThemeSetOutcome.RejectedStale
            )

            val read = store.read()
            assertEquals(PresentationThemeMode.DAY, read.themeMode)
            assertEquals(1L, read.revision)
        }
    }

    @Test
    fun sameIdempotencyKeyWithDifferentPayloadFailsClosed() = runBlocking {
        val dir = Files.createTempDirectory("faro-v5-control-key-conflict")
        val file = dir.resolve("presentation.preferences_pb").toFile()

        PresentationPreferenceStore(file).use { store ->
            val executor = PresentationControlExecutor(store)
            val night = prepareThemeSet(
                store = store,
                executor = executor,
                themeMode = PresentationThemeMode.NIGHT,
                idempotencyKey = "same-key",
            )
            val first = executor.executeThemeSet(night)
            assertTrue(first is PresentationThemeExecution.Applied)

            val conflicting = PreparedOperation(
                contract = night.contract,
                payload = ThemeSetPayload.payload(PresentationThemeMode.DAY),
                dependencyStamps = night.dependencyStamps,
                idempotencyKey = night.idempotencyKey,
            )
            val result = executor.executeThemeSet(conflicting)
            assertTrue(result is PresentationThemeExecution.Applied)
            assertTrue(
                (result as PresentationThemeExecution.Applied).outcome
                    is ThemeSetOutcome.RejectedIdentityConflict
            )
            assertEquals(PresentationThemeMode.NIGHT, store.read().themeMode)
            assertEquals(1L, store.read().revision)
        }
    }

    @Test
    fun sameThemeWithFreshIdentityCommitsDurableOutcomeWithoutRevisionChurn() = runBlocking {
        val dir = Files.createTempDirectory("faro-v5-control-noop")
        val file = dir.resolve("presentation.preferences_pb").toFile()

        PresentationPreferenceStore(file).use { store ->
            val executor = PresentationControlExecutor(store)
            val prepared = prepareThemeSet(
                store = store,
                executor = executor,
                themeMode = PresentationThemeMode.SYSTEM,
                idempotencyKey = "system-noop",
            )

            val result = executor.executeThemeSet(prepared)
            assertTrue(result is PresentationThemeExecution.Applied)
            val outcome =
                (result as PresentationThemeExecution.Applied).outcome
            assertTrue(outcome is ThemeSetOutcome.Committed)
            outcome as ThemeSetOutcome.Committed
            assertFalse(outcome.changed)
            assertEquals(0L, outcome.snapshot.revision)
        }

        assertTrue(file.exists())
    }

    @Test
    fun corruptControlStoreFailsClosedInsteadOfResettingAuthority() {
        val dir = Files.createTempDirectory("faro-v5-control-corrupt")
        val file = dir.resolve("presentation.preferences_pb").toFile()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))

        assertThrows(Exception::class.java) {
            runBlocking {
                PresentationPreferenceStore(file).use { store ->
                    store.read()
                }
            }
        }
        assertTrue(file.exists())
        assertEquals(6L, file.length())
    }

    @Test
    fun runtimeAvailabilityUsesAuxiliaryExecutorWithoutRealityBinding() = runBlocking {
        val dir = Files.createTempDirectory("faro-v5-control-runtime")
        val file = dir.resolve("presentation.preferences_pb").toFile()

        PresentationPreferenceStore(file).use { store ->
            val readExecutor = PresentationReadExecutor(store)
            val themeExecutor = PresentationControlExecutor(store)
            val catalog = ContractCatalog.of(
                capabilities = listOf(
                    CapabilityDescriptor(PresentationOperations.CAPABILITY_ID)
                ),
                operations = listOf(
                    PresentationOperations.READ_CONTRACT,
                    PresentationOperations.THEME_SET_CONTRACT,
                ),
            )
            val registry = BindingRegistry.of(
                catalog = catalog,
                additionalExecutors = listOf(readExecutor, themeExecutor),
            )
            val session = CapabilitySession.open(registry)

            assertEquals(
                ContractAvailability.AVAILABLE,
                session.availability(PresentationOperations.PRESENTATION_READ),
            )
            assertEquals(
                ContractAvailability.AVAILABLE,
                session.availability(PresentationOperations.THEME_SET),
            )
            assertNotNull(session.resolve(PresentationOperations.THEME_SET))
            assertEquals(
                null,
                registry.operationBinding(PresentationOperations.THEME_SET),
            )
        }
    }

    private suspend fun prepareThemeSet(
        store: PresentationPreferenceStore,
        executor: PresentationControlExecutor,
        themeMode: PresentationThemeMode,
        idempotencyKey: String,
    ): PreparedOperation {
        val catalog = ContractCatalog.of(
            capabilities = listOf(
                CapabilityDescriptor(PresentationOperations.CAPABILITY_ID)
            ),
            operations = listOf(
                PresentationOperations.READ_CONTRACT,
                PresentationOperations.THEME_SET_CONTRACT,
            ),
        )
        val registry = BindingRegistry.of(
            catalog = catalog,
            additionalExecutors = listOf(
                PresentationReadExecutor(store),
                executor,
            ),
        )
        val session = CapabilitySession.open(registry)
        val runtime = PreparationRuntime(
            IdempotencyKeySource { IdempotencyKey(idempotencyKey) }
        )
        val capturedPresentationStamp = store.read().dependencyStamp()
        val spec = PreparationSpec(
            operationId = PresentationOperations.THEME_SET,
            inputEncoder =
                object : CanonicalInputEncoder<PresentationThemeMode> {
                    override val formatId: String = ThemeSetPayload.FORMAT_ID

                    override fun encode(
                        input: PresentationThemeMode,
                    ): ByteArray =
                        input.name.toByteArray(Charsets.UTF_8)
                },
            dependencyStampSource =
                DependencyStampSource<PresentationThemeMode> {
                    DependencyStampSet.of(capturedPresentationStamp)
                },
            validator =
                PreparationValidator<PresentationThemeMode> {
                    PreparationValidation.Valid
                },
        )

        val result = runtime.prepare(
            session = session,
            input = themeMode,
            spec = spec,
        )
        assertTrue(result is PreparationResult.Prepared<*>)
        return (result as PreparationResult.Prepared<PresentationThemeMode>).operation
    }
}
