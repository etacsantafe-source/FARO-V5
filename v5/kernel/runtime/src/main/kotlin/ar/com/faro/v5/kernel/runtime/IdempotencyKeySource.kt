package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.IdempotencyKey

/**
 * Produces a new opaque identity for a new semantic intention.
 *
 * Retry/recovery must reuse the IdempotencyKey already frozen in the
 * PreparedOperation; it must not call this source again.
 */
fun interface IdempotencyKeySource {
    fun newKey(): IdempotencyKey
}
