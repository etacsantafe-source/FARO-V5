# FARO V5 — F2C1B0 · RED_FIX de workflow en corrección acotada
Fecha: 2026-11-23 Argentina. Unidad `S4_W04_F2C1B0_SELECTED_ACTIVE_TYPED_PROJECTION_DEV`. Code SHA auditado `a41acb9685a0b881d50ee0e9c46f653a42cdc2ba`. Solo permiso de esta unidad; V2 productiva OFF.

## B1 — Evidencia real
- La corrección previa al test `JUnit assertNotNull -> Kotlin requireNotNull` está en `a41acb9685a0b881d50ee0e9c46f653a42cdc2ba` y el guard obligatorio `45119417725` SUCCESS.
- La CI dirigida `45119417722`, job `137509512966`, terminó FAILURE exactamente en paso `Exact three path preflight and mandatory protocol`, antes de Gradle; el log incluye `FARO_PROTOCOL_PREFLIGHT_PASS S4_W04_F2C1B0_SELECTED_ACTIVE_TYPED_PROJECTION_DEV c19c522b5acc37ffcffb7d1fb125ce70718e64bd`, inmediatamente seguido de exit code 1 por `test "$count" -eq 3`. El commit de corrección tiene UN único archivo en diff, el test Kotlin, autorizado por READY.
- La guardia no es la causa. El verificador propio del workflow equivocadamente exigió tres cambios en **cada** commit, aunque RED_FIX parcial está permitido y la guardia ya audita rutas.
- La CI Home del mismo SHA puede continuar independientemente: no declarar compilación JVM 6/6 hasta verificar su propio XML.

## B2 — Reutilización y causa
- Mantener protocolo `scripts/quality/faro-protocol-gate.py` sin cambios, pues pasa el preflight exacto del padre para la ruta corregida y evita cambios ajenos.
- La condición original `count == 3` era válida para la primera publicación B6, no para un RED_FIX limitado. V4.4, F2C0, F2C1A y D0–D4 no se tocan.

## B3 — Alternativas
- Regla de GitHub Actions `git diff --name-only HEAD^ HEAD`: https://git-scm.com/docs/git-diff. El conjunto de cambios en una corrección es un subconjunto del alcance autorizado, no necesariamente el tamaño completo.
- Alternativa A `count==3`: DESCARTAR por falsa alarma frente a test-only fix.
- Alternativa B `count>=1 && count<=3` CON preflight FARO por cada ruta y `FARO-UNIT` exacto: ADOPTAR.
- Alternativa C eliminar el conteo: DESCARTAR porque permitiría cambios sin comprobar y perdería prueba explícita de límite.

## B4 — Delta exacto
Solo `.github/workflows/faro-v5-s4-w04-f2c1b0-selected-projection.yml`: cambiar la condición final de `test "$count" -eq 3` a dos condiciones `test "$count" -ge 1` y `test "$count" -le 3`; conservar la comprobación por ruta, guard obligatorio, JUnit6/XML, SDK, Java, artifact. El permiso de la MISMA unidad sigue READY exact3, el último código `a41acb9685a0b881d50ee0e9c46f653a42cdc2ba` queda RED_FIX documentado antes de modificar workflow.

## B5 — Control adversarial
- Un commit con 0 archivos funcionales no debe pasar por el conteo; con 4 o más debe fallar; 1–3 solo si TODOS satisfacen preflight FARO para misma unidad (ninguna ruta ajena).
- Doble commit sin B7 previo seguirá bloqueado por guard obligatorio.
- Rerun CI sobre NUEVO SHA exacto, 6 testcases y XML real con 0 failures/errors/skips, regressions S3 Home/V2-DEV pertinentes. Sin Android hardware, Apply, pagos, egress, Auto/Noche, main, V4.4 o shipping.

## B7 — RED_FIX de `a41acb9685a0b881d50ee0e9c46f653a42cdc2ba` (NO PASS)
`RED_FIX_WORKFLOW_FALSE_EXACT3_ON_SCOPED_TEST_ONLY_CORRECTION`: resultado rojo real es una aserción del workflow, no un nuevo defecto de la fuente productiva. La auditoría NO certifica F2C1B0 ni declara 6/6. Autoriza exclusivamente el cambio de condición del workflow de esta misma unidad, con nuevo SHA+CI+XML antes del cierre final.
