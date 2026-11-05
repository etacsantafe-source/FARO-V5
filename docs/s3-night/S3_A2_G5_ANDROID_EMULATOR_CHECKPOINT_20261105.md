# FARO V5 — G5 Android emulador · checkpoint B7 PASS (05/11/2026)

## Estado verificado — no repetir pruebas cerradas
- Branch `work/s3-night-20261102`. Último código funcional `bcb38e9ca36da7ae9df387d80bb2e911cbb71533`, modificación SOLO de la invocación bash del workflow Android. Sin cambios en código de la app ni datos de Moto G15.
- [G5 Android API35 CI #42071443306](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42071443306) **SUCCESS**, compilación app y AndroidTest **SUCCESS**, emulador arrancó y `FaroV5G5AndroidHostIntegrationTest.fourRealOwnersSearchNavigateAndRevokeOnAndroidWithoutEgress` corrió **1/1 PASS**. Salida exacta `OK (1 test)`, `INSTRUMENTATION_CODE: -1`, `G5_ANDROID_REAL_OS_FOUR_OWNER_PASS 1/1`.
- [Protocolo #42071443729](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42071443729) **SUCCESS**. B7 de código corregido **PASS**, documento `docs/s3-night/S3_A2_G5_ANDROID_EMULATOR_PASS_B7_20261105.md`; permiso `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` refleja SHA exacto y revisión B7.
- Fallos históricos ya resueltos: OOME en #42066291096; dependencia A5 legacy en #42067731793; shell dividido por línea en #42069366649. **No volver a esas hipótesis ni repetir tests ya aprobados.**
- G1–G4 **CLOSED**. S3.A2 **4/5** (80%) aún formal, G5 **OPEN_PHYSICAL_PENDING**; S3 cierre formal **0/5**, desarrollo heurístico previo aproximado ~40%; S2 **8/8 frozen**.
- El G5 emulado verificó búsquedas Finance/Objective/Matter, apertura tipada, miss, validez de sesion/revocación de puertos y permisos/manifest dentro de sus aserciones. No equivale a UI tocada en Moto G15 ni prueba de red/IPC externa total.

## Único pendiente de G5
Una **sola campaña física final** en Moto G15 para casos positivos de Finanzas, Objetivos, Asuntos (las tres tablas estaban en cero en snapshot físico), navegación de detalle y adversariales ACL/egress/IPC pertinentes. Chat local, persistencia, búsqueda Workspace, reinicio, UID y ausencia de permisos observados ya tienen evidencia física parcial y no deben repetirse salvo requisito justificado. Sin SQL directo ni inventar datos, sin Termux copiado repetitivo ni acceso remoto ficticio al equipo. Dejar claro qué no puede certificarse físicamente desde GitHub.

## Restricciones vigentes
Writer único CAS liberado al terminar; V2 productiva OFF; S4 OFF; FARO Auto/Noche OFF; no tocar `main`, V4.4 ni datos de usuario; catálogo 172 sin expansión; sin APIs pagas ni claims de G5 CLOSED hasta la campaña física o decisión formal aprobada. El siguiente turno debe leer primero este checkpoint, el B7 PASS y el estado writer.
