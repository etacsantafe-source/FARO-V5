# FARO V5 · W05-A1B3C0 · B7 PASS SCOPED · SQL11 cutover preflight READ_ONLY

Fecha 2026-11-24 Argentina. Rama `work/s3-night-20261102`.
Unidad `S4_W05_A1B3C0_FBS2V8_SQL11_CUTOVER_PREFLIGHT_DEV`.

## B1–B5 — Etapas certificadas y permiso

Entrada: checkpoint `bf6c56b97c0e94777ad584435cf0af6c1f4542f0`, A1B3B1 B7 PASS FBS2v8 Evidence1 STAGED9→SQL11 H10/H11R, XML10/10; writer RELEASED epoch1862 y permiso de B1 consumido.
Documento B1–B5 `docs/v5/FARO_S4_W05_A1B3C0_SQL11_CUTOVER_PREFLIGHT_B1_B5_20261124.md`, blob `8e61f2d140fd2ee2af8197d6b3bfae409d7943fe`, commit `8ade67e7305fb1a256bb1202aa70a1dfe218d1e9`, guard `45486561982` COMPLETED SUCCESS.
Permiso READY exact3 commit `19054a0aebbe7ff79d446b946b8a19cd2a9e9db8`, guard `45486705472` COMPLETED SUCCESS. No repetición de W1–W4, A1B3A, A1B3B0, A1B3B1.

## B6 — Código y pruebas reales

Código único `178c9edd908feae51b2ac7af430de4213f7307e7`, exactamente tres rutas:
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevCutoverPreflight.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevCutoverPreflightTest.kt`
- `.github/workflows/faro-v5-s4-w05-a1b3c0-cutover-preflight.yml`

Guardia de código `45487014427` COMPLETED SUCCESS. CI propia `45487014676`, job `138774983533`, código SHA exacto, COMPLETED SUCCESS. Preflight READY parent exact3 PASS para las tres rutas. `BUILD SUCCESSFUL in 3m 9s`; marcador workflow `FARO_W05_A1B3C0_SQL11_CUTOVER_READ_ONLY_XML_PASS_8_8`.

Artifact GitHub original `14963103988`, `w05-a1b3c0-sql11-cutover-preflight-xml`, ZIP 829 bytes, SHA256 GitHub `dcb63ae05d565226508dab7b738932f926442323d76cfaddb7a383323b8d1f42`. ZIP descargado, SHA256 recomputado y coincidente, único XML `TEST-ar.com.faro.v5.generation.FaroV5Household11DevCutoverPreflightTest.xml`. Análisis directo `xml.etree.ElementTree`: **tests=8, failures=0, errors=0, skipped=0**, ocho elementos `testcase`, cero `failure/error`; duración JUnit reportada 10.196 segundos.

## B7 — Revisión independiente y alcance

**B7 PASS SCOPED SQL11 EVIDENCE1 AUTHENTICATED READ_ONLY CUTOVER_PREFLIGHT XML8/8.**

Comprobado en JVM DEV aislada, sin cambiar SQLite ni punteros:
1. Etapa SQL11 completamente restaurada desde FBS2v8: pin durable preexistente del **ciphertext original**, origin/target distintos, puntero ID+sequence exacto, cinco stores WAL sellados, fingerprint estable origen/target, SQL11 Household10 y recepciones H11R auténticas y revisiones/filas exactas. La inspección no crea journal, ticket, replay fence, autorización ni ejecuta cutover.
2. Household10 y H11R **revised-empty** conservan revisión sin inventar stock.
3. Wrong password, ciphertext/tag alterado, v6 antiguo o archivo con nonce distinto se rechazan: ausencia de fallback y de nueva identidad durable.
4. H10 o H11R alterados después de stage restore se rechazan aunque el archivo original sea auténtico.
5. Host cerrado revocado y pointer ABA detectados al revalidar después de observación.
6. Journal existente sin resolver, hardlink/sidecar y replay-fence ya instalado bloquean prevalidación sin tomar autoridad de activación.
7. Byte-for-byte observación sin mutación de cinco stores durante el preflight. Guard y CI con XML original PASS.

**No autorizado ni probado por C0:** cambiar `activePointer`, crear/transicionar `FileBootstrapJournal`, ticket durable, fence material-write, seleccionar SQL11 como activo, abrir graph SQL11 en Android, startup crash recovery, rollback tras switch, Evidence2 typed, producción V2, Apply del stock ni pagos. Household10 cutover anterior es incompatible con SQL11 por sus validadores, y no se reutilizó saltándolos.

Siguiente **W05-A1B3C1**: implementación separada del cutover SQL11 journaled con runtime/owner/reopen/recovery y fallas inyectadas. Exigir propio B1–B5, guard SUCCESS, READY exact routes, CI/XML, B7 y writer RELEASED; luego A1C Android Stock Review/Apply/Undo humano si corresponde, nunca automáticamente.

V2 productiva, Auto/Noche, pagos y stock real OFF. Se consume READY, se actualiza puntero de continuidad y se libera writer con última guardia SUCCESS.
