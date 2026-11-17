package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * M1A: INTERNAL Finance9 policy identity. These are canonical strings received
 * from a FUTURE trusted Android host, not proof of a human or an Apply grant.
 * Never synthesize a principal from Chat/AI/Intent/Workspace APPROVED.
 */
internal data class Finance9AclScope(
    val principalHash: String,
    val generationId: String,
    val pointerSequence: Long,
    val operationId: String,
    val objectScope: String,
) {
    init {
        require(Regex("[0-9a-f]{64}").matches(principalHash))
        require(pointerSequence > 0)
        for (value in listOf(generationId, operationId, objectScope)) {
            require(value.length in 1..128 && value == value.trim() &&
                value.none { it.code < 32 || it.code == 127 })
        }
    }
}

/**
 * Finance9 M1A: STAGED-only SQLite primitive, never an authorization host.
 *
 * The caller supplies an already validated SQLite9 STAGED connection and
 * an independent generation/owner fence. This class cannot prove either
 * condition itself; the sandbox gate remains in the future M1B/M1C owner.
 * No active pointer, product Apply, Android, human authentication, grant,
 * execution receipt, finance ledger or backup is modified here.
 *
 * All policy issues/revocations serialize via SQLite BEGIN IMMEDIATE, not a
 * Kotlin monitor. The read method is informational ONLY: it must NOT be used
 * to authorize product Apply, because it is outside the final COMMIT.
 */
internal class RealityFinance9Acl(
    private val connection: SQLiteConnection,
    private val stagedAdmission: () -> Boolean,
    private val afterWriteBeforeCommit: () -> Unit = {},
) {
    private data class State(val next: Long, val revision: Long)
    private data class Policy(val epoch: Long, val revoked: Boolean, val expiresAtMs: Long)

    private fun scalar(sql: String): Long = connection.prepare(sql).use { st ->
        check(st.step()) { "Finance9 ACL scalar absent" }
        st.getLong(0)
    }

    private fun table(name: String): Boolean = connection.prepare(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?"
    ).use { st ->
        st.bindText(1, name)
        st.step()
    }

    private fun requireStage9() {
        check(stagedAdmission()) {
            "Finance9 policy SQL requires independent staged owner admission"
        }
        check(scalar("PRAGMA user_version") == 9L) {
            "Finance9 policy SQL never opens schema7, 8 or arbitrary versions"
        }
        check(scalar("PRAGMA foreign_keys") == 1L) {
            "Finance9 policy SQL requires enforced foreign keys"
        }
        check(table("finance_acl_state") &&
            table("finance_acl_policy") &&
            table("finance_apply_grant")) {
            "Finance9 ACL three-table schema incomplete"
        }
    }

    private fun state(): State = connection.prepare(
        "SELECT next_policy_epoch,policy_revision " +
            "FROM finance_acl_state WHERE singleton_id=1"
    ).use { st ->
        check(st.step()) { "Finance9 durable ACL epoch row absent" }
        val next = st.getLong(0)
        val rev = st.getLong(1)
        check(next > 0 && rev >= 0)
        check(!st.step()) { "Finance9 ACL state not singleton" }
        State(next, rev)
    }

    private fun policy(key: Finance9AclScope): Policy? = connection.prepare(
        "SELECT policy_epoch,revoked,expires_at_ms FROM finance_acl_policy " +
            "WHERE principal_hash=? AND generation_id=? AND pointer_sequence=? " +
            "AND operation_id=? AND object_scope=?"
    ).use { st ->
        st.bindText(1, key.principalHash)
        st.bindText(2, key.generationId)
        st.bindLong(3, key.pointerSequence)
        st.bindText(4, key.operationId)
        st.bindText(5, key.objectScope)
        if (!st.step()) null else Policy(
            st.getLong(0), st.getLong(1) == 1L, st.getLong(2),
        )
    }

    private fun advance(state: State) {
        check(state.next < Long.MAX_VALUE && state.revision < Long.MAX_VALUE) {
            "Finance9 policy epoch/revision overflow"
        }
        connection.prepare(
            "UPDATE finance_acl_state SET next_policy_epoch=?,policy_revision=? " +
                "WHERE singleton_id=1 AND next_policy_epoch=? AND policy_revision=?"
        ).use { st ->
            st.bindLong(1, state.next + 1L)
            st.bindLong(2, state.revision + 1L)
            st.bindLong(3, state.next)
            st.bindLong(4, state.revision)
            st.step()
        }
        check(this.state() == State(state.next + 1L, state.revision + 1L)) {
            "Finance9 ACL monotonic state update lost"
        }
    }

    private fun <T> transaction(block: () -> T): T {
        requireStage9()
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        return try {
            requireStage9()
            val value = block()
            afterWriteBeforeCommit() // stage-only fault seam for durable rollback
            requireStage9()
            connection.execSQL("COMMIT TRANSACTION")
            value
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
    }

    /**
     * Requires PRIOR external trusted admission which is NOT implemented by
     * M1A. This method creates NO Finance Apply grant and authenticates NO user.
     */
    fun issueAfterTrustedStageAdmission(
        key: Finance9AclScope,
        issuedAtMs: Long,
        expiresAtMs: Long,
    ): Long = transaction {
        require(issuedAtMs >= 0 && expiresAtMs > issuedAtMs) {
            "Finance9 policy cannot be born expired"
        }
        val before = state()
        check(before.next < Long.MAX_VALUE && before.revision < Long.MAX_VALUE) {
            "Finance9 ACL counter overflow"
        }
        connection.prepare(
            """
            INSERT INTO finance_acl_policy(
                principal_hash,generation_id,pointer_sequence,operation_id,
                object_scope,policy_epoch,revoked,expires_at_ms
            ) VALUES (?,?,?,?,?,?,0,?)
            ON CONFLICT(
                principal_hash,generation_id,pointer_sequence,operation_id,object_scope
            ) DO UPDATE SET
                policy_epoch=excluded.policy_epoch,
                revoked=0,
                expires_at_ms=excluded.expires_at_ms
            """.trimIndent()
        ).use { st ->
            st.bindText(1, key.principalHash)
            st.bindText(2, key.generationId)
            st.bindLong(3, key.pointerSequence)
            st.bindText(4, key.operationId)
            st.bindText(5, key.objectScope)
            st.bindLong(6, before.next)
            st.bindLong(7, expiresAtMs)
            st.step()
        }
        advance(before)
        check(policy(key) == Policy(before.next, false, expiresAtMs)) {
            "Finance9 policy issue not durable in staged SQL"
        }
        before.next
    }

    /**
     * Marks the old policy REVOKED, never deletes its row or recycles an epoch.
     * Repeated revoke is idempotent. Returns null for absent/foreign scope.
     */
    fun revoke(key: Finance9AclScope): Long? = transaction {
        val old = policy(key) ?: return@transaction null
        if (old.revoked) return@transaction old.epoch
        val before = state()
        check(before.next < Long.MAX_VALUE && before.revision < Long.MAX_VALUE)
        connection.prepare(
            "UPDATE finance_acl_policy SET policy_epoch=?,revoked=1 " +
                "WHERE principal_hash=? AND generation_id=? AND pointer_sequence=? " +
                "AND operation_id=? AND object_scope=? AND revoked=0 AND policy_epoch=?"
        ).use { st ->
            st.bindLong(1, before.next)
            st.bindText(2, key.principalHash)
            st.bindText(3, key.generationId)
            st.bindLong(4, key.pointerSequence)
            st.bindText(5, key.operationId)
            st.bindText(6, key.objectScope)
            st.bindLong(7, old.epoch)
            st.step()
        }
        advance(before)
        check(policy(key) == Policy(before.next, true, old.expiresAtMs)) {
            "Finance9 policy revoke did not persist"
        }
        before.next
    }

    /**
     * Observed snapshot of policy ONLY, never a usable Apply credential.
     * M1C must re-check inside the SAME Reality transaction as Apply COMMIT.
     */
    fun observedCurrentEpochOrNull(key: Finance9AclScope, atMs: Long): Long? {
        requireStage9()
        if (atMs < 0L) return null
        val policy = policy(key) ?: return null
        return policy.epoch.takeIf { !policy.revoked && policy.expiresAtMs > atMs }
    }

    fun observedCounterForStageTest(): Pair<Long, Long> {
        requireStage9()
        val value = state()
        return value.next to value.revision
    }
}
