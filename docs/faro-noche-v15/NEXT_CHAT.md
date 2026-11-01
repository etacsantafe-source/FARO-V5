# FARO V5 — Continuación en un chat nuevo

## Texto para activar (sólo cuando el usuario lo envíe)
```text
FARO V5 — ACTIVAR NOCHE V1.5

Autorizo activar ahora la ejecución autónoma preparada en:
etacsantafe-source/FARO-V5
rama: work/faro-noche-v15-20261101

Leé y seguí:
- docs/faro-noche-v15/AUTH_PROTOCOL.md
- docs/faro-noche-v15/RUN_STATE.json
- docs/faro-noche-v15/BACKLOG_READ_ONLY.md
- docs/S2_A6_BATCHED_PHYSICAL_T4_DECISION_20261031.md
- docs/FARO_V5_F3_S2_S5_DECISION_20261030.md

Revalidá hora Argentina, HEAD y writer real. Si ya son las 06:00 del 01/11/2026, no iniciar. Crear dos automatizaciones horarias A y B desfasadas 30 minutos (una hora por tarea como máximo) y el informe final READ_ONLY a las 06:00; no extender vencimiento ni reiniciar tareas viejas.

Orden excluyente: F2 hasta terminar todo lo automatizable, pasar a F3 sólo cuando F2 dependa únicamente de T4/humano; luego F3 hasta READY_FOR_T4, y F4 con igual regla. No escribir en main, ni V1/V4.4, ni S3/S4. V2 OFF y pruebas físicas al T4 agrupado.

Mientras GitHub Actions o writer estén ocupados, preparar tareas útiles READ_ONLY, con un único writer y CAS exacto; no repetir auditorías ni CI innecesaria. Verificar SHA y XML. Medir trabajo, investigación, CI, esperas, inactividad y coordinación realmente observados.

Detener definitivamente a las 06:00 del 01/11/2026 o al concluir el trabajo autónomo de S2.A6, lo primero que ocurra. Desactivar A/B y entregar informe final con qué falta.
```

## Preparado, sin activación
- Todos los documentos en rama exclusiva de Noche V1.5.
- Ninguna tarea A, B ni informe ha sido creada por esta preparación.
- Respetar la intención del usuario: configurar ahora, iniciar **sólo en el chat siguiente**.
