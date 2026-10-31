# FARO V5 — V1.4 Noche A turno 17:10 · 31/10/2026

**Estado:** S2.A6 OPEN **3/8**, V2 OFF, S3/S4 CLOSED, T4 físico NOT_RUN. Cierre anticipado medido, sin atribuir 30 minutos ni certificaciones nuevas.

## Cambios funcionales
- [2bcb78b](https://github.com/etacsantafe-source/FARO-V5/commit/2bcb78b699b5156ee0b3b936b37e09532d8fab9f): runtime de consentimiento SAF exige URI `content://` antes de exportar/restaurar; preview rechaza `file://` sin abrir proveedor; restauración rechaza antes del journal. Test Android positivo autentica MediaStore `content://` en preview, verifica cinco stores, ningún journal y puntero V1 intacto; después verifica restauración en cuarentena.
- [64a5717](https://github.com/etacsantafe-source/FARO-V5/commit/64a5717961c5b4c5dcff9a84de6f6f6115816b74): amplía test Android para demostrar que un destino de exportación `file://` queda FAILED durablemente antes de escribir, sin activar V2.

## GitHub Actions
- [#41237986536](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41237986536), SHA `2bcb78b`: **SUCCESS**, XML exacto `F3_V14_PINNED_PROVIDER_JOURNAL_PASS 14/14`, cero fallos/errores/omitidos, Android emulador API35. 20:14:42–20:21:36 UTC = 414 s wall; primera línea de runner 20:14:45 UTC (cola proxy 3 s), tramo de logs de ejecución ~409 s; tiempos exactos de cola/ejecución NO_REGISTRADO.
- [#41238671049](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41238671049), SHA `64a5717`: **IN_PROGRESS**, conclusión `NO_REGISTRADO`, job 124647724429, XML `NOT_YET_VERIFIED`. **No declarar PASS hasta revisar resultado y XML**. No cancelar ni lanzar otra CI después del minuto 20.

## Métricas exclusivas
- Programado 17:10 -03; inicio runtime 17:12:05 -03; primera herramienta observada 17:12:28.601; writer epoch43 adquirido 17:12:37.854 y CAS confirmado 17:12:51.639.
- Checkpoint preparado 2026-10-31T20:26:52.114Z; wall observado 887 s; ventana programada transcurrida 1012 s; writer mantenido 854 s. Cierre anticipado para evitar dejar writer retenido mientras CI sigue, sin mover los cortes originales.
- Investigación, código, pruebas/revisión, espera CI, coordinación e inactividad: **NO_REGISTRADO** como categorías exclusivas; espera writer 0. CI se superpone con la sesión y **no** se suma al tiempo productivo.

## Continuidad
- Verificar primero Actions #41238671049 y XML sobre SHA exacto, sin repetir 14/14 aprobadas de #41237986536.
- F3 sigue abierto: falta recorrido positivo de **selector SAF Android real** con consentimiento de usuario. El test MediaStore no es una selección real del picker; exportación de generación activa permanece correctamente bloqueada mientras V2 OFF. No desactivar ese gate ni simular T4.
- F1 remoto consentido/Keystore E2E, F2 startup/recovery, F4 integral y T4 físico siguen pendientes. Un launcher interno para el selector SAF no se verificó en los hosts principales inspeccionados; preparar entrada de prueba segura sin activar V2, si procede.
- No tocar main, histórico, V1/V4.4, FARO Auto, S3/S4, secretos, pagos ni visibilidad.
- Writer epoch43 liberado por este commit con CAS esperado; **verificar post-CAS** antes de afirmar NONE.
