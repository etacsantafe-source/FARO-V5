# FARO V5 — W04-C3 · refresco tipado append-only STAGED — B7 PASS

2026-11-20. Unidad `S4_W04_C3_STAGED_TYPED_APPEND_REFRESH_DEV`; B1–B5 `docs/v5/FARO_S4_W04_C3_APPEND_REFRESH_PREAUDIT_20261120.md` (blob `0af24f8d6f1afa88922ca0f5b1f3330a97ecbdf3`); READY exact3 `54e75ebd2b84fec4287a792cfaeabee6d0ef978a`, guard de permiso `44851309307` SUCCESS.

## B7

**PASS_SCOPED_STAGED_DEV_AFTER_RED_FIX.** Código inicial `698a093c29240806e7e27fc5a9f08cc98190b568`, guard `44851508611` SUCCESS, CI `44851508657` FAILURE: 99 pruebas, una fallida `toolSourceAndMissingProductAreNotManualRefresh` porque el fixture sin grant pedía NO_EXISTING_HISTORY en lugar de NOT_AUTHORIZED. B7 RED_FIX registrado en `docs/v5/FARO_S4_W04_C3_B7_RED_FIX_20261120.md`, blob `09469a768d4420611f2ae0e9c4c8d9ded0a69c90`, documentación `08726994842b776e0cfb711b532dd3a993eed4e3` y guard `44851973350` SUCCESS.

RED_FIX funcional final `fef0c845c93c4caa1d066344cf020ea1dde97e8d`: **sólo dos de tres rutas autorizadas** (test `CommercialTypedPriceRefreshTest.kt`, workflow `faro-v5-s4-w04-c3-append-refresh.yml`); se agregó grant explícito del producto del fixture y se conservó preflight por cada ruta de diff bajo count 1..3. **El código Kotlin productivo `CommercialTypedPriceRefresh.kt` no cambió.**

Guardia código final `44852084952` COMPLETED SUCCESS. CI dirigida `44852084693` COMPLETED SUCCESS, job `136685546264`, `:evidence:test` BUILD SUCCESSFUL in 2m 56s; JUnit XML C3 exacto **(12 tests,0 failures,0 errors,0 skipped)**, marca `W04_C3_STAGED_TYPED_APPEND_REFRESH_JVM_XML_PASS 12/12`; regresión Evidence SUCCESS. Artefacto XML `14685817016` `w04-c3-staged-typed-refresh-xml`, 10248 bytes, no expirado.

Alcance: servicio STAGED manual que verifica contrato OBSERVATIONAL_WRITE, grants Household+Commercial, fence generación/pointer/epoch y confirmación humana, compara watermark antes de anexar una nueva observación y terms de Evidence2 mediante transacción única existente; conserva historial y evita falso rollback tras COMMITTED (RecoveryRequired). No crea host ACTIVE, Android UI ni prueba física G15; tampoco ejecuta fuente, paga, agrega stock, muta Finance ni abre egress. **C3 B7 PASS acotado**; consumir permiso a BLOCKED, writer RELEASED al publicar checkpoint.
