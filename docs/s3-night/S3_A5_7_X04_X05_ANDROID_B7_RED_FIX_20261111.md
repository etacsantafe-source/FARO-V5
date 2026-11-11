# FARO V5 — S3.A5.7 X04/X05 Android — B7 RED_FIX 2026-11-11

Unidad: `S3_A5_7_X04_X05_ANDROID_GUARDADOS`.
Commit funcional auditado: `80db826dace9c9155ca729e84be64071c5a44744`. Guardia `seven-block-evidence`: run `43072811113`, SUCCESS.
CI Android exacta: run `43072811590`, job `130704861164`, FAILURE (compilación, no emulador).

## B7 — RED_FIX documentado antes del siguiente código funcional

- `assembleV2CoreDev` terminó y generó APK DEV; `:app:compileV2CoreDevAndroidTestKotlin` falló.
- Error real del log del job: `UNRESOLVED_IMPORT: Unresolved reference 'onAllNodes'` en `FaroV5S3A57BookmarksUiTest.kt:14`.
- Causa delimitada: import explícito de una operación miembro de `SemanticsNodeInteractionsProvider`; las pruebas existentes usan `rule.onAllNodes(...)` sin importar `onAllNodes`.
- Estado real: X04/X05 `0/2 NOT_RUN`; no existe `OK (2 tests)`, `INSTRUMENTATION_CODE: -1` ni XML/artefacto aceptado. **No es PASS.**
- Corrección mínima permitida: **eliminar solamente** la línea `import androidx.compose.ui.test.onAllNodes` del test nuevo; dejar el workflow y el comportamiento intactos.
- Prohibido tocar código productivo, catálogo, A3, S4, Auto, Noche, V2 productiva, main o G15.
- Revalidar permiso `READY`, mismo `FARO-UNIT`, HEAD y writer CAS. Repetir CI Android API35, verificar logs, APKs, XML 2/2 y guard por SHA; B7 PASS sólo con evidencia posterior.

Esta auditoría B7 es independiente del commit funcional fallido. El permiso siguiente está acotado a las mismas dos rutas (en la práctica, una sola línea del test).
