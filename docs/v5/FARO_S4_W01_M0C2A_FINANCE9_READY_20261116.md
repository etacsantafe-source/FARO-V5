# FARO V5 W01-M0C2A — READY four scoped paths
Fecha AR 2026-11-16. Unidad S4_W01_M0C2A_FINANCE9_SEALED_CAPTURE. Reviewed HEAD 134021b49ea33d19bbd849f982cf5fb4693c817a. B1-B5 doc docs/v5/FARO_S4_W01_M0C2A_FINANCE9_SEALED_CAPTURE_B1_B5_20261116.md, blob dd97d5662cdc7500f3fc45fe767ebd0042df0fd9. Guard 43964431933 SUCCESS; writer RELEASED epoch991.
## B1
Fuente Finance9 staged real y ocho tablas (M0A/M0B) y Finance9SectionSnapshot F9S1 M0C1 6/6 PASS; no base 9 exportable aún.
## B2
Reusar StagedSqlitePreflight READ_ONLY, FileStorageGenerationLayout, pointer ID+sequence, F9S1 codec. No abrir RealityStore 7/8 para 9.
## B3
SQLite immutable read-only con validación de nombres de columnas, FK y UTF8, sin SQL de entradas ni red.
## B4
READY exclusivamente:
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9SealedReadPort.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9SealedSnapshotCapture.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9SealedCaptureTest.kt
- .github/workflows/faro-v5-s4-w01-m0c2a-finance9-capture-directed.yml
Tests siete: lectura real/ACL excluida, datos presupuestos/deudas, apertura UNKNOWN/null, target activo, descriptor/sidecar, pointer ABA y schema ajeno. 7/7 XML dirigido.
## B5
Captura sólo sección Finance9, no FBS2v4 completo ni restore/cutover, no ACL grants/sesiones, no productiva y M0C2B pendiente. Caso error FAIL CLOSED.
## B6
READY_TO_IMPLEMENT sólo tras preflight exact-path, writer CAS, código y permiso commits separados.
## B7
PENDING_FINAL_AUDIT, CI 7/7 + guard, writer RELEASED, permiso consumido y diff cuatro rutas.
