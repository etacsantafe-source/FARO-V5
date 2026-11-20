# FARO V5 — W04 · Relevo Work / Chat después de B1–B5
Fecha local 2026-11-19; baseline `20646219d53753cbd4129e5e732d4cf1a782387f`; trabajo **DOC_ONLY**. Autorizado por usuario: comenzar W4 con toda preauditoría primero; dejar preparado para Work y continuación manual sin créditos. No escribir código en este turno.
## Recuperación
Repo `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`. Leer `AGENTS.md`, puntero `docs/v5/FARO_CONTINUITY_POINTER.json`, permiso `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`, protocolo B1–B7 `docs/v5/FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md` y auditoría `docs/v5/FARO_S4_W04_A_HOUSEHOLD_NEEDS_B1_B5_20261119.md`. Revalidar HEAD, writer CAS remoto, CI y rutas ANTES de cada cambio. No repetir W01/W02/W03 B7.
## Secuencia inmediata, NO AUTO
1. Verificar que el checkpoint documental W04-A está en HEAD y que `seven-block-evidence` del SHA de documentación terminó SUCCESS. Releer B1–B5 y manifestar su alcance exacto. Si no hay guard verde, NO READY.
2. Emitir en commit DOC_ONLY un permiso READY **solo** para `S4_W04_A_HOUSEHOLD_NEEDS_MODEL_DEV`, 5 rutas enumeradas exactamente en B4 y evidencia B1–B5 con blob SHA real. No incluir otras rutas/owners; verificar guard y preflight por ruta. Adquirir y RELEASE writer entre commits/CI.
3. B6 de A: módulo `:owner:household` con modelo tipado puro, tests JVM y workflow CI. Prohibidos SQLite/migraciones/host/App/Finance/stock/pagos/Internet en A. Esperar CI exacta; verificar logs y XML, no cifras supuestas. B7 independiente PASS o RED_FIX y checkpoint; consumir permiso READY→BLOCKED, RELEASE writer.
4. Continuar W04-B necesidades persistentes solo con SU PROPIO B1–B5, rutas y READY: modelar schema/migración Reality9 segura, ACL/epoch, única transacción/journal/receipt/Undo/recovery, sin segundo escritor ni producción. Reusar tests antiguos solo como evidencia; añadir pruebas del delta.
5. W04-C evidencia comercial de precios con provenance/frescura; W04-D carrito/presupuesto/promos; W04-E UI nativa DEV + Android API35; cierre integral W04-F B7. Cada unidad requiere B1–B7 específicos, sin permisos generales ni trabajo duplicado. No activar W05 ni W06/W07 por herencia.
## División trabajo sin créditos
Chat puede recuperar/depurar B1–B5, revisar código/CI y redactar documentos acotados mediante GitHub, si writer está libre. Work es útil para B6 de A y B–E, ejecución Gradle/Android emulador y corrección de errores. Los dos usan MISMA rama y writer CAS, jamás en paralelo.
## GATES verificables
- Writer HELD de otro owner: READ_ONLY, sin takeover. Writer UNKNOWN: STOP.
- Guardia roja: STOP y corregir evidencia sin código.
- W03-F OS, V2 productiva, main, V4.4, Auto/Noche, G15 físico, APIs pagas: OFF/NOT_RUN.
- W04 no es pago: Household gestiona necesidades, ofertas, presupuestos; Finance maneja dinero. No compra automática, no stock físico hasta recepción W05, no transacción doble Household+Finance hasta W06 PASS.
- Cierre solo con SHA exacto, logs/XML, checksum si hay artefacto, checkpoint y writer RELEASED.
