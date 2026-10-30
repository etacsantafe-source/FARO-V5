# FARO V5 — CIERRE Noche V1.1 · piloto autorizado 2026-10-30 (3 h)
Estado: PAUSED_BY_USER / ALL_THREE_TASKS_DISABLED / NO_AUTOMATIC_RESTART. Esta autorización histórica no habilita reactivación ni extensión de la ventana.

## Alcance, reloj y repositorio
- Autorización textual del usuario en conversación 2026-10-30: "NUEVO PILOTO CIERRE NOCHE ... hasta 3 horas o completar S2.A6".
- Repo privado exclusivo: etacsantafe-source/FARO-V5.
- Nueva rama exclusiva de escritura: work/cierre-noche-v11-pilot-20261030, derivada del piloto anterior cerrado @ b2ff3466b4af7ba598b7edcb34bc4c5dd34cab1a.
- Rama documental de referencia: docs/cierre-noche-v1-20261030, contrato docs/cierre-noche/PROTOCOL_V1.md V1.1.
- Huso: America/Argentina/Buenos_Aires (-03).
- Inicio 2026-10-30 15:00:00 -03. Corte absoluto 2026-10-30 18:00:00 -03 o S2.A6 8/8 CLOSED/PASS con evidencia terminal, lo primero. Si solo resta T4 físico, detener escrituras y dejarlo pendiente. Nunca renovar permiso por rutina.
- Noche A programada 15:00, 16:00, 17:00; Noche B 15:30, 16:30, 17:30; como máximo 30 min de cada turno. Informe final READ_ONLY a las 18:10 -03 si no se adelanta.
- Prohibido solapar escritores, incluso si una sesión se retrasa: preflight falla cerrado. Cada ejecución tiene obligación de comprobar ausencia de otra instancia, epoch/lease y GitHub HEAD exacto; usar escrituras condicionadas con CAS, no force-push.
- Sesión minuto 0–20: recuperar, investigar, implementar mínimo, Actions dirigida y encadenar VERDES; 20–25: ningún nuevo trabajo ni Actions, recoger en curso; al minuto 25: registrar run IDs + SHA de Actions sin terminar como WAIT_CI, NO cancelar ni esperar activamente; 25–30: checkpoint durable y writer NONE, terminar. Si arranca tarde, respetar límites de corte y reducir trabajo.
- Si S2.A6 se certifica antes, desactivar ambas tareas programadas y adelantar informe si la herramienta lo permite. Si no es posible adelantarlo, dejarlo agendado y realizar informe READ_ONLY.
- F3 consentimiento/Settings (S2 vs S5): DECISIÓN HUMANA RESUELTA, opción A aprobada el 30/10/2026; ver `docs/FARO_V5_F3_S2_S5_DECISION_20261030.md`. F3 continúa pendiente de implementación/certificación, sin sumar al 3/8. T4 físico = NOT_RUN. Mantener V2 OFF, S3/S4 sin abrir, main intacta y histórico etacsantafe-source/FARO READ_ONLY; no tocar FARO Auto, V4.4, V1, secretos, gastos, permisos o visibilidad; sin merges ni publicación ni Termux.
- CI con permissions contents:read; resultados sólo certificados por run terminal, SHA y XML preciso. JVM verde no equivale a Android E2E. Ante ROJO diagnosticar y probar hipótesis nueva sin bucle; si bloquea, continuar frente independiente seguro.
- Antes de cada ciclo leer este permiso y el estado real de GitHub, no asumir que un checkpoint es live.
- Reportar estado real S2.A6 y criterios congelados 1–8, evidencia, bloqueos F1/F2/F3/F4/T4, writer, HEAD y Actions.
- Condición de seguridad: si no hay comprobación de exclusividad/tiempo/permiso, READ_ONLY. Autorizar tareas programadas no equivale a garantizar 30 minutos de trabajo continuo.

## Programaciones verificadas por herramienta
- Noche A (PAUSADA por el usuario): programación histórica 2026-10-30 15:00, 16:00, 17:00 -03, HOURLY COUNT=3.
- Noche B (PAUSADA por el usuario): programación histórica 2026-10-30 15:30, 16:30, 17:30 -03, HOURLY COUNT=3.
- Informe final (PAUSADO por el usuario, READ_ONLY): programación histórica 2026-10-30 18:10 -03.
- El cron registra intenciones de invocación, NO acredita ejecución efectiva ni continuidad de 30 minutos; verificar resultados reales en cada checkpoint.
- Tareas históricas anteriores verificadas desactivadas al preparar el piloto. No reactivar ninguna.

## Decisión posterior y pausa
- El usuario solicitó pausar las tres tareas antes de resolver F3 y autorizó opción A. Se verificó Noche A=false, Noche B=false, Informe Final=false en Automations.
- Los tres horarios arriba son solamente el registro histórico de programación; **no activar, no crear nuevas ejecuciones, no ampliar la ventana automáticamente**. Para un nuevo piloto se requieren horarios/permiso nuevos.
- Decisión normativa F3: [FARO_V5_F3_S2_S5_DECISION_20261030.md](FARO_V5_F3_S2_S5_DECISION_20261030.md). S2 implementa flujo mínimo exportar/restaurar con confirmación y SAF; S5 presenta la sección definitiva de Ajustes. No abrir S5 ahora. S2.A6 OPEN, 3/8 hasta evidencia completa.
