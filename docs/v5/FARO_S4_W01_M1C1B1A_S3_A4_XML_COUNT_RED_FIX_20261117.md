# W01 M1C1B1A — B7 RED_FIX regresión S3 A4 XML exact13
Fecha AR 2026-11-17, unidad S4_W01_M1C1B1A_POST_RECEIPT_FINAL_COMMIT_GATE. Código inicial 6b6b828a6f5daca0731995c45a1b53024bcd07d6, guard código 44034166691 SUCCESS, CI dirigida 44034166788 SUCCESS con 19/19 JUnit, seis nuevos + trece anteriores. Writer RELEASED epoch1033 antes de CI.

## B7 — RED_FIX scoped, no PASS global
Regresión automática S3 A4 run 44034166682 FAILURE únicamente en paso "Check exact JUnit XML and zero failures/skips": el log dice **A4_TXN_HOOK_XML_FAIL (19,0,0,0)**. Su compilación Kotlin y ejecución Gradle pasaron, cero fallas/errores/skips; el workflow legado '.github/workflows/faro-v5-s3-a4-n033c2b4-txn-hook-directed.yml' verifica EXPLÍCITAMENTE EXACT 13 casos en la clase ApplyTransactionExecutorTest; el código de esta unidad agregó 6 casos a esa misma clase, cambiando a 19. No es una falla del SQL ni del final-commit hook. **NO tocar ni desactivar ni relajar el workflow S3 ya certificado**.
Corrección autorizable DENTRO de M1C1B1A y sus rutas exact3 (sólo dos archivos efectivamente se modifican):
- Mover los seis D01–D06 nuevos a clase test separada `ApplyTransactionFinalCommitGateTest` en el MISMO archivo autorizado `ApplyTransactionExecutorTest.kt`, dejar trece pruebas S3 originales en `ApplyTransactionExecutorTest` intactas. Compartir fixture interno del archivo mediante visibilidad test-only, sin cambios funcionales ni producción.
- Ajustar solamente workflow nuevo `.github/workflows/faro-v5-s4-w01-m1c1b1a-final-commit-gate-directed.yml` para ejecutar las dos clases y verificar DOS XML: S3 13/13 más M1C1B1A 6/6, ningún error/skip/falla.
- Mantener `ApplyTransactionExecutor.kt` EXACTAMENTE igual al código inicial 6b6b828a6f5daca0731995c45a1b53024bcd07d6; no reabrir source code ni M1C1B0 certificado.
- Revalidar guard GitHub exact SHA, workflow M1C1B1A 19 agregados, workflow S3 A4 13 intactos, writer CAS RELEASED. Red fix no permite ampliar 3 rutas, cambiar permisos, Android/Finance productiva ni W2.
- Antes de la corrección estado B7 RED_FIX_BOUNDED_SAME_UNIT; después B7 PASS sólo si ambas suites y regresiones vuelven verdes.

## B6
Se permite única corrección de test class layout + CI dentro de READY M1C1B1A vigente, no código productivo.

## Checkpoint
Permiso continúa READY exact3 bajo RED_FIX, writer de esta auditoría epoch1034 se libera antes del siguiente código.
