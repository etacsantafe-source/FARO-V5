# FARO V5 — W05-A2C1 · Noche Lite, runbook acotado

**Objetivo exclusivo:** A2C1A → B → C → D → E → F en desarrollo aislado. Baseline `6cbee2adc887c62e931765eba5c4f5d31747e89b`.
**Estado:** A2C1A B7 PASS DEV (8/8) y guardia de permiso consumido 45926448108 SUCCESS; A2C1B B1–B5 guardia 45926633918 SUCCESS, permiso exact4 READY publicado, exigir guardia del commit de permiso antes de B6. Solo desarrollo aislado. El scheduler no constituye permiso.

## En cada ejecución
1. Leer AGENTS.md, puntero, permiso y guardia; verificar rama y HEAD real.
2. Leer `faro-v5-writer-lock:writer.json` desde rama dedicada; solo adquirir writer con CAS del ref si RELEASED. Si HELD ajeno o UNKNOWN, exclusivamente READ_ONLY. Nunca hacer takeover.
3. La primera unidad admitible es A2C1A; A2C1B–F son dependientes de B7 precedente, no autorizados por lotes.
4. Verificar B1–B5 con evidencia pública, guardia del documento, permiso READY de rutas exactas y preflight por ruta. Solo entonces B6. Verificar el writer y HEAD antes de cada mutación; no mantener writer durante CI.
5. B7 requiere resultado CI exacto y XML original (>0 tests, 0 failure/error/skip), lectura independiente y guardia. RUNNING/WAIT_CI no es PASS.
6. Ante rojo, diagnosticar dentro del scope y dejar RED_FIX; si dependencias bloqueadas, no saltarlas ni iniciar A2D/A2E. Evitar reintentos de Apply.
7. Registrar `scheduled_start`, `actual_start`, `actual_end`, `effective_seconds_verified`, HEAD, unidad/bloque, rutas, CI/XML/guardia, tests PASS/FAIL, correcciones, bloqueos, writer y B7 verificados. Datos desconocidos quedan null, nunca estimados.
8. Liberar writer legítimamente con token propio, confirmar ref RELEASED, guardar checkpoint, sin tocar producción/stock real/pagos/FARO Auto histórico.

## Piloto
Tres tareas, ocho ejecuciones: T1 22:45/23:45/00:45, T2 23:00/00:00/01:00, T3 (primera adelantada manualmente)/00:15 (America/Argentina/Cordoba). No garantizan 15 min efectivos por disparo. Si A2C1 acaba, restantes solo verifican cierre. Comparar productividad por hora efectiva, porcentaje de turnos con B7 y bloqueos solo a partir de datos observados.
