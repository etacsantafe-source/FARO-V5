# FARO V5 W05-A1B3C2B1B — B7 PASS scoped · Evidence2 v2 durable PRE-CUTOVER authorization
Unidad `S4_W05_A1B3C2B1B_EVIDENCE2_SQL11_DURABLE_PRECUTOVER_AUTH_DEV`, rama `work/s3-night-20261102`, fecha local 2026-11-24/25 (Argentina).

## B1–B5 — Auditoría existente no repetida
Auditoría `docs/v5/FARO_S4_W05_A1B3C2B1B_EVIDENCE2_DURABLE_AUTH_B1_B5_20261125.md`, blob `eba84215d23e557518160e5d5c0b7c1ebed5d17d`, commit `440ebf0c4b48fd94d025cd6266ecf7fa78626981`, guard `45521383417` SUCCESS. Permiso READY exact3 `94e020ebdaa37074ad50531711604cdec3bd312c`, guard `45521524211` SUCCESS.
Reuso histórico C1A Evidence1 8 tests, no cambiar origen. C2B1A semántica tipada PASS XML20/20 y C2B0 PASS14/14, no duplicar.

## B6 — Implementación, RED_FIX y scope
Código inicial nuevo `07a6719b1e8c7290bd33b1e0c3d049b8e47f283d` (3 rutas exactas): owner `FaroV5Household11Evidence2DevCutoverAuthorization.kt`, test correspondiente y workflow de unidad. Ticket v2 exclusivo `household11-evidence2-dev-cutover-v2.bin`, identificador `FARO_H11_SQL11_EVIDENCE2_PRE_CUTOVER_AUTHORIZATION_V2`, root lock DEV, doble confirmación foreground, cotejo semántico C2B1A FBS2v8 autenticado, bind archivado al operationId/pointer, SHA256 y fsync durable, journal PREPARING → STAGED → VALIDATED con CAS, recuperador READ_ONLY sin permiso de corte. Rechaza ticket antiguo Evidence1 v1 simultáneo.
Primera CI `45521825103`, job `138895327117`, **FAILED** por Kotlin `UNINITIALIZED_PARAMETER` en test line185; producción `compileKotlin` sí compiló, pero `compileTestKotlin` falló ANTES de tests y **sin XML**. NO atribuir PASS a ese SHA.
RED_FIX B0 `docs/v5/FARO_S4_W05_A1B3C2B1B_REDFIX_B0_TEST_FIXTURE_45521825103_20261125.md`, blob `0608e9b93d730ead0f7b11c62ea4383a901144b0`: permiso exact2 test+workflow; guardia documental del primer permiso `45522346320` falló porque B6 tenía un valor no admitido `READY_RED_FIX_EXACT2`; corregido a `READY_TO_IMPLEMENT` en commit `b31d71c7f82babdf8e38b36d14b4ad7993ca548f`, guard `45522526301` SUCCESS. No se amplió alcance.
Código RED_FIX `e17aa76a41f2292ef32dbc1265d6a7882e7c42a1`: dos rutas, default `key` Kotlin explícito, fixture realmente restaurado SQL11 antes de autorizar, y v7 Evidence2 como entrada negativa (en lugar de códec v6); workflow preflight exact2. **Código funcional original NO cambiado en RED_FIX**. Writer CAS adquirido y liberado, epoch 1925.

## B7 — CI y XML real
- Guardia obligatoria SHA exacto `e17aa76a41f2292ef32dbc1265d6a7882e7c42a1`: workflow `45522706044` COMPLETED **SUCCESS**.
- CI dirigida SHA exacto, workflow `45522706065`, intento 1, job `138898289748`, COMPLETED **SUCCESS**; preflight parent READY **2/2 PASS**, Gradle `BUILD SUCCESSFUL in 3m 13s`.
- XML JUnit ORIGINAL verificado en el workflow: nuevo C2B1B Evidence2 durable **8/8**, regresión C1A Evidence1 durable **8/8**, regresión C2B1A Evidence2 semántica **6/6**; total **22/22**, failures=0 errors=0 skipped=0. Marcador real de log `FARO_W05_C2B1B_EVIDENCE2_DURABLE_AUTH_XML_PASS_22_22`.
- Artefacto XML original ZIP GitHub id `14981701295`, nombre `w05-a1b3c2b1b-evidence2-durable-authorization-xml`, tamaño 2452 bytes, digest reportado `sha256:329ae5cad92d1a66a228dea2cf1f1dad63eddf7b069d5608be489d5606519e1a`. No atribuir verificación externa del ZIP descargado.
- No se ejecutó campaña G15 física ni se afirma que todos los workflows heredados del repositorio estén verdes.

## Disposición
**B7 PASS SCOPED** para W05-A1B3C2B1B v2 Evidence2 durable PRE-CUTOVER DEV. El ticket y el journal pueden quedar VALIDATED de manera durable y reanudarse tras crash **sin cambiar el puntero** ni activar material writes. **NO** es certificación de cutover, rollback/reopen ACTIVE ni stock real.
El permiso de la unidad se consume/BLOCKED. Siguiente unidad con B1–B5 y READY propios: **C2C0** consumo del ticket Evidence2 v2, journaled generation switch/rollback/recovery, luego C2C1 Android/ACTIVE reopen y cierre A1 global. No reutilizar C1B Evidence1 v1 ni levantar V2 productiva. Stock real, pagos, Auto/Noche y Apply productivo OFF.
