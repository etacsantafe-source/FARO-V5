package ar.com.faro.v5.kernel.runtime

/** Canonical FARO V5 contract catalog materialized from the verified build-time baseline. */
object BuiltInContractCatalog {
    fun create(): ContractCatalog = GeneratedRuntimeContractCatalog.create()
}
