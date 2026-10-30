package ar.com.faro.v5.owner.profile.api

import ar.com.faro.v5.owner.profile.spi.ProfilePersistencePort

class ProfileReader(
    private val persistence: ProfilePersistencePort,
) : ProfileReadApi {
    override fun read(): ProfileSnapshot = persistence.read()
}
