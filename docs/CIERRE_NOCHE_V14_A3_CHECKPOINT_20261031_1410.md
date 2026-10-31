# FARO V5 V1.4 — Noche A turno 3 (14:10 Argentina)

- Inicio real de ejecución: 2026-10-31T17:06:41.212128Z (antes del horario programado, sólo READ_ONLY hasta 2026-10-31T17:10:00Z).
- Writer recuperado con CAS: 2026-10-31T17:10:16.080Z; epoch 37; dueño NOCHE_A_V14_TURN3_20261031_1410. Owner anterior A2 terminó y B2 terminó; lease anterior vencido; HEAD original d722d213.
- Último código: `e9a1097c648cbd108569b2b7435462c5df1d86e3`.
- Writer: NONE por commit CAS de liberación que contiene este checkpoint; verificar HEAD y estado después del CAS.
- S2.A6 OPEN **3/8**; F1/F2/F3/F4 OPEN; T4 físico NOT_RUN; V2 OFF; S3/S4 CLOSED.

## Desarrollo F3

1. `60e0acf3672a67e3af3e0f14eca72dc82de9e807`: autenticar una sola lectura de content://, fijar los bytes exactos en staging cifrado privado ANTES de journal; restaurar desde el pin, no volver a leer un proveedor mutable; nueva regresión Android que modifica el proveedor después de autenticar y valida restore de cinco owners sin tocar V1.
2. `06c04c74953923d0f409108710a9c8d43090e38c`: corregir literal Kotlin del test antes de certificación.
3. `e9a1097c648cbd108569b2b7435462c5df1d86e3`: proteger archivos cifrados compartidos por digest ante conflicto de journal; preflight de autoridad pendiente; no borrar un digest que podría estar fijado por otra operación.

## CI observado al checkpoint

- [#41223479830](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41223479830) SHA e9a1097c648cbd108569b2b7435462c5df1d86e3, pending/NONE, XML NOT_VERIFIED.
- [#41223312753](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41223312753) SHA 06c04c74953923d0f409108710a9c8d43090e38c, completed/cancelled, XML NOT_VERIFIED.
- [#41223286607](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41223286607) SHA 60e0acf3672a67e3af3e0f14eca72dc82de9e807, in_progress/NONE, XML NOT_VERIFIED.
- [#41219203795](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41219203795) SHA d722d2139b8974e0fd059eaf8a245a2134117dda, completed/success, XML PREVIOUS_SHA_PASS.

**No declarar PASS F3** hasta verificar terminal y XML de la Action SHA `e9a1097...`; la prueba nueva requiere oráculo 10/10. La cancelación automática de la ejecución intermedia no es PASS.

## Medición (sin doble contabilidad)

- Checkpoint preparado 2026-10-31T17:14:17.068Z.
- Duración real desde inicio de la ejecución: 455 segundos.
- Duración transcurrida de la ventana programada hasta checkpoint: 257 segundos.
- Writer adquirido durante 240 segundos hasta checkpoint; liberación se verifica aparte.
- Desglose exclusivo investigación/código/pruebas/esperas/checkpoint: **NO_REGISTRADO** (no inventar minutos).
- CI wall, cola y ejecución: pendiente de cierre; no sumar como trabajo productivo independiente.
- Resto de ventana 14:10–14:40: no se presume actividad productiva si no hay evidencia.

## Pendiente concreto

Verificar Action #41223479830 con SHA y XML 10/10; si falla investigar y corregir con hipótesis nueva. F3 no certificado: selector SAF real de usuario, retención acotada de artefactos sin referencia y E2E de journal/restore; F1 remoto consentido, F2, F4 y T4 físico pendientes. Mantener writer NONE hasta siguiente sesión.
