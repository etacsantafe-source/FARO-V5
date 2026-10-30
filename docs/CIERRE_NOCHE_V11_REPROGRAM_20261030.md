# FARO V5 — CIERRE Noche V1.1 · NUEVO piloto de 3 horas, 30/10/2026
**Estado:** NEW_EXPLICIT_AUTHORIZATION / SCHEDULES_CONFIRMED_ENABLED / DO_NOT_EXTEND.

## Autoridad y calendario (Argentina UTC-03)
- La persona usuaria pausó expresamente las tres automatizaciones anteriores, resolvió el bloqueo F3 aprobando opción A y luego dio una NUEVA orden explícita «Reprogramamos». Esta autorización reemplaza solo para la nueva ventana el estado PAUSED del registro histórico; NO autoriza tareas fuera de la ventana ni prórroga automática.
- **Inicio programado**: 2026-10-30T15:10:00-03:00. **Corte absoluto**: 2026-10-30T18:10:00-03:00, o al certificar S2.A6 CLOSED/PASS 8/8 antes (lo primero). Si solo queda T4 físico o únicamente operaciones que requieran decisión humana, detener desarrollo y cerrar.
- Noche A: 15:10, 16:10 y 17:10, un máximo de 30 min desde inicio efectivo, nunca más allá del corte.
- Noche B: 15:40, 16:40 y 17:40, igualmente máximo 30 min y EXCLUSIVIDAD estricta con A.
- Informe final (READ_ONLY, una única ejecución): 18:20, 10 min después del cierre de la última ventana prevista. Adelantarlo si S2.A6 se cierra antes y la herramienta lo permite; nunca crear informes duplicados.
- Solo pueden usarse las tres automations previamente pausadas y expresamente reprogramadas; ninguna otra tarea nueva o histórica puede activarse. Registrar y verificar la programación y habilitación reales.

## Límite de autoridad
- Repositorio exclusivo privado: `etacsantafe-source/FARO-V5`.
- ÚNICA rama de escritura: `work/cierre-noche-v11-pilot-20261030`; último HEAD observado antes de autorización: `d421a2d7fa08b424129bf54067b8620410eff9cc`. Revalidar HEAD antes de escribir y nunca asumir que se mantuvo.
- Protocolo rector: `docs/cierre-noche/PROTOCOL_V1.md` V1.1 en `docs/cierre-noche-v1-20261030`.
- **Decisión F3 más nueva y específica:** `docs/FARO_V5_F3_S2_S5_DECISION_20261030.md` en rama autorizada. Resuelve el viejo WAIT_HUMAN del consentimiento S2/S5; permite en S2 el flujo **mínimo de exportación y restauración con consentimiento explícito y selector de archivos SAF Android**, sin adelantar la pantalla definitiva de Ajustes S5. S5 conserva su superficie definitiva. F3 queda IN_PROGRESS, **NO CERTIFIED**.
- Sin tocar `main`, `etacsantafe-source/FARO` original, V1/V4.4/FARO Auto, sin merges, publicaciones, nuevas cuotas/gastos, secretos, cambios de permisos o firma de APK. V2 OFF y S3/S4 sin abrir. No depender de Termux. Pruebas T4 físicas solo con usuario/dispositivo, pendientes.
- Nunca relajar identidad, writer único, inmutabilidad ApplyPlan/fingerprint, autorización y políticas de egreso, recuperación, restauración/quarantena/SSRF o regresión V1 para conseguir verde.

## Estado verificado al crear permiso
- Rama en `d421a2d7fa08b424129bf54067b8620410eff9cc`; repo PRIVATE; última autoridad heredada de checkpoint: `S2.A6 OPEN`, `3/8`, `V2 OFF`, writer NONE. No atribuir writer vivo solo a un snapshot; comprobar exclusividad real en cada sesión.
- Ninguna GitHub Actions queued/in_progress a la verificación inicial; todas las automatizaciones preexistentes desactivadas.
- Evidencia histórica en rama anterior: 29/29 pruebas dirigidas, 36/36 regresiones críticas, 31/31 Surface, NO Android E2E/T4.
- Criterios congelados: 1 cinco owners PASS; 2 FBS2 restore PASS; 3 journal/replay/WAL PASS; 4 F1 Android chat/review/apply/delivery IN_PROGRESS; 5 F2 Android activación/recovery IN_PROGRESS; 6 F3 backup SAF/consent IN_PROGRESS tras decisión humana resuelta; 7 F4 integral IN_PROGRESS; 8 T4 físico NOT_RUN. Mantener 3/8 hasta evidencia completa de cada criterio.

## Contrato exacto de ejecución
1. Antes de actuar: leer este permiso y estado durable real/último checkpoint, HEAD, runs CI queued/in_progress/terminal y último turno de automatizaciones A/B. Verificar horario permitido, ausencia de otra ejecución viva/writer/lease activo y epoch; adquirir writer mediante CAS sobre HEAD exacto. Ante incertidumbre: FAIL CLOSED = READ_ONLY sin escrituras/Actions; informar el impedimento.
2. Minutos 0–20 desde inicio efectivo: recuperar→investigar brechas indispensables F1/F2/F3/F4→implementar mínimo seguro en rama exclusiva→probar CI dirigida con GitHub Actions `contents:read`→evaluar VERDE y **encadenar** trabajos útiles. ROJO: investigar nuevas hipótesis, corregir seguro evitando repetir ciegamente. No inventar certificaciones ni trabajos; F3 ya no está bloqueado por decisión de UX.
3. Desde minuto 20: **no iniciar** implementaciones nuevas, escrituras de producto nuevas ni Actions. Hasta minuto 25 recoger y cerrar trabajos en curso.
4. Minuto 25: si Actions sigue en progreso/cola, registrar ID, SHA, estado `WAIT_CI` para siguiente sesión; NO cancelar automáticamente, no lanzar Actions duplicada ni escritor superpuesto.
5. Minutos 25–30: checkpoint durable, resultados, SHA, XML y errores, writer NONE y terminar. No afirmar que el runtime sostuvo 30 minutos sin observarlos. Si cae contexto/cuota, retomar la próxima sesión desde GitHub reconciliando commits y CI primero.
6. El corte absoluto 18:10 prevalece incluso si la sesión empezó tarde. Al cerrarse S2.A6 o quedar solo T4, pausar ambas automatizaciones y adelantar informe de forma segura si es posible. No abrir S3/S4, no ampliar permiso ni programar más tareas.
7. Informe final 18:20: leer GitHub READ_ONLY, identificar ejecuciones reales (no solo calendario), X/8 evidenciado, F1/F2/F3/F4/T4, ROJOS, CI y SHA/XML, writer/leasing, duración efectiva y pendiente exacto para S2. No modificar código ni disparar Actions.

## Confirmación real de programaciones (segunda ventana)
- Reutilizada Noche A id `6ae4d8f201948191aacf0589adcb5c64`: is_enabled=true, DTSTART 20261030T181000Z, HOURLY COUNT=3.
- Reutilizada Noche B id `6ae4d8f4b2308191b3c8c3e148f38570`: is_enabled=true, DTSTART 20261030T184000Z, HOURLY COUNT=3.
- Reutilizado Informe final id `6ae4d90683b88191930dd64194370d27`: is_enabled=true, DTSTART 20261030T212000Z, ejecución única.
- **Las ejecuciones programadas son intenciones, no comprobación de trabajo efectivo ni 30 minutos de cómputo.** Cada sesión debe reportar tiempo real y checkpoint.
