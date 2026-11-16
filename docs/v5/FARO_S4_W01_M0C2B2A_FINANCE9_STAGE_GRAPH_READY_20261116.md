# FARO V5 W01-M0C2B2A — READY exact-four Finance9 STAGED five-store graph
Fecha AR 2026-11-16, `S4_W01_M0C2B2A_FINANCE9_STAGE_GRAPH_ADMISSION`. HEAD preaudit `4361f7dc15d525fcdd8d7335f45b17319ac8e36f`, guard `43981110773` completed/SUCCESS. Writer previo RELEASED epoch999. B1–B5 fuente `docs/v5/FARO_S4_W01_M0C2B2A_FINANCE9_STAGE_GRAPH_B1_B5_20261116.md`, blob `5be11de02f705066c9d1fc50e5e87a787bd899b2`.
## B1
Reality7 default, Planning8 opt-in and existing Graph stage8; schema9 stage currently lacks opener. Owner Finance9 sealed snapshot 7/7 PASS.
## B2
Reusar RealityStore y GenerationGraph con opt-in STAGED9 sin editar defaults; lectura G4/Planning8 desde mismo SQLite9.
## B3
AndroidX SQLite bundled y preflight read-only de 5 stores, sin app ni red. No abrir schema9 como ACTIVE ni downgrade.
## B4
READY exactamente:
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9StageGraphAdmissionTest.kt
- .github/workflows/faro-v5-s4-w01-m0c2b2a-finance9-graph-directed.yml
CI JVM seis tests 6/6, XML por SHA exact.
## B5
Schema7/8/9 fences, pointer/descriptor/sidecars/extra columns, sin permisos Chat/Android. Existing B7 PASS suites sólo si delta de código exige regresión.
## B6
READY_TO_IMPLEMENT después del preflight exacto CAS writer, commit código distinto del permiso.
## B7
PENDING_FINAL_AUDIT: CI6/6 XML, guard, writer RELEASED, permiso consumido. Full capture/replay M0C2B2B sigue OPEN.
