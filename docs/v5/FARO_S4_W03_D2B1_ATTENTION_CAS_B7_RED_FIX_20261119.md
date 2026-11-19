# FARO V5 — W03-D2B1 B7 RED_FIX · Kotlin test import only
Fecha 2026-11-19. MISMA unidad S4_W03_D2B1_NOTICE_ATTENTION_CAS_DEV, B1–B5 vigentes docs/v5/FARO_S4_W03_D2B1_NOTICE_ATTENTION_CAS_B1_B5_20261119.md blob3c680487cf0d99f94fa17e6e3673a24d836626f3; permiso READY de cinco rutas emitido commit0132d53b9e94e48cb980c1446c7f0c6c77d02a8b y guard44583688238 SUCCESS. No repetir preauditoría de la misma unidad.

## B1
Code SHA 9ce50306d9f91285eb0e8ae29e241029de01a62f, guard 44584639847 SUCCESS. CI dirigida 44584639967 job135774159941 FAILURE: preflight passed, :store:auxiliary:test S4W03AuxiliaryNoticeAttentionCasTest 5/5 SUCCESS; después :app:compileDebugUnitTestKotlin Kotlin UNRESOLVED_REFERENCE en S4W03NoticeAttentionCasHostTest.kt línea 21, CanonicalPreparedPlanEnvelopeVerifier importado sólo vía kernel.contracts.*, pero su tipo está en ar.com.faro.v5.kernel.runtime. No hubo 3 tests app, no XML8/8. No hay evidencia de defecto de negocio en Auxiliary.

## B2
Comparar imports de S4W03ReminderNoticeReconcileHostTest.kt ya B7 PASS: usa explícitamente import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier para abrir misma raíz Finance9. Reutilizar exacto import, cero cambios de algoritmo, DB, owner policy, CAS, UI o OS.

## B3
Kotlin namespace/import determinista; no dependencia externa, paid API ni arquitectura nueva. Hipótesis falsable: con ese import, :app:compileDebugUnitTestKotlin resuelve símbolo y CI ejecuta los tres escenarios de integración.

## B4
ONE exact READY route v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W03NoticeAttentionCasHostTest.kt. Insertar únicamente import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier. Mantener intactos los demás cuatro archivos y tests ya verdes. New code commit FARO-UNIT:S4_W03_D2B1_NOTICE_ATTENTION_CAS_DEV, guard+XML.

## B5
Preflight una ruta contra READY parent; :store:auxiliary:test 5 y :app:testDebugUnitTest 3; XML 8/8, no skips, artifact. Si aparece fallo nuevo no asumir PASS, auditar log primero y RED_FIX acotado. No tocar W03-D2A, W01/W02, schema3, productivo ni OS. Writer RELEASED tras code; sin mantener lock durante CI.

## B7
RED_FIX solo en SHA 9ce50306d9f91285eb0e8ae29e241029de01a62f; compilación app FAIL, Auxiliary 5/5 PASS dirigido, tres apps NOT_RUN. Mantener permiso READY de la MISMA unidad; último código RED_FIX con esta evidencia y CI44584639967, B7 NO PASS hasta revisión dirigida completa.
