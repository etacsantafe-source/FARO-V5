# FARO V5 — G5 Android OS · checkpoint reproducible 05/11/2026

## Estado observado en GitHub
- Rama `work/s3-night-20261102`, SHA `13911eacf677b0ec968fd1202b521d764bc73a1c`.
- G1–G4: `CLOSED`. S3.A2: `4/5` (80%). S3 cierre formal `0/5`, desarrollo heurístico previo ~40%. S2 `8/8` frozen.
- G5: `OPEN_WAIT_ANDROID_EMULATOR_CI`; no prueba Android física integral, no positivos Finance/Objective/Matter en Moto G15.
- Prueba host DEV real JVM 1/1 PASS [#42062273554](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42062273554). Histórico Chat/Workspace físico parcial PASS. No repetir G1–G4, ni 10/10, ni acciones manuales ADB.
- Nueva clase Android G5 bajo `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5G5AndroidHostIntegrationTest.kt`, fuente separada del `src/androidTest` histórico mediante propiedad opt-in.
- Test Android exacto [#42069366649](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42069366649) estaba `in_progress`, etapa `Assemble isolated V2 DEV and G5 instrumentation APKs`, al último chequeo; **ningún PASS de test Android declarado**.
- Protocolo [#42069373871](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42069373871) `SUCCESS`; otras regresiones del mismo SHA deben observarse en vivo antes de afirmar PASS.
- Primer intento [#42066291096](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42066291096) FAILURE OOME Gradle; RED_FIX `6a6076585` eliminó OOME. Segundo intento [#42067731793](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42067731793) FAILURE por A5 legacy AndroidTest incompatible con aislado DEV; se aisló source root en `13911eacf`. Esos fallos previos no son fallos de los owners ni habilitan cierre.
- `FARO_PROTOCOL_WORK_PERMIT.json` READY `S3_A2_G5_ANDROID_EMULATOR_DIRECTED`; el próximo cambio **funcional** requiere auditoría B7 escrita sobre `13911eacf`, con PASS o RED_FIX (según CI), antes de otro código.

## Único siguiente paso
1. Leer conclusión/logs de [#42069366649](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42069366649) y SHA exacto; si compila y ejecuta, exigir `G5_ANDROID_REAL_OS_FOUR_OWNER_PASS 1/1` y ausencia de fallos/skips. Nunca convertir `in_progress` en verde.
2. Si FAILURE, B7 RED_FIX circunscrito a causa exacta y nueva autorización de la misma unidad antes de tocar código. Tras dos fallos de misma hipótesis, reevaluar B2/B3/B5. No repetir procesos históricos.
3. Si PASS, documentar auditoría B7 de test **emulador**, todavía no Moto físico. Decidir criterio G5 formal separado; hasta completar físico `G5 OPEN`, `S3.A2 4/5`.
4. Actualizar `docs/s3-night/RUN_STATE.json`, `docs/v5/FARO_CONTINUITY_POINTER.json`, liberar writer CAS y verificar HEAD remoto. No habilitar V2 productiva, S4 ni Auto/Noche; datos del usuario intocados.

**Permisos y seguridad:** V2 productiva OFF, S4 OFF, Auto/Noche OFF, catálogo 172 sin expansión, sin SQL directo en teléfono, sin datos o claves de usuarios en fixtures, sin APIs pagas, no escribir `main`/V4.4.
