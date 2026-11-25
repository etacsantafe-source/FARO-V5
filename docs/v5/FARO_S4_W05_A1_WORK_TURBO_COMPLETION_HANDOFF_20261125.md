# FARO V5 — WORK TURBO · terminar W05-A1 · paquete preparado
Fecha 2026-11-24/25 Argentina. Branch `work/s3-night-20261102`. **Este documento es un handoff, NO inicia un Work ni prueba CI por sí solo.**

## Objetivo
Terminar W05-A1 con C2C0 Evidence2 journaled cutover, C2C1 Android ACTIVE reopen y A1C Review/Apply/receipt/Undo de recepción en DEV si se acredita el catálogo/permisos correspondientes; luego aceptación global A1 DEV. Código solo tras permiso READY por unidad. No repetir S2/S3, W01/W02/W03/W04 ni los B7 de W05 A0/A1A/A1B0–C2B1B.

## Inicio rápido obligatorio
1. Leer `AGENTS.md`, `docs/v5/FARO_CONTINUITY_POINTER.json`, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`, `docs/v5/FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md`, guard y writer remoto `faro-v5-writer-lock:writer.json`. Recuperar branch HEAD real, verificar pointer+writer target, no usar un SHA antiguo como permiso.
2. C2C0 preauditoría exacta: `docs/v5/FARO_S4_W05_A1B3C2C0_EVIDENCE2_JOURNALED_CUTOVER_B1_B5_20261125.md`. Si su permiso READY está vigente y guard documental SUCCESS, pasar directamente a **B6** en 3 rutas exactas, preflight en parent, código/CI/XML; no repetir B1–B5. Si permiso BLOCKED o HEAD cambió, reconciliar sin bypass y reemitir conforme a protocolo.
3. CI de C2C0: nueva suite 10/10, C1B Evidence1 8/8 y C2B1B Evidence2 8/8 = 26/26 (XML real, 0 failures/errors/skips), guard exact SHA. RED_FIX solo fallos reales. B7 independiente, checkpoint remoto, permiso consumido BLOCKED, writer RELEASED.
4. Luego C2C1 desde `docs/v5/FARO_S4_W05_A1_COMPLETION_NEXT_UNITS_PLAN_20261125.md`: actualizar solo delta preauditoría contra resultado C2C0, permiso READY propio, implementar y certificar Android DEV ACTIVE reopen. Después A1C0–A1C4 según auditorías y autorización real, terminar A1 global DEV. No asumir que C2C1 por sí solo concluye A1.
5. Si otra sesión toma writer, READ_ONLY y STOP de escrituras; jamás robar HELD. Una CI no autoriza cambiar branch/HEAD mientras se espera. No usar nuevas APIs pagas.
6. Reportar al final **HEAD exacto, unidades B7, CI y XML por SHA, guard, permit, checkpoint y writer RELEASED**. Si una unidad queda OPEN, marcarla y no declarar A1 terminado.

## Restricciones irrenunciables
V2 productiva OFF; stock real OFF; pagos OFF; Auto/Noche OFF; no main ni V4.4 escritos; sin operaciones bancarias externas; no activar Apply productivo ni mezcla Household↔Finance (W06 separado); no instalar APK ni operar G15 físico sin gate propio.

## Enlaces de control
Preauditoría C2C0: `docs/v5/FARO_S4_W05_A1B3C2C0_EVIDENCE2_JOURNALED_CUTOVER_B1_B5_20261125.md`. Siguientes: `docs/v5/FARO_S4_W05_A1_COMPLETION_NEXT_UNITS_PLAN_20261125.md`. Último B7: `docs/v5/FARO_S4_W05_A1B3C2B1B_B7_PASS_EVIDENCE2_V2_DURABLE_AUTH_XML22_22_20261125.md`. Última CI `45522706065` SUCCESS XML22/22; guard `45522706044` SUCCESS.