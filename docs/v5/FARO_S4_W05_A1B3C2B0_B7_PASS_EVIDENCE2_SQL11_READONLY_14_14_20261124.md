# FARO V5 · W05-A1B3C2B0 · B7 PASS SCOPED Evidence2 SQL11 READ_ONLY · 2026-11-24
Rama `work/s3-night-20261102`, unidad `S4_W05_A1B3C2B0_EVIDENCE2_SQL11_READONLY_PREFLIGHT_DEV`.

## B1–B5 — Evidencia conservada
Preauditoría `docs/v5/FARO_S4_W05_A1B3C2B0_EVIDENCE2_SQL11_READONLY_PREFLIGHT_B1_B5_20261124.md`, blob `56c72f9c80159ba620f61e3c38c1d4b0f30e2cf0`, checkpoint `b759bbef7e8695dfceb0279a94bb8424e75e7688`, guard `45517528045` SUCCESS. Permiso exact3 READY parent `96b36ff6d9b8a70f83a5927b6681f49e5a184f54`, guard `45517662951` SUCCESS.
C2A ya B7 PASS scoped, XML 16/16. C0 Evidence1 ya certificado, ahora reprobado como regresión sin reescribir test C0.

## B6 — Implementación limitada a 3 rutas
Commit `957a67f9462c2d7a6126d0ca6f0bb8d4b00278ad` (padre READY):
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevCutoverPreflight.kt`: selector explícito expectedEvidenceSchema, default Evidence1; en schema2 se liga la versión al FBS2v8 autenticado y se exige STAGED SQL11 + Comercial schema2 con quote_terms y sin triggers mediante inspector SQLite inmutable/read-only. Revalida tras observación; mantiene pin, host cerrado, pointer ABA, H10/H11R y sello de cinco archivos. No abre ACTIVE, no crea ticket.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2CutoverPreflightTest.kt`: seis pruebas de C2B0 con origen Finance9 Evidence1 y archivo FBS2v8 Evidence2 real restaurado hasta SQL11, verifica no mutación, rechazo por default Evidence1, passphrase/nonce, trigger adversarial, host/ABA, H11R drift.
3. `.github/workflows/faro-v5-s4-w05-a1b3c2b0-evidence2-preflight.yml`: Gradle Java21 + SDK37, preflight exact3 contra el parent, 6 C2B0 + 8 C0, parser JUnit original y subida de XML.
Writer de código adquirido/liberado CAS epoch 1914; sin lock durante CI.

## B7 — Verificación independiente
- Guardia obligatoria workflow `45517890838` **COMPLETED SUCCESS** para SHA `957a67f9462c2d7a6126d0ca6f0bb8d4b00278ad`.
- CI dirigida workflow `45517890891`, intento 1, job `138881968335`: **COMPLETED SUCCESS**, Gradle `BUILD SUCCESSFUL in 3m 54s`.
- Preflight de las tres rutas contra permiso READY padre **3/3 PASS**.
- XML JUnit original validado por workflow: **Evidence2 6/6 + Evidence1 8/8 = 14/14**, failures0 errors0 skipped0, marker real de log `FARO_W05_C2B0_EVIDENCE2_READONLY_SQL11_XML_PASS_14_14`.
- Artefacto GitHub `14978132543`, nombre `w05-a1b3c2b0-evidence2-sql11-readonly-xml`, 1590 bytes. Digest del ZIP reportado por GitHub: `sha256:9a86c6b7c556224cb1b947c33da0fc0132ee55339cde32dca593f7b94d871082`. No atribuir verificación de hash independiente no ejecutada.
- **Excepción histórica:** el workflow C0 de Evidence1 `45517890802` se activó por ruta compartida y falló SOLO en el preflight de permiso de la unidad C0 anterior, antes de sus tests; no indica fallo funcional de C2B0 y tampoco constituye CI global verde. El workflow dirigido C2B0 sí ejecutó los ocho casos C0 y los aprobó.

## Disposición y frontera
**B7 PASS SCOPED ONLY: C2B0 Evidence2 SQL11 STAGED READ_ONLY PREFLIGHT, XML 14/14 y guard SUCCESS.**
La validación formal del schema SQL11 y la inspección de integridad NO autentican todavía la igualdad semántica completa de las filas/términos comerciales typed contra el archivo cifrado original. C2B1 debe agregar un puerto sellado READ_ONLY que contraste ABSENT/UNKNOWN/KNOWN, condiciones, fechas y promociones con el snapshot FBS2v8, seguido de autorización durable propia con ticket y journal versionados. C2C debe certificar cutover/recovery/ACTIVE WAL reopen; no heredar C1B/C1C Evidence1. W05-A1 global continúa OPEN.
**No se activó ningún stock real, Apply, pagos, V2 productiva ni Auto/Noche. No hay prueba física Moto G15.**
