# FARO V5 — W04-D2A · B7 RED_FIX2 2026-11-21

Unidad `S4_W04_D2A_OBSERVED_BUDGET_DEV`. HEAD del código corregido `863c002f997a135b35e9e837fc20e0fc6160d5e1` (única ruta de motor Kotlin) sobre B7 RED_FIX anterior `docs/v5/FARO_S4_W04_D2A_B7_RED_FIX_20261121.md`, checkpoint `e643e7626d4a25e002a2dd5671dad79181933bc4`. Guardia código `44899537465` SUCCESS.

## B7

**RED_FIX2, NO PASS**. CI dirigida `44899537390` FAILURE, job `136832330341`: el preflight real `FARO_PROTOCOL_PREFLIGHT_PASS S4_W04_D2A_OBSERVED_BUDGET_DEV e643e762...` sí pasó, pero el workflow impuso `test "$count" -eq 3` para un RED_FIX de **una sola ruta**, por lo cual el propio shell terminó exit 1 antes de ejecutar Gradle. JUnit/XML **NOT_RUN** y upload de XML ausente. No atribuir esto a compilación Kotlin ni reprogramar el motor.

### Corrección mínima autorizada
Modificar **únicamente** `.github/workflows/faro-v5-s4-w04-d2a-observed-budget.yml` dentro del alcance original exact3: tras preflight de cada ruta cambiada contra parent READY, aceptar subconjuntos de 1..3 rutas, con límites `test "$count" -ge 1` y `test "$count" -le 3`. El gate verifica rutas individuales, B1–B5 y B7 previo; el workflow conserva marcador `FARO-UNIT:S4_W04_D2A_OBSERVED_BUDGET_DEV`, target Java21, SDK37, JUnit5 12/12 exactos y artefacto. D0/D1/C2, motor Kotlin, tests, Gradle, Finance, SQL, UI, G15 permanecen intactos.

Antes del cambio: confirmar writer CAS, HEAD, documentación B7 RED_FIX2 y guardia documental; después commit funcional de una sola ruta, nueva guardia, ejecutar CI real y realizar auditoría B7 independiente con XML. Si falla, documentar B7 RED_FIX de la misma unidad; nunca declarar PASS sin evidencia. V2 productiva OFF.
