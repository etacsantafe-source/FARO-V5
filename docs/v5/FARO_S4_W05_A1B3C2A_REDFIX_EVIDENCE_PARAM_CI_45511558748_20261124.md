# FARO V5 · W05-A1B3C2A · RED_FIX Kotlin evidence selector + workflow scope

2026-11-24, rama work/s3-night-20261102. Misma unidad S4_W05_A1B3C2A_FBS2V8_EVIDENCE2_SQL11_STAGED_REPLAY_DEV. A1 global OPEN, V2 productiva/Auto/Noche/pagos/stock real OFF.

## B1 — Error real de CI

SHA funcional ff7db63b86f58b0413b69a8948b9f3994fcc8583, guardia 45511558589 COMPLETED SUCCESS y preflight EXACT3 SUCCESS; CI Evidence2 45511558748, job 138860277489, FAILURE en Gradle compileKotlin ANTES de ejecutar JUnit. Diagnóstico original del log Kotlin NO_VALUE_FOR_PARAMETER, FaroV5Household11V8FullStageReplay.kt línea 189: NO se pasó parámetro evidence a requireEleven. Las dos llamadas previas sí reciben evidenceSchema, la llamada final tras RECEIPTS11_RESTORED era residual. CI sin XML de las 16 pruebas planificadas, ningún B7 funcional atribuible. El workflow previo de B3B1 se disparó por archivo común y falló en SU preflight histórico de cuatro rutas, sin ejecutar pruebas; no se infiere regresión de B3B1.

## B2 — Reuso y comparación

El mismo B0 certificado A1B3B0 soporta Evidence2 y los 10 tests B3B1 Evidence1 deben ejecutarse sin editar código previo; V4.4 auditado P02/P03 carece de contrato typed Evidence2. Los selectores perfil1/perfil2 se derivan del FBS2v8 autenticado y deben aplicarse al mismo validator final, no hacer downgrade ni duplicar clases. Mantener exactamente cinco stores STAGED.

## B3 — Alternativas auditadas

A ADOPTAR: propagar evidenceSchema a la llamada final requireEleven(stage,evidenceSchema), sin otras alteraciones de comportamiento. A fin de que la CI del RED_FIX realmente ejecute JUnit, permitir exact3 de primer código o exact2 de RED_FIX en el contador local del workflow (se puede aceptar exact1 para potencial corrección dentro de la misma unidad) y mantener el preflight individual contra el permiso del HEAD padre. B DESCARTAR: dar valor predeterminado evidence=1 en requireEleven, ocultaría Evidence2 y violaría fail-closed. C DESCARTAR: eliminar validación final o recortar CI a Evidencia1, invalidaría aceptación. D DESCARTAR: tocar C1A/C1B/C1C por fallo local.
B3 externo del original se mantiene; el problema actual lo determina log de compilador y código, no fuente externa.

## B4 — Rutas exactas RED_FIX2, sin ampliar dominio

1. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8FullStageReplay.kt — reemplazar SOLO la llamada requireEleven(stage) por requireEleven(stage, evidenceSchema) tras H11R.
2. .github/workflows/faro-v5-s4-w05-a1b3c2a-evidence2-stage.yml — contador rutas exact1/2/3 (no wildcard) y conservar loop preflight por cada ruta, Gradle y XML assertions. Test nuevo y suite B3B1 permanecen byte-identical.

## B5 — Auditoría adversarial RED_FIX

R01 :store:generation:compileKotlin compila las tres validaciones SQL11 con evidencia explícita. R02 no existe fallback evidence=1, ni Evidence2 convertido a perfil1. R03 preflight de parent READY exact2 PASS en CI y cada ruta autorizada; cualquier ruta ajena STOP. R04 nueva suite Evidence2 seis tests y regresión Evidence1 diez tests, XML originales 16/16, cero skips/failures/errors. R05 el código previo NO se declara PASS sin CI; si Kotlin u otro contrato falla, registrar nuevo RED_FIX con evidencia. R06 sin Android productivo, cutover, stock Apply, pagos, Auto/Noche.

## B7 — Disposición del SHA funcional anterior

ff7db63b86f58b0413b69a8948b9f3994fcc8583 → RED_FIX_COMPILE_NO_VALUE_FOR_PARAMETER_EVIDENCE; no B7 PASS. Guardia 45511558589 SUCCESS pero CI 45511558748 FAILURE; sin XML. Preauditoría original docs/v5/FARO_S4_W05_A1B3C2A_EVIDENCE2_FBS2V8_SQL11_STAGE_B1_B5_20261124.md y guard 45510975074 SUCCESS siguen vigentes. El permiso de esta MISMA unidad debe marcar last_code_review SHA exacto RED_FIX con evidencia B7 de este documento, READY solo dos rutas y guardia SUCCESS antes de un nuevo commit funcional. Nunca reutilizar READY original sin registrar disposición. Después B6/CI/B7 originales; A1 global OPEN y stock OFF.
