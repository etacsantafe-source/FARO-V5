package ar.com.faro.v5.app;

import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph;
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate;
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort;
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier;
import ar.com.faro.v5.maintenance.StagedStorageGeneration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/** Quarantined staging only; cannot open the currently active generation. */
final class FaroV5QuarantinedV2GraphAdmission {
    private final MaterialWriteGate gate;
    private final PreparedPlanEnvelopeVerificationPort verifier;
    private final Supplier<Path> activeDirectory;

    /** Only the trusted runtime verifier is allowed in the host's staged V2 factory. */
    static FaroV5QuarantinedV2GraphAdmission canonical(
            MaterialWriteGate gate,
            Supplier<Path> activeDirectory) {
        return new FaroV5QuarantinedV2GraphAdmission(
                gate, CanonicalPreparedPlanEnvelopeVerifier.INSTANCE, activeDirectory);
    }

    FaroV5QuarantinedV2GraphAdmission(
            MaterialWriteGate gate,
            PreparedPlanEnvelopeVerificationPort verifier,
            Supplier<Path> activeDirectory) {
        this.gate = Objects.requireNonNull(gate);
        this.verifier = Objects.requireNonNull(verifier);
        this.activeDirectory = Objects.requireNonNull(activeDirectory);
    }

    FaroV5GenerationStoreGraph open(StagedStorageGeneration staged) {
        Objects.requireNonNull(staged);
        try {
            Path active = Objects.requireNonNull(activeDirectory.get()).toRealPath();
            Path target = staged.getDirectory().toRealPath();
            if (active.equals(target)) {
                throw new IllegalStateException("staged V2 cannot reopen active authority");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("cannot prove staged/active separation", failure);
        }
        return FaroV5GenerationStoreGraph.openValidatedV2WithSharedGate(
                staged, verifier, gate);
    }
}
