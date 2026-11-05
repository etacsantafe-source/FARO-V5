# FARO V5 — S3.A1, B1/B2 recuperación acotada · 05/11/2026 turno A 15:00

**Alcance:** investigación documental y handoff. **No hay permiso B1–B5 específico de S3.A1; B6 funcional NO AUTORIZADO.** Este registro no declara S3.A1 cerrada ni una nueva CI PASS.

## B1: evidencia viva
- Rama `work/s3-night-20261102`; HEAD leído antes del turno: `a5d6d0401d3593bf0c571a2fe90e5a3213b08bd2`.
- Writer inicial `RELEASED` epoch 275; cola `ENABLED`, autorización 05/11 hasta 19:00 AR. La adquisición y liberación de este turno deben corroborarse en la rama del writer, no por este texto.
- `docs/s3-night/RUN_STATE.json`: A1 parcial; Home tiene cuatro indicadores `Pendientes/Avisos/Actividad reciente/Guardados`; **solo Avisos N032** posee owner activo y invalidación de readiness, notice commit, cierre y puntero activo verificados en pruebas dirigidas. No declarar los otros tres como cero.
- N015: existe `RealityStore` de receipts `COMMITTED` con cursor lexicográfico y fence transaccional, CI #41811354035 2/2 para SHA histórico `948d6201`. **Faltan** federación de historial, tiempo de commit verificable, read admission/ACL y binding a Home. No inferir cronología desde ID.
- N031: proyección pura de `Matter` con CI #41811626933 4/4, SHA `760ce23b`; **falta** owner persistente de planning/recordatorios y autoridad para contar pendientes. La proyección aislada no equivale al owner real.
- Guardados: ocho operaciones candidatas no aprobadas; catálogo oficial 172. Home Guardados sigue `UNAVAILABLE`, no inventar bookmark count.
- S3.A2 está `CLOSED_AUTOMATED_SCOPE` G1–G5 5/5 (G5 Android emulador #42071443306 1/1); X01–X12 y única campaña Moto G15 se difieren a S3.A5, **NOT_RUN**.
- `FARO_PROTOCOL_WORK_PERMIT.json` actualmente autoriza únicamente la unidad histórica `S3_A2_G5_ANDROID_EMULATOR_DIRECTED` y sus rutas. **No reutilizar ese READY para editar A1**.

## B2: comparación específica V4.4, solo lectura
- V4.4 `FaroV4FollowUpGateway.kt` y `MatterService.pending`: cálculo real desde asuntos/recordatorios/atención, excluyendo resueltos y snoozed. En V5 la política N031 no prueba el owner persistente; **ADAPTAR semántica, no copiar DB ni convertir workspace pending review en pendientes**.
- V4.4 `FaroV4GlobalHistoryGateway.kt`: historial filtrado y paginado de receipts. En V5 el lookup/índice Reality es parcial y no demuestra historial federado, orden temporal ni read authority. **ADAPTAR cursor seguro y procedencia, no forzar conteo sintético**.
- V4.4 `FaroV4HomeSurface.kt` refresca radar al abrir. V5 `home.read` es read-only: **DESCARTAR refresco con efectos al montar Home**.
- Comparación externa pertinente ya documentada en `docs/s3-night/S3_A2_G1_B3_EXTERNAL_REAL_WORLD_RESEARCH_20261104.md`: OWASP denegar por defecto, Android single source of truth, Room multi-instance invalidation no sustituye ACL. **Referencia reutilizable**, pero no sustituye B3 específico A1 con fuentes y revisión adversarial de la nueva unidad.

## Hipótesis comprobables y B3–B5 pendientes
1. N015 solo podrá alimentar Actividad cuando owner exponga read model estable con identidad de generación, filtros, cursor, procedencia, cobertura y admisión ACL; test de receipt PREPARED, transacción no committed, revocación y cambio de generación.
2. N031 solo podrá alimentar Pendientes cuando owner canónico de planificación/recordatorios exponga revisión/autoridad y evento real; probar due/priority/snooze, timezone, ACL y restore. No sumar únicamente Matter.
3. Guardados requiere decisión humana bounded antes de ampliar operaciones; investigar alternativas dentro del catálogo 172 sin incorporar las 8 candidatas por defecto.
4. B3 exige referencias externas concretas y comparación de alternativas **para A1**; B4 unificar el mínimo; B5 matriz negativa antes de emitir un permiso READY de rutas A1. El siguiente turno puede completar B3–B5 o trabajar en otra unidad S3 segura; **no código funcional A1 sin permiso**.

**Estado:** B1/B2 `EVIDENCE_RECORDED`, B3/B4/B5 `PENDING`, B6/B7 `NOT_RUN`. S3 1/5 etapas cerradas en alcance automatizado, ~40% estimado histórico sin recalcular. No repetir suites históricas PASS. Tiempo productivo `NO_REGISTRADO` sin timestamps comparables.
