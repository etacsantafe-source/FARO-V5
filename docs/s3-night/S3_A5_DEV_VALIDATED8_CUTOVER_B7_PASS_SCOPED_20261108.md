# FARO V5 — S3.A5 DEV validated Reality8 canonical cutover/recovery — B7 PASS_SCOPED
Fecha 2026-11-08 AR. Unidad exacta `S3_A5_DEV_VALIDATED8_CUTOVER`, rama `work/s3-night-20261102`.

## B7 — Evidencia exacta y revisión independiente
- B1–B5 previos `docs/s3-night/S3_A5_DEV_VALIDATED8_CUTOVER_B1_B5_20261108.md` blob `58ff124393240303d62704ecff951d9a1b8e6af9`, permiso READY en padre `cc9d7d05b3a7342414ec4e4b5a5f8f4955743b18`. Cambio funcional exacto [`a7eeccdeed2f5f3bf36754d793221a4c05ad788f`](https://github.com/etacsantafe-source/FARO-V5/commit/a7eeccdeed2f5f3bf36754d793221a4c05ad788f), trailer `FARO-UNIT:S3_A5_DEV_VALIDATED8_CUTOVER`.
- Comparación GitHub `cc9d7d..a7eeccde`: tres archivos nuevos exclusivamente: `FaroV5Planning8DevCutoverOwner.kt` (+263 líneas), `FaroV5Planning8DevCutoverOwnerT2Test.kt` (+131 líneas), workflow `faro-v5-s3-a5-dev-validated8-cutover-directed.yml` (+57 líneas). No se modificaron el StageOwner ya certificado, los coordinadores de Maintenance, Activity, Manifest, esquema activo, Apply/Undo, S2/S4, V4.4 o main.
- CI dirigida [#42531064127](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42531064127), job `128885134495`, **COMPLETED SUCCESS** SHA funcional exacto, `BUILD SUCCESSFUL in 2m 28s`, JUnit XML tests=4 failures=0 errors=0 skipped=0. Marcador de log `S3_A5_DEV_VALIDATED8_CUTOVER_REAL_SQLITE_XML_PASS 4/4`.
- Guardia de siete bloques [#42531064175](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42531064175), job `128885134752`, **SUCCESS** mismo SHA; `FARO_PROTOCOL_GATE_PASS 110 functional units validated`, `FARO_PROTOCOL_PREFLIGHT_PASS S3_A5_DEV_VALIDATED8_CUTOVER cc9d7d05...`. La CI de regresión V2 core se observa separadamente; no confundir un resultado pendiente con PASS.

## Alcance verificado con cuatro SQLite integration tests nuevos
1. El productor journal A5 real prepara cinco bases STAGED Reality8 con RESTORE/VALIDATED; el adaptador entrega la operación al coordinator canónico: puntero Reality7→Reality8, journal COMPLETED, materialWritesAccepted y replay fence durables; Reality7 original no se altera, ACTIVE8 reabre con exactitud.
2. Interrupción inyectada tras publicación de pointer, antes de avanzar journal: estado SWITCH_PREPARED, sin fence antes de recuperar; recuperación con el MISMO journal lleva a COMPLETED, instala fence y no vuelve a publicar puntero.
3. Fallo de salud del objetivo Reality8 devuelve autoridad a Reality7 antes de admitir escrituras, puntero secuencia anterior+2, journal ROLLBACK_VERIFIED y fence target ausente.
4. Host sin certificado y ABA de pointer ID antiguo con secuencia distinta rechazan el cutover antes de modificar el journal VALIDATED.

## Dictamen, límites y siguiente unidad
- **PASS_SCOPED_DEV_VALIDATED8_REAL_JOURNAL_CUTOVER_RECOVERY_4_OF_4**. Cuarta subunidad S3.A5 certificada; las tres anteriores permanecen aprobadas y no se repiten.
- Este puente todavía NO está conectado a `FaroV5V2CoreDevAuthority` ni a `FaroV5V2CoreDevActivity`; la instalación DEV actual sigue con su ruta Reality7 histórica. No se conceden sesiones de usuario, ACL, Apply ni Undo; el booleano `certifiedHost` no es autorización humana. No hubo cutover del G15, tampoco prueba física. **S3.A5 sigue OPEN.**
- El próximo B1–B5 independiente deberá enlazar DEV host de Reality8, revocar sesiones Reality7 por secuencia de pointer, admitir lectores de Pendientes por principal/política real solo tras interacción humana y comprobar start/recovery. La campaña física única X01–X12 queda al final. V2 productiva, S4, Auto y Noche OFF. Sin APIs pagas.
