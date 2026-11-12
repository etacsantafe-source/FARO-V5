# FARO V5 — Work Turbo cadena secuencial con un solo writer · 2026-11-11

## Motivación y decisión aprobada
El usuario pidió encadenar tareas mientras Work muestra aproximadamente 36% de cupo disponible y restablecimiento próximo. **Esto es una observación de UI, no dato verificable por GitHub ni permiso de evadir límites**. El objetivo es minimizar handoffs y no permitir reejecución de trabajo ya certificado.

## Cola en orden
| Índice | Unidad | Estado a la publicación | Acción autorizable |
| --- | --- | --- | --- |
| 0 | X03 U01/U02 | **B7 PASS SCOPED** | No repetir. X03 global OPEN: respuesta live Groq Free e integración productiva quedan pendientes de verificación humana; no bloquear tareas independientes. |
| 1 | **X09 — Review explícito sin Apply** | B1–B5 preparados, esperando permiso READY individual | Work B6/CI API35/XML/APK/B7 desde paquete `docs/s3-night/S3_A5_7_X09_WORK_EXEC_PACKAGE_20261111.json`. |
| 2 | **X10 — Apply/receipts** | Preparación READ_ONLY `docs/s3-night/S3_A5_7_X10_READ_ONLY_PREPARATION_20261111.md` | Después de X09 B7, completar B1–B5, scope y permiso independiente; B6 sólo si guard/READY PASS. |
| 3 | **X01 — Actividad** | PASS scoped parcial existente | Evaluar únicamente brechas nuevas de receipts COMMITTED y origen, B1–B5 delta y permiso individual antes de B6. |
| 4 | **X02 — Avisos/Pendientes** | PASS scoped parcial existente | Completar fuentes/estados reales y autorizar individualmente. |
| 5 | X06 → X08 → X12 | Evidencia parcial | Reusar pruebas previas, autorizaciones individuales, cierre funcional dirigido. |
| 6 | Aceptación global X01–X12 + G15 | NO RUN | Una sola campaña física final, sin inventar PASS productivo. |

## Regla mecánica de encadenamiento
1. Recuperar HEAD, writer CAS y permiso. **Nunca dos READY de código simultáneos.**
2. Si unidad ya tiene B7 scoped exacto + HEAD/evidencia válida, saltar sin recompilar; no consumir cuota.
3. Si paquete PREAUDITED y permiso READY individual, **Work prioriza B6, compilación mínima, CI exacta, diagnóstico causal y B7**.
4. Si falta permiso de la unidad siguiente, Work puede completar B1–B5 independiente, publicar docs, esperar guard y emitir READY solo tras verificar; **sin inventar seguridad ni hacer código fuera de scope**.
5. Cada unidad concluye con B7 independiente, estado permiso BLOCKED/consumido, checkpoint, writer RELEASED; recién después, nueva unidad.
6. Durante CI, preparar la siguiente solo READ_ONLY; nunca dos writers ni dos funciones bajo el mismo permiso. Si el turno se corta por cuota, last_checkpoint + next_action exacta y writer RELEASED permiten reanudación.
7. En `NEEDS_PREP`, registrar tres datos: **ruta faltante, motivo técnico y menor delta posible**. Si otra unidad está lista e independiente, pasar sólo cuando writer está RELEASED y se respete orden/dependencias. No ignorar CI roja de código existente.
8. X03 global no puede reclamarse PASS mientras no exista llamada live gratuita y validación independiente. Sin cuentas pagas, secretos en CI, ni Internet V2 DEV.

**Métrica**: B7 PASS scoped reales / CI dirigidas / RED_FIX causales; no inventar duración de Work ni inferir tokens exactos del 36% reportado. Resumen final: avance, pruebas, último SHA, writer y semáforo.
