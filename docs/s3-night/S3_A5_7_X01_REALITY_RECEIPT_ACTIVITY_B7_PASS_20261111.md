# FARO V5 — X01 Actividad / Reality COMMITTED receipt · B7 PASS acotado
Fecha de cierre: 2026-11-11 AR (CI UTC 2026-11-12). Unidad `S3_A5_7_X01_REALITY_COMMITTED_ACTIVITY_ANDROID_SCOPED`.

## B1–B5 — Preauditoría y delta
La evidencia original y el alcance test-only de dos rutas constan en `docs/s3-night/S3_A5_7_X01_REALITY_RECEIPT_ACTIVITY_B1_B5_20261111.md` (blob `a21b49b7e01b7a3a49cd8c6fc77580d8da30e15a`). La corrección RED_FIX, causal y restringida a **un YAML**, consta en `docs/s3-night/S3_A5_7_X01_XML_EVENT_CARDINALITY_B7_RED_FIX_20261111.md` (blob `a37438d3480c6a7a935dcff44c74de95e1088605`). No se repiten tests históricos X01/X02, X06, X08 ni X12.

## B6 — Código, control y CI dirigida
Código Android test-only y workflow: Kotlin test nuevo + workflow X01, sin cambios de producción ni permisos Android. Commit de la corrección final `a48c19708f469ede066ae0f31918ecf1bf3a69e2` (un YAML; parser de eventos de inicio/fin que preserva una sola finalización exitosa); original B6 `cc112b1074bab80e75d0e4c75bff43b460f19c11`. Mensaje con `FARO-UNIT:S3_A5_7_X01_REALITY_COMMITTED_ACTIVITY_ANDROID_SCOPED`.

- CI [run 43134563606](https://github.com/etacsantafe-source/FARO-V5/actions/runs/43134563606), job `130906660799`: **COMPLETED SUCCESS**, source SHA exacto `a48c19708f469ede066ae0f31918ecf1bf3a69e2`. Compilación de app V2 DEV y AndroidTest, verificación manifest/firma, emulator API35 offline, test X01, JUnit XML y artifact todos SUCCESS.
- Guardia [run 43134563570](https://github.com/etacsantafe-source/FARO-V5/actions/runs/43134563570): **SUCCESS**, mismo SHA.

## B7 — Verificación independiente del artifact ZIP
**PASS_SCOPED_REALITY_COMMITTED_ACTIVITY_ANDROID**. Artifact GitHub `13907536117`, 10 entradas; ZIP descargado y recalculado SHA-256 `98dc81adb03e8508e196ccfe72d278e578e52b2e04a0ec949074fb7ec97215c1`, igual al digest publicado por GitHub. Detalle reproducible en `docs/s3-night/S3_A5_7_X01_ARTIFACT_VERIFICATION_20261111.json`.

- XML abierto con parser: `tests=1, failures=0, errors=0, skipped=0`, caso `x01CanonicalRealityReceiptVisibleOnlyAfterForegroundReadAndRevokedOnPause`. Log extraído del ZIP: evento start=1, terminación éxito `INSTRUMENTATION_STATUS_CODE: 0`=1, `OK (1 test)`, `INSTRUMENTATION_CODE: -1`. El primer CI 43133141939 era FAILURE por contador duplicado de eventos start/end y quedó registrado **RED_FIX**, no fue presentado como PASS.
- APK V2 DEV `fef6445b5750e47e1e20d0968671b7ebfa1fd6e65ff89f1eaeed2e8b4e4133be`; APK AndroidTest `ddc14da9bed3a1dceb2c77f3f2b59cb807575d1842db2486b11a33aad0cd3dc4`. Ambos SHA-256 recalculados de las entradas ZIP y comparados con `x01-receipt-apk-sha256.txt`. Fuente `x01-receipt-source-sha.txt` exactamente `a48c19708f469ede066ae0f31918ecf1bf3a69e2`.
- Badging: `ar.com.faro.alpha.v2coredev` y `ar.com.faro.alpha.v2coredev.test`. Permisos/manifest extraídos: V2 DEV sin `android.permission.INTERNET`, sin Activity X03/U02, sin `sharedUserId`. Firma de ambos APK verificada por runner (no repetida localmente).
- Código test reexaminado: el fixture usa owner real `RealityStore.run/commit` para receipt duradero `COMMITTED` y verifica lookup e historial (no DTO simulado). La UI exige gesto foreground nativo, fila `Aplicación confirmada` con tiempo de commit, no presenta ID, fingerprint ni receipt ID; pausa y reanudación revocan y ocultan el historial sin nuevo gesto. Esta evidencia se limita al test integrado en emulador Android35.

## Dictamen, límites y continuidad
**B7 PASS acotado**, unidad X01 de **receipt Reality COMMITTED → Actividad V2 DEV Android** cerrada. NO declarar X01 global/S3.A5 global cerradas; no demuestra Apply X10 en el mismo host, flujo IA real, dispositivo Moto G15 ni aceptación de campaña física. Test sintético usa datos efímeros de CI. V2 productiva OFF, S4/AUTO/Noche OFF; rama `main` intacta. Permiso de esta unidad queda **consumido/BLOCKED**, sin autorización a otras X. Writer CAS y checkpoint se verifican tras publicar; no reciclar unit B1–B5 para nuevo código.
