package ar.com.faro.v5.control.spi;

/**
 * Least-authority read-only view of device-local secret material.
 *
 * Provider adapters that only need a credential must depend on this interface instead of
 * receiving SecretStore mutation authority. Missing and unavailable material intentionally
 * collapse to null at this boundary; richer lifecycle diagnostics remain on SecretStore.
 */
@FunctionalInterface
public interface SecretReader {
    SecretValue readValueOrNull(SecretId id);
}
