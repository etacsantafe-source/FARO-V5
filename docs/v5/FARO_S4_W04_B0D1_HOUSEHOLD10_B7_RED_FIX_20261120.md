# FARO V5 — B0D1 Fixture de bootstrap

## B7 — RED_FIX

Commit `106532c3222e5d268b9e6b014c7eb762dd5ee685`. Guardia 44726197307 SUCCESS. CI JVM 44726197350 falló al compilar el fixture, sin XML. El fixture usó `BootstrapPhase.PREPARED`; el journal real exige `PREPARING → STAGED → VALIDATED`, tal como `FaroV5Finance9DevStagePreparation`. Ajuste acotado al fixture y al conteo de rutas cambiadas en la CI para este RED_FIX. Reejecutar los seis casos antes del B7 PASS.