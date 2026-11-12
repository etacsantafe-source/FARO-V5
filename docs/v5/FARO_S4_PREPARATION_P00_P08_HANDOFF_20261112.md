# FARO V5 — Plan maestro DURABLE de preparación S4 (P00–P08)
Fecha: 2026-11-12 (Argentina). Modo: DOCUMENTAL / PLANIFICACIÓN. **S4 FORMAL OFF**.
Repositorio: `etacsantafe-source/FARO-V5`; rama de trabajo: `work/s3-night-20261102`.
Baseline leído antes de publicar: `fbc2c1a15029b7ebe156332958043ce5f2747ef8`, writer RELEASED epoch 842. Este baseline es histórico después del commit: siempre recuperar HEAD vivo.

## Propósito y límites
El usuario pidió reorganizar el debate sobre S4, recuperar la investigación y las decisiones previas, y empezar **P00 de nuevo en un chat nuevo**. Este documento sirve de índice durable, NO es la ejecución ni la certificación P00.
**No crear permisos READY, no programar, no abrir S4, no cerrar S3, no cambiar el contrato de menú, no tocar main, V4.4, secretos ni productiva V2.**
Trabajo próximo: P00 READ_ONLY. Los paquetes P01–P08 son una propuesta de secuencia; no constituyen contratos de alcance aprobados.
No duplicar tests/gates B7 ya certificados. No inferir PASS físico del Moto G15 ni Groq real.

## P00–P08 — Orden y entregables
| Unidad | Qué se hace | Producto de salida | Estado al publicarse |
|---|---|---|---|
| P00 — Recuperación de decisiones | Leer fuentes de autoridad, entrevistas/decisiones anteriores y conflictos; clasificar APROBADA/PROPUESTA/PENDIENTE/RECHAZADA | Matriz de decisiones con fuente concreta, versión, alcance y dudas | **NOT_STARTED: ejecutar de nuevo** |
| P01 — Inventario de V5 | Recorrer owners, Reality, contracts, operaciones, Search, UI, storage, permisos, tests y CI; diferenciar código/contrato/host/UI/Android | Inventario IMPLEMENTADO/PARCIAL/SOLO_CONTRATO/AUSENTE con evidencia por ruta y SHA | NOT_STARTED |
| P02 — Auditoría V4.4 READ_ONLY | Finanzas, Objetivos, Asuntos, Radar, SmartCompra, Cocina, proyecciones, menú, UX, tests y fallos | Matriz de capacidades reales, reglas, regresiones y pruebas legadas | NOT_STARTED |
| P03 — Delta V4.4 ↔ V5 | Comparar inventarios: CONSERVAR/ADAPTAR/REIMPLEMENTAR/DESCARTAR/INVESTIGAR, separar compatibilidad del runtime | Matriz reutilización, owners y dependencias | NOT_STARTED |
| P04 — Benchmark delta | Reutilizar benchmarks externos ya documentados; investigar solo huecos técnicos/funcionales, licencias y costes | Referentes y opciones con decisión motivada + pruebas negativas | PRELIMINARY_RESEARCH_EXISTS, NOT_CLOSED |
| P05 — Definición funcional | Especificar Finanzas, Objetivos, Asuntos, SmartCompra/Cocina y lugar de Radar; estados, errores y escenarios | Flujos completos y contratos de producto para aprobación humana | NOT_STARTED |
| P06 — UX final | Conservar drawer V4.4 D-491/D-493, contrastar UI V5, simplificar módulos, iconografía, accesibilidad y estados de fallo | Especificación UX y recorridos por módulo, sin código | NOT_STARTED |
| P07 — Integración y predicción | Dueños de datos y relaciones cruzadas, proyección móvil 6 meses, simulación ≠ Reality, permisos/undo/recibos/recovery, offline | Contratos transversales y pruebas adversariales | NOT_STARTED |
| P08 — Paquetes para Work | Auditar alcance, permisos, límites, B1–B5 por unidad, matriz aceptación y pruebas físicas finales | Documentación READY_TO_REVIEW, **NO** permiso funcional automático | NOT_STARTED |

## Decisiones del usuario a preservar como insumos P00
- Producto completo, con UX sencilla, práctica e intuitiva. Las funcionalidades se entregan por etapas sin recortar la visión.
- Opción A: al abrir, mostrar directamente **Chat**. Menú lateral único como acceso principal; **sin accesos rápidos duplicados** en la pantalla Chat.
- Recuperar y mejorar únicamente con evidencia el menú de **V4.4** y sus iconos con nombres; las APK de desarrollo NO son diseño final.
- El drawer V4.4 ya fue objeto de D-491/D-493 en el repo histórico: cabecera FARO+búsqueda; secciones Inicio, Finanzas, Objetivos, Asuntos, SmartCompra; Fijados; Recientes hasta 7; pie Nuevo chat y Ajustes. El Chat no figura como sección redundante; Radar integrado en Chat/Asuntos. Confirmar vigencia/alcance en P00, no reinventar.
- **Proyección operativa móvil de 6 meses**, sin borrar, ignorar ni recortar compromisos posteriores; cuotas largas y datos históricos conservados. Distinguir REAL/COMPROMETIDO/ESTIMADO/SIMULADO; primer mes detallado y otros cinco más sintéticos es preferencia a contrastar contractualmente. Diferenciar escenario hipotético de transacción.
- Cada módulo debe funcionar manualmente sin IA; la IA propone, usuario revisa, FARO ejecuta bajo autoridad pertinente. Offline funcional básico y sin APIs pagas obligatorias.
- El árbol inicial S4 «A1 Finanzas, A2 Objetivos, A3 Asuntos, A4 SmartCompra, A5 Integración» es SOLO PROPUESTA, **no contrato congelado**.
- No crear un módulo Proyecciones separado de manera prematura: evaluar un motor común presentado en Finanzas y utilizado por los demás owners. No fijar alcance hasta P01–P07.

## Fuentes precisas que deben recuperarse en P00
Repositorio actual `etacsantafe-source/FARO-V5`:
- `AGENTS.md`, `docs/v5/FARO_CONTINUITY_POINTER.json`, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`;
- `docs/v5/FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md`;
- `docs/v5/FARO_V2_MINIMUM_CORE_UNBLOCK_PLAN_20261104.md`;
- `docs/v5/FARO_S3_BENCHMARK_APPS_20261102.md`;
- `docs/s3-night/S3_A5_7_ACCEPTANCE_MATRIX_20261112.md`;
- `v5/owner/finance`, `v5/owner/objectives`, `v5/owner/matters`, `v5/store/reality`, `v5/surface`.
Repositorio histórico `etacsantafe-source/FARO` **READ_ONLY**:
- `docs/v5/V5_S1_DRAWER_PRODUCT_CONTRACT.md` — D-491/D-493;
- `docs/v5/V5_S1_DRAWER_V44_PARITY_RESEARCH.md`;
- `app-v4/src/main/kotlin/ar/com/faro/v4/FaroV4Shell.kt`;
- rutas `app-v4/src/main/kotlin/ar/com/faro/v4/{finance,objectives,radar,smartcompra}` y tests;
- documentos originales de Cocina, Finanzas, proyecciones y UX que se localizarán en P00/P02.
**No inferir fuente contractual de un recuerdo conversacional sin lectura de respaldo.**

## Protocolo B1–B7 de la unidad DOCUMENTAL `S4_PREP_P00_P08_HANDOFF_20261112`
### B1 — Evidencia
HEAD original `fbc2c1a15029b7ebe156332958043ce5f2747ef8`; pointer antes del cambio: S3 OPEN, S4 OFF; writer RELEASED epoch 842. Permiso funcional vigente BLOCKED para la unidad S3 anterior; no se amplía. Backlog S3 y certificaciones no se modifican. Criterio: documento accesible desde pointer y retomable por chat nuevo desde P00.
### B2 — V4.4 y reutilización
D-491/D-493 ya documentan el drawer. Se conserva el contrato de UX; este handoff no propone reescribir V4.4 ni duplicar auditorías. P02 debe investigar otros dominios funcionales sin copiar su runtime.
### B3 — Benchmark externo
Se reutiliza `docs/v5/FARO_S3_BENCHMARK_APPS_20261102.md`, con referencias primarias a Obsidian, Notion, Keep, Todoist, Anytype y Android. Hallazgos exploratorios posteriores de Finanzas/Compras/organización incluyen Actual Budget, Firefly III, KitchenOwl, Grocy, Vikunja y Expense Manager, **no verificados como implementación de FARO**. P04 auditará pertinencia, mantenimiento, licencias, offline, complejidad y alternativas antes de autorizar código; no importar código ajeno.
### B4 — Unificación
En lugar de abrir S4 o emitir permisos, publicar UN documento índice y agregar una referencia de planificación al pointer, manteniendo todas las señales de S3 vigentes. P00 será la única unidad siguiente. No crear repositorios/ramas, dueños ni motores nuevos.
### B5 — Auditoría adversarial previa
Amenazas: tratar propuestas como aprobadas, alterar el siguiente objetivo S3, perder contexto al mudar chat, falsear PASS, importar código con licencias conflictivas, crear navegación redundante, abrir V2 productiva, eludir writer CAS. Controles: plan con estados NOT_STARTED, referencia explícita de autoridad, cambios **solo docs/v5**, no permisos READY, no código ni workflows, no CI Android ni físico, writer global CAS y verificación remota + release. Si pointer no se publica, cierre INCOMPLETE.

### B6/B7
B6: publicación documental bajo writer único y CAS, más actualización **posterior** del pointer.
B7: comparar diff remoto y comprobar archivo/pointer/head/writer RELEASED. Hasta obtener evidencias, **NO afirmar PASS**. Sin cambios funcionales, CI Android: NOT_APPLICABLE. Guardia de protocolo: verificar si hay ejecución; no inventar SUCCESS.

## Instrucción exacta para un nuevo chat
«Continuar FARO V5 — PREPARACIÓN S4. Leer primero AGENTS.md, pointer y `docs/v5/FARO_S4_PREPARATION_P00_P08_HANDOFF_20261112.md`. Comenzar **P00 desde cero, READ_ONLY**. Recuperar y clasificar decisiones de fuentes reales. No avanzar a P01, no programar ni abrir S4, no tocar writer sin trabajo de escritura documental expresamente autorizado. Reportar progreso, pruebas, writer, checkpoint y termómetro.»

## Estado declarado de S3, no reabrir ni inflar
S2 CLOSED 8/8; S3 formal 0/5 (el avance de desarrollo ~40% que figura en snapshots de 04/11 es histórico y ya no actualizado); S3/A5 integración global y Groq live/physical OPEN. S4 OFF; V2 productiva OFF; Auto/Noche OFF; `main` y V4.4 intactas. El único propósito de este documento es hacer durable la secuencia de estudio.
