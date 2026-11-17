# FARO V5 W01 B3B1B1B1A — checkpoint B6 / CI 22 de 23, B7 SIN VERIFICAR
Fecha AR: 2026-11-17. Unidad `S4_W01_B3B1B1B1A_FINANCE9_READ_ONLY_POLICY_ISSUER_DEV`; SHA código `8a4d79ca3a27e5d312345df0a1c50503749487e0`. **NO ES B7 PASS**.

## B1 — Recuperación
Preauditoría B1–B5 `docs/v5/FARO_S4_W01_B3B1B1B1A_SEPARATE_READ_POLICY_ISSUER_B1_B5_20261117.md` blob `22ba3a1875d7baf8ae75f66c7cfb3193a652bcb8`, guard `44109546378` SUCCESS. Auditoría B5 independiente + READY exact4 `docs/v5/FARO_S4_W01_B3B1B1B1A_READ_POLICY_INDEPENDENT_B5_READY_20261117.md` blob `bfe9c98671d5d6295ef9f4d43db39848621c2358`, guard `44109988029` SUCCESS. Preflight exacto: `FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_B3B1B1B1A_FINANCE9_READ_ONLY_POLICY_ISSUER_DEV 19400505f5c61ea5cab0e50f6e29979be256e6bb`.

## B2 — Implementado
Commit de código `8a4d79ca3a27e5d312345df0a1c50503749487e0` con marker FARO-UNIT exacto; sólo cuatro rutas READY:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyIssuerPort.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyIssuerTest.kt`
4. `.github/workflows/faro-v5-s4-w01-b3b1b1b1a-read-issuer-directed.yml`
Writer liberado epoch1096 antes de CI. Política SQL fija `finance.snapshot.read/finance:all`, TTL <=30s, epoch durable monotónico; NO Apply grants, receipts, UI, Android shipping, datos reales, S2/S3/main/V4.4, FBS2v4 o W2.

## B3 — Pruebas verificadas en este checkpoint
- CI dirigida `44110607488`, job `134134835286` COMPLETED SUCCESS sobre `8a4d79ca3a27e5d312345df0a1c50503749487e0`; nuevo JUnit XML **8/8 PASS**, regresiones afectadas B3B1A 8/8 y B3B1B0 8/8. Mensajes `W01_B3B1B1B1A_FIXED_READ_SQL_POLICY_8_OF_8_PASS` y `W01_B3B1B1B1A_B3B1A_8_AND_B3B1B0_8_REGRESSIONS_PASS`, cero failures/errors/skips. Artefacto JUnit `14340118142`.
- Guardia de código `44110607547` COMPLETED SUCCESS.
- En el control 2026-11-17 ~15:20 UTC: **22/23 workflows** de `8a4d79ca3a27e5d312345df0a1c50503749487e0` COMPLETED SUCCESS, 0 failures. **1/23 sin concluir**: `44110607195`, `FARO V5 W01 B3B1A staged Finance9 read policy observation`, todavía `in_progress`. NO atribuirle SUCCESS ni FAIL sin lectura remota final.

## B4 — Seguridad y límites
El issuer pertenece sólo a `RealityStore` STAGED9 DEV; el request puede ser falsificado por Kotlin caller y el reloj del stage no es reloj seguro de Android. La prueba real SQLite no certifica firma AndroidKeyStore, validación UID, consentimiento explícito para leer, invalidación entre procesos ni cero-latencia de revocación. El host real B3B1B1B1B y B3B2 G15 todavía STOP; W2 STOP, V2 productiva OFF.

## B5 — Próxima reanudación, sin duplicar
1. Revalidar HEAD, writer, permit y guard del checkpoint; no repetir pruebas ya SUCCESS.
2. Consultar específicamente el run GitHub `44110607195` por SHA de código `8a4d79ca3a27e5d312345df0a1c50503749487e0`; si SUCCESS, verificar 23/23 total y escribir B7 PASS scoped DEV; si FAILURE, diagnosticar CI real y aplicar RED_FIX de la misma unidad con permiso adecuado, nunca certificar falsamente.
3. Comparar diff exact4, JUnit XML 8 nuevos+16 afectados y ausencia de wiring; no duplicar otros workflows.
4. Writer CAS, commit B7 de documento y permiso consumido sólo tras auditoría final; liberar writer y comprobar guard de HEAD final.

## B6
IMPLEMENTED `8a4d79ca3a27e5d312345df0a1c50503749487e0`, directed 8/8 +16/16 PASS y 22/23 workflows SUCCESS (una ejecución PENDING).
## B7
**PENDING_EXTERNAL_CI_44110607195 — NO PASS**. Esta parada es exclusivamente de cierre/confirmación, no requiere permisos humanos nuevos ni trabajo de código a ciegas.
