# FARO V5 · W05-A1A · Corrección de contador XML FBS1 en regresión S3 (B1–B5)

Fecha local: 2026-11-24. Unidad `S4_W05_A1A_S3_FBS1_XML8_REDFIX`. HEAD `47dc17876bd2713abcd16d2e1aa7ecf4e5435f0c`. Alcance **un único workflow histórico de CI**, cero cambios a pruebas, código productivo, schema/Aplicación, owner, writer, W04, S3 certificado, Chat, V2 productiva, móvil o Automations.

## B1 — Evidencia real y aceptación

La nueva unidad W05-A1A terminó 10/10 XML en `45389909485`, guard `45389909436` SUCCESS y cierre documental guard `45390672697` SUCCESS. Las regresiones V2 core `45389909755` y G4 `45389909361` fueron SUCCESS.

Una regresión adicional por cambios bajo `v5/store/reality/**`, `45389909487` (job `138430564134`), ejecutó las suites JVM **sin fallo** pero terminó FAILURE en el paso `Verify exact Reality receipt JUnit XML`. Log original del job:
- `S3_REALITY_RECEIPT_XML_PASS 3/3`;
- `S3_FINANCE_ACTIVITY_XML_PASS 3/3`;
- `S3_FBS1_RESTORE_XML_FAIL (8, 0, 0, 0)`.

El workflow `.github/workflows/faro-v5-s3-receipt-history-directed.yml` declara `('...SemanticBackupRestoreT2Test.xml', 4, 'FBS1_RESTORE')` en la lista `checks`. La clase `v5/maintenance/src/test/kotlin/ar/com/faro/v5/maintenance/SemanticBackupRestoreT2Test.kt` contiene ocho métodos `@Test`; XML real dice **8** con errores/failures/skips **0**. No es defecto de ejecución SQLite ni pérdida de backup: es una expectativa histórica de suite ampliada. La validación posterior FBS2 no llegó a emitirse por el fail-fast.

Criterio de aceptación: contador FBS1 debe aceptar **exactamente (8,0,0,0)**; conservar las demás expectativas y certificar que todo el workflow S3 queda SUCCESS con XML del mismo SHA. No reinterpretar un FAILURE como PASS.

## B2 — V4.4/V5 y reutilización

V4.4 no es propietario de este workflow ni de sus XML. Se conserva S3/X01 certificada y el validador XML histórico, que compara totales exactos por clase y exige 0 failures/errors/skips. No importar runtime V4.4 ni recompilar clases distintas. Usar sólo el delta a contador, sin repetir funcionalidades terminadas.

## B3 — Investigación de alternativas

Gradle documentación primaria `https://docs.gradle.org/current/userguide/java_testing.html` indica que `Test` genera XML JUnit por clase en `build/test-results/<task>`; esa evidencia refleja suites ejecutadas, no cantidad estable para siempre. Alternativa A elegida: actualizar el valor esperado **4→8** al XML exacto sin alterar otros predicados. B descartada: ignorar el contador con `>=4`, permitir skips, suprimir XML/CI o eliminar pruebas; perdería cobertura y señal de fallos. C descartada: volver a 4 pruebas o hacer rollback de suites válidas; repetiría trabajo certificado y ocultaría regresiones.

## B4 — Rutas y plan

**Ruta funcional única para READY**:
`.github/workflows/faro-v5-s3-receipt-history-directed.yml`.

Cambiar sólo `SemanticBackupRestoreT2Test.xml', 4, 'FBS1_RESTORE'` → `...xml', 8, 'FBS1_RESTORE'`, sin tocar `RealityBackupFilteringT2Test 3/3`, `RealityFinanceActivityReadT2Test 3/3`, `FaroV5FiveStoreSemanticCodecT2Test 7/7`, el trigger ni las pruebas. Guardia preflight padre READY, commit con FARO-UNIT, CI real y B7.

## B5 — Auditoría adversarial

- Una clase faltante, 7/8 o 9/8, fallas/errores/skips no cero, o XML corrupto debe continuar STOP.
- Cero cambios de lógica, modelos, tablas, datos, catálogo180, flags, Internet/egress, main/V4.4, V2 productiva o writer ajeno.
- Esta revalidación es **solo CI**; no activa stock, no mueve finanzas, no mejora ni invalida el scope W05-A1A B7 PASS.
- **PENDIENTE B6** hasta guard documental SUCCESS y permiso exact1 READY. En B7 verificar XML original y head_sha exacto. Si falla otro contador, documentar su causa, nunca ajustar a ciegas.
