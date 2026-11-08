# FARO V5 S3.A4 · N033C2B4 GRANT_COMMIT_LEASE · legado CI XML RED_FIX · B1–B5 + B7
Fecha AR 2026-11-07. Código de unidad 986905d856f62edc2f00ef4ffefd43438b79b516. Esta corrección no toca source ni pruebas Kotlin, sólo el verificador histórico desfasado. S3.A4 completo continúa OPEN.

## B1 — Evidencia exacta del rojo y del verde
- Nuevo código 986905d856f62edc2f00ef4ffefd43438b79b516; guard #42428854264 SUCCESS; CI dirigida #42428854363 SUCCESS, job #128573022973, XML exacto 18/18 GrantOwner + 14/14 Review + 10/10 Search + 2/2 Flow = 44/44, 0 failures/errors/skips, `A4_GRANT_COMMIT_LEASE_PASS 44/44`.
- CI histórica #42428854284 FAILED SOLO en el paso XML tras Gradle BUILD SUCCESSFUL; log: `N033C2B4_GRANT_XML_FAIL FaroV5Planning8ObjectGrantOwnerT2Test (18, 0, 0, 0)`. El workflow sigue esperando 14 y 40 total para 14 tests históricos; no es bug de código, no reescribir la suite.
- Archivo exacto: `.github/workflows/faro-v5-s3-a4-n033c2b4-session-grant-directed.yml`. Es el único archivo que requiere ajuste, porque cualquier cambio de owner/test dispara dos workflows idénticos.

## B2 — Reuso V4.4 y V5; sin repetir ensayos
- V4.4 no contiene solución aplicable a conteos CI de V5. No copiar o ejecutar código antiguo.
- Reusar el XML 44/44 del flujo nuevo ya comprobado, conservar el workflow anterior como registro histórico/manual; nunca rebajar expected tests o aceptar skips. Hacer que las futuras modificaciones de owner/test ejecuten sólo el flujo principal 44/44.
- Sin cambios en `RealityStore`, `ApplyTransactionExecutor`, owner ni pruebas, sin reabrir S2 o código de V4.4.

## B3 — Investigación y criterio
- GitHub Actions path filters: https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onpushpull_requestpull_request_targetpaths — ADAPTAR filtro para que el flujo histórico se dispare en push SOLO cuando cambie su propio YAML, no cuando cambie owner/test cubiertos por el workflow nuevo.
- GitHub Actions triggers workflow_dispatch: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow — CONSERVAR dispatch para auditorías manuales, nunca usarlo para relanzar inútilmente la suite.
- JUnit XML strict: mantener conteo `18+14+10+2=44` sin relajar fallos/errors/skips. DESCARTAR condicionar éxito a `>=14` y descartar deshabilitar test.
- Otra alternativa es eliminar el workflow legado; DESCARTAR porque preservamos trazabilidad histórica y control manual.

## B4 — Delta único aprobado
1. Modificar sólo `.github/workflows/faro-v5-s3-a4-n033c2b4-session-grant-directed.yml`: quitar dos `paths` de sources/tests, retener su propio YAML como disparador y `workflow_dispatch`.
2. En el check histórico actualizar expected GrantOwner de 14 a 18, total 40 a 44, etiquetas y salida `44/44`.
3. El workflow principal `faro-v5-s3-a4-n033c2b4-grant-commit-lease-directed.yml` conserva trigger owner/test y XML 44/44; su CI PASS por SHA anterior no se repite a mano.
4. Nuevo commit funcional debe incluir `FARO-UNIT:S3_A4_N033C2B4_GRANT_COMMIT_LEASE`; B7 después de CI exacta workflow viejo y guard, con writer RELEASED durante espera.

## B5 — Auditoría adversarial y límites
- Verificar que el flujo histórico sigue ejecutable por su propio YAML y que cualquier regresión del owner dispara el flujo principal. Un cambio sólo en workflow viejo NO debe disparar el flujo nuevo. No desactivar guard obligatorio.
- Test real de grant desde app aún no está montado; nuevo método sólo linealiza revoke/revokeAll/close de su owner durante el callback, no mutaciones externas de policy/pointer ni otros procesos. No marcar A4 completo ni activar V2.
- No introducir redfix de otras suites no observadas, no relanzar CI sin cambio ni crear política falsa. Estado S4 OFF, Auto/Noche OFF, catálogo172 y G15 A5 no ejecutado.
- B5 dictamen READY sólo para .github/workflows/faro-v5-s3-a4-n033c2b4-session-grant-directed.yml, resultado de código previo RED_FIX exclusivamente por XML de workflow legado; una vez corregido, B7 PASS_SCOPED si CI verde.

## B7 — Dictamen adversarial del código 986905d856f62edc2f00ef4ffefd43438b79b516
- Código del lease y pruebas nuevas: **PASS_SCOPED del workflow dirigido** #42428854363 con XML 44/44 y guard #42428854264 SUCCESS. Ningún error de runtime/JVM en ese alcance.
- Estado agregado del SHA: **RED_FIX**, porque el flujo histórico #42428854284 concluyó FAILURE sólo al validar `14` vs `18`. El mismo test JUnit informó 18 passed, 0 failed/errored/skipped.
- Causa raíz: expectativa histórica fija en workflow, no control de permisos. El siguiente código permitido es SOLAMENTE la reparación de ese YAML, con mismo unit_id y este B1–B5. El B7 definitivo deberá distinguir PASS_SCOPE del lease de la validación histórica tras corrección.
