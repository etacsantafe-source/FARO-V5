# FARO V5 · W05-A1A · B7 PASS de corrección XML S3/FBS1

Fecha local 2026-11-24. Unidad `S4_W05_A1A_S3_FBS1_XML8_REDFIX`.
Código `bf9cec694ce9367b063f6236f508b8c9fce7fd3b`; padre READY `6fa87dcd543aa2970aa0a759c1c5cefaa8f9e9f3`.
Alcance: **único contador del workflow histórico de S3**, sin modificación de tests, productivo, SQLite, owners, Android, V2 o catálogo.

## B1–B5 — Causa y guardias

Error previo `45389909487` (job `138430564134`): `SemanticBackupRestoreT2Test` terminó con XML real `(8,0,0,0)` pero el workflow exigía `(4,0,0,0)`; Reality history 3/3 y Finance activity 3/3 habían pasado. FBS2 quedó sin leer por fail-fast. No atribuirlo a W05-A1A SQL.
Preauditoría `docs/v5/FARO_S4_W05_A1A_S3_FBS1_XML8_REDFIX_B1_B5_20261124.md` blob `aa495fc1946849e8c2421b9b5228ad3ba5bb3f25`, guardia `45390973742` SUCCESS. Permiso READY exact1 publicado en `6fa87dcd543aa2970aa0a759c1c5cefaa8f9e9f3`, guardia `45391160088` SUCCESS. No se reabrió W05-A0/W04/S3 funcional.

## B6 — Cambio mínimo y evidencia original

Commit `bf9cec694ce9367b063f6236f508b8c9fce7fd3b` modifica **solo** `.github/workflows/faro-v5-s3-receipt-history-directed.yml`, en la expectativa `FBS1_RESTORE` **4→8**. Se mantienen nombres de clases, rutas, suite, fallo por XML ausente, conteos exactos, cero failures/errors/skips y FBS2 7.
Guardia B6 `45391327029` **SUCCESS**. GitHub Actions `45391329067`, job `138435514975`, SHA `bf9cec694ce9367b063f6236f508b8c9fce7fd3b`, **COMPLETED SUCCESS**. Gradle ambos pasos de tests **SUCCESS** y verificador de XML original **SUCCESS**, con:
- `S3_REALITY_RECEIPT_XML_PASS 3/3`
- `S3_FINANCE_ACTIVITY_XML_PASS 3/3`
- `S3_FBS1_RESTORE_XML_PASS 8/8`
- `S3_FBS2_SEMANTIC_XML_PASS 7/7`

**Total del workflow: 21/21**, 0 failures/errors/skips, validado por parser del job contra sus archivos XML. Esta acción no publicó un ZIP de pruebas; evidencia disponible en logs/jobs del run. No inventar artefactos.

## B7 — Revisión final independiente

**PASS_SCOPED_S3_RECEIPT_HISTORY_FBS1_XML8_REDFIX_21_21**.

Comprobado: cambio de un solo archivo y contador, tests base intactos, CI completa verde y guard funcional verde; la falla histórica del validador se corrigió sin debilitar pruebas negativas. No certifica materialización de recepción/stock real, migración ACTIVE Reality11, backup11/restore11, G15 ni V2 productiva. W05-A1A continúa B7 PASS staged 10/10, sin nuevo código.
El permiso exact1 se consume a BLOCKED; no usarlo para A1B. Siguiente unidad **W05-A1B** requiere B1–B5 propio y READY individual; avanzar por generación sellada/backup Reality11 sin abrir stock antes de recibos reales.
Writer debe quedar RELEASED tras checkpoint. Main/V4.4, S2/S3 certificados, pagos y Auto/Noche no cambiados.
