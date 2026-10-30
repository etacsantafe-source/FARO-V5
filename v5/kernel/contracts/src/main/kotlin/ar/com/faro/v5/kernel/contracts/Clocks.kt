package ar.com.faro.v5.kernel.contracts

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

interface CivilClock {
    val zoneId: ZoneId
    fun now(): Instant

    fun nowZoned(): ZonedDateTime = now().atZone(zoneId)
}

fun interface MonotonicClock {
    fun readNanos(): Long
}
