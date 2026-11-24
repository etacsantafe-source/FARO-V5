# FARO V5 · W05-A1B3B1 · RED_FIX SQLite module boundary · 2026-11-24

Unidad `S4_W05_A1B3B1_FBS2V8_H10_H11R_STAGED_REPLAY_DEV`, rama `work/s3-night-20261102`.
Commit B6 observado `0c4e5e93ebb2f130eee2c43e62afde19e3927f44`. B1–B5 documentado en `docs/v5/FARO_S4_W05_A1B3B1_FBS2V8_H10_H11R_FULL_STAGE_B1_B5_20261124.md`, blob `1b2f8fd5d299b81665b5c8744ab76ec112728493`, READY exact4 `06426d138845b89565cea1be8293f755b28cf287`, guard `45443988085` SUCCESS. Guardia de código `45444505243` COMPLETED SUCCESS.

## B1 — CI real con fallo reproducible

CI dirigida `45444505515`, job `138624435441`, COMPLETED FAILURE. Preflight exact4 PASS. Kotlin `:store:generation:compileKotlin FAILED` error **UNRESOLVED_IMPORT `androidx`** en `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8FullStageReplay.kt:3`. La implementación de generación importó `androidx.sqlite.driver.bundled` directamente, pero esa dependencia pertenece a `store:reality` en código de producción. Los tests NO corrieron, ningún XML generado y ningún artifact subido: **NO B7 PASS**.

Inspección estática adicional de B6: `beforeOthers = digestOtherStores(stage)` se toma **antes** del replay B0 sobre stage9; B0 restaura intencionalmente cuatro almacenes además de Reality. Comparar el digest final contra el stage9 vacío da falso fallo aunque todos los restores sean correctos. La línea se reubica **después** de B0 verificado y antes de las migraciones H10/H11; en reanudaciones schema10/11 se toma antes de las escrituras H10/H11. Esta rectificación no cambia semántica de otros owners, solo el punto de observación.

## B2 — Comparación/reuso

Los archivos `RealityHousehold10SealedRowsPort.kt` y `RealityHousehold11ReceiptRowsPort.kt` ya son el propietario probado de SQLite bundled e immutable URI. No añadir dependency de AndroidX al módulo `generation`, no modificar build ni código legacy. Usar la ruta reality nueva, autorizada en esta misma unidad, para leer `PRAGMA user_version` bajo preflight de archivo sellado y sin escrituras.

## B3 — Alternativas y decisión

A **elegida:** agregar `readTrustedSealedSchemaVersion(file)` en `RealityHousehold11NeedsRowsPort.kt`, delegar la detección de versión desde generation, retirar imports SQLite de production generación; corregir digest base a después de B0.
B descartada: introducir AndroidX en gradle generation, aumento innecesario de superficie y nuevas rutas.
C descartada: detectar versión leyendo bytes 60-63 del header SQLite manualmente; el `user_version` de SQLite y su formato deben observarse mediante motor SQLite con controles de fuente sellada.

## B4 — Rutas exactas RED_FIX, sin nuevas dependencias

1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11NeedsRowsPort.kt`: agregar lector SQL `PRAGMA user_version` de solo lectura en fuente sellada.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8FullStageReplay.kt`: eliminar imports y acceso directo a SQLite, delegar lectura; mover comparación de hashes de otros owners al estado base ya restaurado.
3. `.github/workflows/faro-v5-s4-w05-a1b3b1-h10-h11r-stage.yml`: permitir `count=3` para commit RED_FIX, además de exact4 inicial, sin variar JUnit ni verificador XML.
No alterar archivo test, codecs, gradle ni rutas de A1B3B0. Ningún cambio en V2 productiva.

## B5 — Auditoría adversarial de reparación

Repetir compilación productiva Kotlin + JUnit 10 tests originales. Garantizar preflight 3 rutas contra parent READY con `last_code_review.result=RED_FIX`, no omitir archivo por constructor. Confirmar restored five-store base B0, reintento etapa10/11, idempotencia del ciphertext, recibos parciales, versión incorrecta y foreign H10. Exigir XML genuino 10/10 y volver a verificar que A1B3B1 no alteró archivos no-Reality *después* de B0.

## B7 — Resultado del primer intento

**RED_FIX**, no PASS, por falla compilación `45444505515` y defecto de punto de observación confirmado por código. El puntero activo no fue cambiado, no hubo recepción stock real, cutover, pagos ni Auto/Noche. La misma unidad debe cerrar con nuevo código y B7 independiente si CI/XML pasa.
