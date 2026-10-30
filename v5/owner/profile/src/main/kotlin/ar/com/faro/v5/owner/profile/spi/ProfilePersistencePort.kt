package ar.com.faro.v5.owner.profile.spi

import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.profile.api.DeclaredDisplayName
import ar.com.faro.v5.owner.profile.api.ProfileSnapshot

interface ProfilePersistencePort {
    fun read(): ProfileSnapshot

    fun updateDeclared(
        displayName: DeclaredDisplayName,
        transactionScope: RealityTransactionScope,
    ): ProfileSnapshot
}
