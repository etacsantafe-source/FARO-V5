# FARO V5 — A2C1A · Corrección documental de guardia B7

## Diagnóstico rojo comprobado
- HEAD anterior: `2654333501b4cdb80757aa2a59c89a6d535e088a`. Guardia `45925399508` completed **FAILURE**, paso `Verify exact READY permit preflight on GitHub checkout`.
- Log: `FARO_PROTOCOL_GATE_STOP: STOP: previous code change has no B7 disposition`.
- Causa exacta: A2C1A B7 estaba documentado en `docs/v5/FARO_S4_W05_A2C1A_B7_PASS_CONSUME_REVIEW_DEV_20261126.md` (blob `2464bfc1bd8d711536cf4717f5da9225c4f76d71`), pero el permiso seguía `READY` con `last_code_review.sha=7234e551... (A2C0C2)`, mientras el último commit funcional es `3885084a6447febde9586da2c5a02414e3c4aa40`. El gate no puede verificar el B7 del último código.
- A2C1A código: guardia `45925067509` SUCCESS y CI funcional `45925067636` SUCCESS en SHA exacto; JUnit original 8/8, cero FAIL/error/skip según validación del runner. No reejecutar B6, no afirmar extracción independiente del ZIP.

## Corrección sin código
- Consumir A2C1A exact4 a `BLOCKED`.
- `last_code_review` apunta al SHA de A2C1A y evidencia B7 del documento original; B6/B7 quedan `VERIFIED` con blob real.
- Actualizar puntero/hoja de ruta y registrar esta corrección sin tocar rutas funcionales.
- Ejecutar guardia del nuevo checkpoint documental; antes de SUCCESS no emitir READY de A2C1B.
- A2C1B–F siguen sin permiso, producción/stock real/pagos/Auto histórico OFF, writer GLOBAL CAS siempre.
