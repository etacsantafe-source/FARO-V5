package ar.com.faro.v5.kernel.contracts

data class IoExecutionPolicy(
    val connectTimeoutMillis: Long,
    val readTimeoutMillis: Long,
    val totalTimeoutMillis: Long,
) {
    init {
        require(connectTimeoutMillis > 0L) {
            "connect timeout must be positive"
        }
        require(readTimeoutMillis > 0L) {
            "read timeout must be positive"
        }
        require(totalTimeoutMillis >= connectTimeoutMillis) {
            "total timeout must cover connect timeout"
        }
        require(totalTimeoutMillis >= readTimeoutMillis) {
            "total timeout must cover read timeout"
        }
    }
}

fun interface CancellationSignal {
    fun isCancelled(): Boolean
}

object NeverCancelled : CancellationSignal {
    override fun isCancelled(): Boolean = false
}
