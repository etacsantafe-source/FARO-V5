# FARO V5 — Noche B V1.4, turno final 17:40 · 31/10/2026

**Resultado:** corrección de test Android F3 y GitHub Actions PASS **14/14**; S2.A6 **OPEN 3/8**, V2 OFF, S3/S4 cerrados, T4 físico NOT_RUN.

## Trabajo funcional
- Precheck GitHub: HEAD exacto `2ecb5655b8f34b6819ccaefab4de646cdb623ca8`, writer NONE epoch 43; Noche A anterior terminada. Writer B6 epoch 44 adquirido y verificado por CAS en commit `0fe420a657961ce0414055afdc65d68f3e1e4558`.
- El rojo anterior [Actions #41238671049](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41238671049), SHA `64a5717`, fue fallo de compilación Kotlin `UNSAFE_CALL` en `FaroV5F3QuarantinedSafRoundTripInstrumentationTest.kt:726`; XML de instrumentación no ejecutado.
- [Commit `7424bc99`](https://github.com/etacsantafe-source/FARO-V5/commit/7424bc996d79f5ecefac104027788331dfc26dc4): `checkNotNull(before).generationId.value` en el fixture de exportación SAF no válida. No modifica código de producción, no altera la compuerta V2 ni el puntero V1.
- [Actions #41240002977](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41240002977), SHA exacto `7424bc996d79f5ecefac104027788331dfc26dc4`: **SUCCESS**; pasos instrumentación y oráculo XML SUCCESS; `F3_V14_PINNED_PROVIDER_JOURNAL_PASS 14/14`, cero fallos/errores/omitidos, emulador Android API 35. Evidencia del log 20:49:06 UTC.

## Tiempos y evidencia
- Programado 17:40–18:10 -03; runtime iniciado `2026-10-31T20:39:14.431065Z`; writer adquirido `2026-10-31T20:40:08.002Z`; checkpoint preparado `2026-10-31T20:50:17.523Z`.
- Tiempo observado hasta checkpoint: **663 s**; writer poseído hasta checkpoint: **609 s**. No atribuir 30 minutos completos.
- CI #41240002977: 20:40:30–20:49:09 UTC = **519 s wall**; log runner 20:40:33.137–20:49:07.240 UTC ≈514 s; cola proxy ≈3 s; cola/ejecución exactas **NO_REGISTRADO**.
- Espera CI observada por suspensiones explícitas de 20 s: **al menos 340 s**; un intento adicional con timeout no se cuantifica. Investigación, código, revisión, coordinación, tiempo productivo e inactividad como categorías exclusivas: **NO_REGISTRADO**. La CI transcurrió en paralelo y no se suma otra vez al tiempo de la sesión.

## Pendientes sin certificación
- F3: falta selector SAF real con consentimiento humano, resultado externo verificable y recorrido físico; el adaptador con `content://` y journal de cinco stores sí tiene pruebas de emulador. La exportación productiva está correctamente bloqueada mientras `V2_ACTIVE_ENABLED=false`; **no** levantar esa compuerta para forzar PASS.
- F1 Chat+IA consentida+Review+Apply+Delivery Android, F2 activación/recovery, F4 regresiones integrales: abiertos. T4 físico: **NOT_RUN**, requiere usuario. Sólo 3/8 criterios congelados certificados.
- Próxima acción: informe final READ_ONLY a las 18:10, desactivar/verificar automatizaciones y conservar rama; no abrir S3/S4.

**Writer:** este checkpoint se publica junto con liberación CAS epoch 44; verificar HEAD y `writer NONE` después del CAS antes de afirmarlo.
