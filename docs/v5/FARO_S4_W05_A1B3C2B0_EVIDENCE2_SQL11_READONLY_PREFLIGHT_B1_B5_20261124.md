# FARO V5 · W05-A1B3C2B0 · Evidence2 SQL11 read-only cutover preflight · B1–B5
Fecha local: 2026-11-24. Repositorio etacsantafe-source/FARO-V5, rama work/s3-night-20261102.
Unidad S4_W05_A1B3C2B0_EVIDENCE2_SQL11_READONLY_PREFLIGHT_DEV.

## B1 — Evidencia real y límite
Punto de partida C2A B7 PASS scoped: código 25fb05a31dde9f692f3212f21aa21243b5c7cf21; CI 45514567419 intento 2 SUCCESS, Evidence2 6/6 y Evidence1 10/10 (XML original 16/16, artefacto 14976404131); guardia 45514567307 SUCCESS. Checkpoint documental 13d6f0d60eda1220efebed6c4355d499fcc14e42; guardia final 45515399089 SUCCESS. Writer RELEASED epoch 1911. C2A certifica solo FBS2v8 SQL11 STAGED, no cutover, NO stock.
El preflight existente FaroV5Household11DevCutoverPreflight es C0 Evidence1-only: rechaza comercial schema2, requiere stagedTyped null. C1A/C1B/C1C mantienen ticket, journal, recovery y ACTIVE Evidence1-only. No usar el C1 con Evidence2 como si estuviera certificado.
Objetivo ÚNICO: un opt-in explícito del preflight **READ_ONLY** para destino SQL11 Evidence2, sin emitir permiso de corte, ticket, journal, fence ni mover pointer. Esto es C2B0, NO C2B global.

## B2 — Reuso y comparación V4.4
Reusar C0 Evidence1 preflight, su validación de cinco archivos SQLite sellados sin sidecars, identidad FBS2v8 pin inmutable, origen/fuente distintos, host exclusivo, full pointer ABA, H10/H11R fila por fila, fingerprint de origen/target; reusar validador StagedSqlitePreflight sealed/immutable y su rechazo de triggers para typed Evidence2, además de la implementación C2A de SQL11 con schema2 typed. No repetir C2A ni 8 casos C0 como desarrollo.
Las matrices V4.4 P02/P03 de backup/recovery no prueban portabilidad Evidence2 ni autorizan copiar accesos, estados o negocio V4.4. No tocar V4.4/main ni las implementaciones C1A/C1B/C1C.

## B3 — Investigación externa, opciones y límite de evidencia
Fuentes primarias: SQLite WAL https://sqlite.org/wal.html (sidecars, checkpoint y riesgo de mirar copia incompleta); SQLite WAL format https://sqlite.org/walformat.html (archivos wal/shm); Android Room migration testing https://developer.android.com/training/data-storage/room/migrating-db-versions.html (testear versionado, sin recreación destructiva).
A ADOPTAR: ampliar el preflight actual con selector explícito inmutable expectedEvidenceSchema por instancia, por defecto 1, y validar la versión de evidencia con el FBS2v8 autenticado, reject schema2 sin typed, require staged SQL11 + schema2 commercial_evidence_quote_terms y sin triggers con StagedSqlitePreflight. Revalidar tras afterObservation y conservar pin/readback H10/H11R exacto.
B DESCARTAR: inferir schema2 por presencia de tabla o elegir un fallback tras error; no liga tipo a payload autenticado.
C DESCARTAR: usar openers writable/ACTIVE o desactivar seal guard para inspeccionar DB; pueden crear WAL y conferir falsa autoridad.
D POSPONER: autorización durable, ticket versionado, journal, C1B cutover, startup/Android y verificación semántica completa de términos mediante puerto read-only especializado. Esos límites son trabajo de C2B1/C2C, no puede habilitarse por este preflight.
Licencias/coste: se reutilizan SQLite/AndroidX y código existente del repositorio; sin API pagada, cloud ni paquete nuevo.

## B4 — Alcance exacto de B6, sin ampliaciones
1. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevCutoverPreflight.kt — default Evidence1 conservado, Evidence2 solo con selector explícito y archive versionado verificado, strict sealed SQL11 + commercial schema2, checks antes/después del seam.
2. v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2CutoverPreflightTest.kt — nuevo fixture original Evidence2 C2A, pruebas READ_ONLY y negativas; el Evidence1 C0 ya certificado permanece sin cambios y se vuelve a ejecutar como regresión.
3. .github/workflows/faro-v5-s4-w05-a1b3c2b0-evidence2-preflight.yml — workflow dirigido con preflight de las 3 rutas del padre READY, Gradle JVM, JUnit XML originales Evidence2 + ocho Evidence1, upload.
Las rutas de producción, Android, GraphOwner, C1A/B/C, main y V4.4 están EXCLUIDAS. Nuevas dependencias o cambios de ruta => STOP, nueva preauditoría y nuevo permiso.

## B5 — Auditoría adversarial previa y criterios de aceptación
T01 Evidence2 FBS2v8 original y SQL11 stage pasan solo con expectedEvidenceSchema=2; result metadata y pointer original inmutables, ningún ticket/journal/fence ni mutación de bytes.
T02 default 1 continúa admitiendo su Evidence1 y rechaza Evidence2; selector 2 rechaza Evidence1 (sin downgrade).
T03 Evidence2 exige commercial schema2, tabla quote_terms y ausencia de triggers; foreign sidecars, symlink/hardlink, corrupt schema fallan.
T04 wrong password, nonce cambiado, tamper MAC, archive no pin, source=target y pointer ABA/host revocado bloquean.
T05 H10/H11R drift o revisión falsificada bloquea, incluida mutación tras afterObservation.
T06 regresión Evidence1 C0 8/8, Evidence2 4 o más casos; zero failures/errors/skips, XML real, SHA exacto y protocol guard SUCCESS.
T07 Limitación crítica: comprobar schema SQL11+commercial schema2 y fingerprints de bytes NO equivale a autenticar la igualdad semántica completa de datos comerciales typed frente al archive; antes de C2B1 durable ticket y antes de C2C activar pointer, exigir verificador de términos sealed read-only (ABSENT/UNKNOWN/KNOWN) y pruebas adversariales propias. No certificar C2B global a partir de C2B0.
T08 Esta unidad no autoriza stock, movimientos, Apply, pagos, V2 productiva ni Auto/Noche.
**B1–B5 PREAUDITED; B6 NO IMPLEMENTED y B7 PENDING hasta permiso READY específico, guardia y CI/XML reales.**
