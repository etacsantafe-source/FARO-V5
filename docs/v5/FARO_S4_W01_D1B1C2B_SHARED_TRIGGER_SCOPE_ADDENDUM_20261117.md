# D1B1C2B — agregar el workflow de regresión de la etapa

## B1
El código de C2B toca el helper de preparación para volver a verificar source fingerprint en SWITCH_PREPARED. El trigger de C2A se activa por el mismo helper y su preflight fijo solicitaría el permiso consumido de C2A. No se publicó código C2B.

## B2
Reusar la corrección del mismo mecanismo ya verificada en D1B1C1: auditar el checkout real con scripts/quality/faro-protocol-gate.py --mode ci --head HEAD.

## B3
Mantener el trigger y las pruebas de C2A para regresión. La alternativa de quitar el trigger perdería cobertura del helper compartido.

## B4
Agregar solo .github/workflows/faro-v5-s4-w01-d1b1c2a-finance9-preserve-stage.yml al permiso C2B (EXACT5). Sustituir exclusivamente el paso de preflight fijo. Preservar el resto de YAML, fixtures, reportes y uploader.

## B5
Cero cambios de contrato C2A ni relajación de permisos. Guard global y flujo C2B mantienen sus comprobaciones. C2A debe pasar sus siete casos en el SHA real. Registrar exactos C2B, C2A y guard antes del B7.
