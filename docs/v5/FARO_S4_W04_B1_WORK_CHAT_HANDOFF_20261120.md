# FARO V5 — W04 B1 · Work Turbo + continuidad Chat (PREPARACION DOC)
Fecha local 2026-11-20. Repo `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`. Baseline 5cc67fc5a3bc0bcfe1f3246c746b812dfc55a294.
**Objetivo**: encadenar las unidades de B1 sobre Household10 sin repetir B0. Documento de relevo, no permiso por sí mismo.

## Releer antes de cada escritura
`AGENTS.md`, `docs/v5/FARO_CONTINUITY_POINTER.json`, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`, `docs/v5/FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md`, `docs/v5/FARO_S4_W04_B0_HOUSEHOLD10_GLOBAL_B7_PASS_20261120.md`, este handoff y:
- `docs/v5/FARO_S4_W04_B1A_HOUSEHOLD10_ACTIVE_READ_B1_B5_20261120.md` — B1A ACTIVE10 READ (primero).
- `docs/v5/FARO_S4_W04_B1B_B1D_NEEDS_OPERATIONS_B1_B5_20261120.md` — B1B persistencia CAS, B1C1 Review, B1C2 Apply/receipt, B1D Undo/historial.
- `docs/v5/FARO_S4_W04_C0_PRICE_READ_ADVANCE_20261120.md` — repaso C0 Evidence y riesgos que NO son B1.
Las referencias a HEAD/epoch en documentos son antecedentes; **consultar siempre el HEAD remoto vivo y el lock CAS**.

## Orden obligatorio
1. **B1A primero**: si `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` está READY exact5 y guardia SUCCESS, ejecutar solo las cinco rutas del permiso para ACTIVE10 DEV read; prueba JVM XML, guard, B7, consumir READY y RELEASED. Si aún BLOCKED, verificar preauditoría y guardia, emitir READY DOC_ONLY con cinco rutas y preflight. No se habilitan mutaciones en A.
2. **B1B**: revalidar `docs/v5/FARO_S4_W04_B1B_B1D_NEEDS_OPERATIONS_B1_B5_20261120.md` B1–B5 contra el código B1A y congelar 6 rutas candidatas. Guardia, READY y B6 solo después. CRUD real sobre la misma Reality10, CAS de revision, snapshots, sin duplicar writer/DB, sin recibos artificiales. B7.
3. **B1C1**: review tipado de cuatro acciones sin Apply, entrada humana y fingerprint. Rutas EXACTAS nuevo delta; B1–B5/guard/READY/B6/B7.
4. **B1C2**: Apply real bajo foreground y grant Household específico, receipt + commit de necesidades en una sola RealityTransactionScope; recovery por receipt. No usar grant de Finanzas9 ni inventar un reader externo. B1–B7 propios.
5. **B1D**: Undo mediante nuevo Review/Apply, historial verificable y prueba con backup FBS2v6 tras mutaciones reales. Si falta preimagen durable, dividir en subunidad explícita, no insertar tabla furtiva. B7 y cierre integral B1.
6. Después de B1 B7, revalidar `docs/v5/FARO_S4_W04_C0_PRICE_READ_ADVANCE_20261120.md` y preparar W04-C0. C/D/E/F, W05 y W06 NO se autorizan por herencia.

## Gates y entregables
- B0 A+B0A–B0D2 B7 PASS DEV en `5cc67fc5...`, guard global 44729681979 SUCCESS; NO repetir.
- Todo commit funcional debe llevar `FARO-UNIT:<unit>`, estar bajo un solo READY exact-path preexistente y writer CAS libre, con B7 antes del siguiente funcional.
- Verificar con ejecución JVM/Android cuando aplique, JUnit XML, guard SHA exacto; fallos ⇒ RED_FIX y corregir únicamente delta.
- Writer ajeno HELD ⇒ READ_ONLY, prohibido takeover. RELEASED después de cada commit y antes de esperar CI. Ningún secret, API paga, Internet obligatorio, pagos/stock/movimientos Finance ni V2 productiva.
- No repetir auditorías B1–B5 ya demostradas: hacer **delta de código/HEAD** y comprobar guard vigente; si cambia el diseño, documentar solo el cambio.
- Work prioriza código/CI/Android; Chat puede investigar y publicar evidencias DOC_ONLY cuando writer libre. Nunca paralelizar sesiones que escriben.
- Informar al terminar SHA, unidad, permisos, XML, RUN, checkpoint y writer RELEASED; dejar continuidad para sesión sin créditos.
