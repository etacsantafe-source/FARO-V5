# FARO V5 — autorización piloto CIERRE Noche V1.2 (30/10/2026)

Estado inicial: **AUTHORIZED / PENDING_FIRST_SESSION**. Autorización expresa recibida en chat nuevo el 30/10/2026. Este archivo es el punto de recuperación durable; no sustituye verificar la programación real de ChatGPT, GitHub HEAD, writer ni hora actual.

## Alcance y reloj
- Repo privado de escritura exclusivo: `etacsantafe-source/FARO-V5`.
- Rama exclusiva: `work/cierre-noche-v12-pilot-20261030`, creada desde `f1873b71da4622cb7309e1bff082d2f76bb66fa5` (V1.1), y ninguna otra rama writable.
- Protocolo normativo V1.2: `docs/cierre-noche/PROTOCOL_V1.md` en `docs/cierre-noche-v1-20261030`.
- Inicios programados, hora Argentina (UTC-03:00): Noche A 18:40, 19:40, 20:40; Noche B 19:10, 20:10, 21:10 del viernes 30/10/2026.
- Límite global de autorización **2026-10-30T21:40:00-03:00** (o S2.A6 8/8 certificado antes). Nunca escribir, iniciar Actions ni adquirir writer después. Si retraso al iniciar, no extender el límite.
- Tres ejecuciones RRULE COUNT=3 por tarea; **sólo dos automatizaciones**, A/B. Verificar sus IDs y que las previas permanezcan disabled. Inicio efectivo y duración real por ejecución se registrarán, sin fingir una ventana de 30min.

## Estado inicial recuperado y verificado
- Head fuente V1.1: `f1873b71da4622cb7309e1bff082d2f76bb66fa5`.
- `docs/CIERRE_NOCHE_RUN_STATE.json` fuente: CLOSED, writer NONE epoch8, sin CI en vuelo; comprobar en vivo antes de adquirir writer.
- S2.A6 OPEN, 3/8 certificados: 1 FIVE_OWNERS, 2 FBS2_ISOLATED_RESTORE, 3 JOURNAL_REPLAY_WAL.
- No certificados: 4 F1_ANDROID_CHAT_E2E; 5 F2_ANDROID_ACTIVATION_RECOVERY; 6 F3_FIVE_STORE_BACKUP_SAF_CONSENT (opción A ya aprobada); 7 F4_INTEGRAL_REGRESSIONS; 8 T4_PHYSICAL NOT_RUN.
- La evidencia anterior de #41125074207 pertenece exactamente a SHA `b030c2fa464a211921015fac47c1e25d37247dc2`: 38/38 dirigidas, F1 18/18, F4 36/36, Surface 31/31; NO Android E2E ni T4.

## Reglas operativas vinculantes
- Una única adquisición writer por vez, lease/epoch con verificación GitHub y actualizaciones CAS sobre expected_sha; confirmar otra A/B no está ejecutándose. En conflicto/incertidumbre: sólo READ_ONLY. Ninguna superposición de escritores.
- Prioridad **desarrollar F1** funcional en Android: ComposedModelGateway + bootstrap real de perfiles/credenciales Keystore + política egreso + Composer→Review→Apply→Delivery. Después F2 Android recovery y F3 UI mínima SAF/consentimiento/restore, opción A aprobada. F4 integral al cierre. Sin microtests redundantes.
- Minutos efectivos 0–20 desarrollo encadenado; 20–25 terminar sin arrancar nada; min 25 registrar Actions run ID + SHA + WAIT_CI sin cancelarlas; 25–30 checkpoint durable + writer NONE. Si ChatGPT termina antes: checkpoint anticipado y declarar tiempo real. 
- CI dirigida por lote funcional; CI completa al cerrar etapa. Reutilizar éxitos sobre SHA exacto. Registrar para cada Actions ID, SHA, motivo, timestamp inicio/fin, duración observada, resultado y XML (solo si disponible). No confundir duración con minutos facturados.
- Guardar en el checkpoint último HEAD, stage 8 criterios, decisiones ya aprobadas, writer NONE/lease, Actions pendientes, próximos pasos y desglose medido: desarrollo, investigación, CI, espera, coordinación/checkpoint y tiempo no ejecutado.
- V2 OFF; S3/S4 sin abrir; T4 físico pendiente; `main` y repo histórico `etacsantafe-source/FARO` intactos. No tocar secretos, permisos, visibilidad, firmar, publicar, pagar ni hacer merge.
- Al final de última sesión (B 21:10, antes de 21:40) o cierre anticipado, informe final y desactivar A/B. No crear tercera tarea de informe. Los horarios son objetivos de disparo, no garantizan 30 minutos de cómputo.

## Handoff inmediato
Antes de actuar leer este permiso, estado real de rama V1.2 y sólo los archivos afectados; iniciar prioridad F1 Android trusted bootstrap, pruebas dirigidas por lote funcional, checkpoint pequeño y trazable, no certificaciones falsas.
