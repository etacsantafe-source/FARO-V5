package ar.com.faro.v5.kernel.contracts

private fun requireOpaqueId(value: String, label: String) {
    require(value.isNotBlank()) { "$label must not be blank" }
}

@JvmInline
value class CapabilityId(val value: String) {
    init { requireOpaqueId(value, "capability id") }
}

@JvmInline
value class OperationId(val value: String) {
    init { requireOpaqueId(value, "operation id") }
}

@JvmInline
value class ExecutorId(val value: String) {
    init { requireOpaqueId(value, "executor id") }
}

@JvmInline
value class ContractVersion(val value: String) {
    init { requireOpaqueId(value, "contract version") }
}

@JvmInline
value class ContractFingerprint(val value: String) {
    init { requireOpaqueId(value, "contract fingerprint") }
}

@JvmInline
value class TransactionId(val value: String) {
    init { requireOpaqueId(value, "transaction id") }
}

@JvmInline
value class ReceiptId(val value: String) {
    init { requireOpaqueId(value, "receipt id") }
}

@JvmInline
value class EffectId(val value: String) {
    init { requireOpaqueId(value, "effect id") }
}

@JvmInline
value class TriggerId(val value: String) {
    init { requireOpaqueId(value, "trigger id") }
}

@JvmInline
value class IdempotencyKey(val value: String) {
    init { requireOpaqueId(value, "idempotency key") }
}


@JvmInline
value class PlanFingerprint(val value: String) {
    init { requireOpaqueId(value, "plan fingerprint") }
}

@JvmInline
value class PayloadFingerprint(val value: String) {
    init { requireOpaqueId(value, "payload fingerprint") }
}


@JvmInline
value class PlanNodeId(val value: String) {
    init { requireOpaqueId(value, "plan node id") }
}

@JvmInline
value class ModelProfileId(val value: String) {
    init { requireOpaqueId(value, "model profile id") }
}

@JvmInline
value class ModelBindingId(val value: String) {
    init { requireOpaqueId(value, "model binding id") }
}

@JvmInline
value class ModelRequestId(val value: String) {
    init { requireOpaqueId(value, "model request id") }
}

@JvmInline
value class PromptId(val value: String) {
    init { requireOpaqueId(value, "prompt id") }
}

@JvmInline
value class PromptFingerprint(val value: String) {
    init { requireOpaqueId(value, "prompt fingerprint") }
}
