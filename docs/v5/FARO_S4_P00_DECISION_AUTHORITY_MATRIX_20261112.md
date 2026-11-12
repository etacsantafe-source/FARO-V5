# FARO V5 — P00 · Matriz canónica de decisiones y conflictos (preparación S4)
Fecha: 2026-11-12 (AR). Unidad: `S4_PREP_P00_DECISION_RECONCILIATION`.
Modo: **DOCUMENTAL**. S4 formal **OFF**. No crea contrato S4 funcional, autorización READY, cambios de código ni certificación física.

## B1 — Evidencia y autoridad
Baseline vivo al iniciar: `b3e578556acb2df38b6a7458640fa75829346257` (rama `work/s3-night-20261102`). Writer doc-only epoch 844 adquirido mediante CAS; permiso funcional `BLOCKED`. S2 CLOSED 8/8; S3 abierto (formal 0/5); A5 global, X03 Groq real y campaña física pendientes. V2 productiva, Auto, Noche y S4 OFF.
Fuentes inspeccionadas, en orden según objeto:
1. **Usuario, decisiones explícitas 2026-11-12 y anteriores**: producto completo entregado por etapas; opción A abre Chat; drawer único sin accesos rápidos redundantes; recuperar menú V4.4; predicción visible 6 meses sin perder compromisos posteriores. Fuente directa conversacional, no sustituye permiso de código ni una certificación.
2. **Gobernanza V5 actual**: `AGENTS.md`, `docs/v5/FARO_CONTINUITY_POINTER.json`, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`, `docs/v5/FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md`; handoff `docs/v5/FARO_S4_PREPARATION_P00_P08_HANDOFF_20261112.md` (blob `0f1a6b3c45c1bb0060b3d1ffac6c3011defb63f4`) y su B7 documental.
3. **Diseño V5 aprobado en repo histórico `etacsantafe-source/FARO`, rama `work/faro-v5-design-clean-20261019`**: `docs/v5/V5_S1_DRAWER_PRODUCT_CONTRACT.md` (D-491/D-493, blob `8586d3a5cf7f1a15170de878a63192f4b70f956b`); `docs/v5/V5_S1_DRAWER_V44_PARITY_RESEARCH.md` (D-492 y aprobación posterior D-493, blob `b4fd8d25632240236b0650199e39bc06126e94fa`); `docs/v5/V5_S_EXECUTION_METHOD.md` §6.1.
4. **Referencia V4.4, SOLO LECTURA**: `docs/v4/FARO_V4_4_AUTHORITY.md`, `docs/v4/archive/FARO_V4_4_PHASE_A_FROZEN_SOURCE_2026-10-11.txt`, `docs/v4/FARO_V4_4_DECISION_LOG.md`, `docs/v4/FARO_V4_4_C_UX_VISUAL_SCOPE.md` (blob `faeb1120922cd6323ea5e7524a6c0e9dcba66c87`), `app-v4/src/main/kotlin/ar/com/faro/v4/FaroV4Shell.kt` y `conversation/FaroV4ConversationWorkspace.kt` sobre referencia sana `bfa0ee71f5b9cd104baaadd71f1e77a61f203939`.
5. **V5 técnica, sin inventario exhaustivo**: `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt` (blob `e40ac8fcbf65014149cfd97a797166e55f40004f`), `docs/s3-night/S3_A5_7_ACCEPTANCE_MATRIX_20261112.md`, `docs/v5/FARO_S3_BENCHMARK_APPS_20261102.md`, `docs/v5/FARO_V2_MINIMUM_CORE_UNBLOCK_PLAN_20261104.md`.
La autoridad V4.4 rige lo que V4.4 hizo; **no reabre automáticamente la decisión más nueva y explícita de V5**. Entre propuesta, contrato aprobado y hecho probado, mantener categorías separadas. Esta lectura P00 no pretende probar funcionalidades V5, tarea P01/P02.

## Matriz de decisiones (identificador, estatus, evidencia, límite)
`APROBADA` = requisito o límite humano vigente en su alcance. `HEREDADA_V44` = baseline aprobado V4.4, candidata a recuperación V5 sin autorización funcional nueva. `PROPUESTA` = alternativa no aprobada. `PENDIENTE` = falta decisión/contrato o evidencia. `RECHAZADA` = alternativa excluida expresamente o incompatible con autoridad vigente.

| ID | Estado | Decisión / criterio | Fuente y alcance |
|---|---|---|---|
| P00-D01 | APROBADA | FARO completo como producto final, entrega gradual **sin reducir la visión a la APK DEV**; UX simple/práctica/intuitiva. | Usuario 12/11; handoff P00–P08. No congela cronograma ni árbol S4. |
| P00-D02 | APROBADA | Arranque visible **Chat**, opción A; módulos accesibles desde drawer principal. | Usuario 12/11; contratos Chat-first V5 D-491/D-493. No equivale a crear un chat nuevo en cada lanzamiento. |
| P00-D03 | APROBADA | **Drawer como navegación principal**, sin barra inferior ni atajos duplicados sobre Chat; iconos sencillos con nombres humanos. | Usuario 02/11 y 12/11; D-491/D-493. La disposición final V5 se compara en P06. |
| P00-D04 | APROBADA | Estructura D-491: cabecera FARO + acceso único lógico a búsqueda; secciones Inicio, Finanzas, Objetivos, Asuntos, SmartCompra; Fijados, Recientes; pie Nuevo chat y Ajustes. Chat no se agrega como fila redundante. | Contrato histórico D-491/D-493, vigente como destino UX. |
| P00-D05 | APROBADA | Hasta **7 recientes no fijados**, fijados fuera del cupo, Ver todos/Ocultar anteriores, archivados recuperables y sin registros duplicados. | D-493, P01/P02/P11; dueño Chat/Workspace, integración pendiente según unidad. |
| P00-D06 | APROBADA | Foco único sección **o** conversación; drawer desplazable y pie accesible; iconos + textos; semántica, rotación y fuente grande. | D-491/D-493; V4.4 C3 físico de su propia app, no PASS V5. |
| P00-D07 | APROBADA | Abrir chat actualiza selección y vuelve a Chat; acciones de conversación son contextuales (renombrar/fijar/buscar/archivar/eliminar confirmado), no batería de botones permanentes. | D-493 P03/P06/P09. Aplicación progresiva por owner. |
| P00-D08 | APROBADA | Nuevo chat real; cold start reutiliza **solo** el chat más reciente no archivado si está de verdad vacío; si no, crea uno nuevo; no rescata vacíos antiguos. | D-493 P05 y V4.4 C1/ConversationWorkspace; probar equivalencia V5, no copiar `chat-main`. |
| P00-D09 | APROBADA | Radar mantiene análisis transversal, pero **NO sección principal independiente en drawer V5**; resultados/contexto en Chat/Asuntos según su dueño. | D-491/D-493 más decisión usuario 12/11; supera navegación previa V4.4. |
| P00-D10 | APROBADA | Diferenciar conversación, candidato, aprobación, simulación y Reality; IA propone, humano revisa, FARO ejecuta por owner con Apply/receipts/Undo/recovery cuando corresponde. | Chat-first previo, AGENTS y protocolo V5; no auto-Apply. |
| P00-D11 | APROBADA | Funciones básicas de cada módulo deben servir manualmente y offline, sin IA remota obligatoria; sin API paga exigida. | Usuario, handoff y AGENTS. Egress sujeto a consentimiento y políticas. |
| P00-D12 | APROBADA | Horizonte **móvil de 6 meses** para visualización/predicción, conservando histórico y obligaciones/cuotificaciones más lejanas. El recorte de pantalla nunca borra, omite del sistema ni convierte a cero los meses posteriores. | Usuario 12/11; precedente V4.4 Horizonte seis meses (C UX C3). Diseño de cálculos y degradación P07. |
| P00-D13 | APROBADA_CONCEPTUAL | Etiquetar origen y certeza: REAL, COMPROMETIDO, ESTIMADO, SIMULADO; el escenario hipotético no es transacción ni escritura Reality. | Handoff 12/11 + principio Evidence≠Reality. Nomenclatura concreta de UI a cerrar en P07; no afirmar que los cuatro estados estén implementados. |
| P00-D14 | APROBADA | Conservar protecciones: cinco owners, writer único, CAS, plan inmutable, recibos, Undo, recuperación, Evidence separado y no egress secreto sin permiso. No V4 runtime fallback. | AGENTS/protocolo, arquitectura V5. |
| P00-D15 | APROBADA | No abrir formalmente S4, no modificar main/V4.4, no activar V2 productiva/Auto/Noche, no reejecutar CI ya certificado ni pruebas G15 por esta preparación. | Usuario 12/11, pointer, permiso BLOCKED; limitada a P00. |
| P00-D16 | HEREDADA_V44 | **Cocina dentro de SmartCompra**, no módulo principal adicional; relaciona necesidades/stock/compra/menú. | V4.4 fuente congelada Fase A (~líneas 1054–1112, 2183–2185), C3 físico (Stock/Compra/Cocina). V5 debe decidir paridad funcional en P02/P05, no asumir implementación. |
| P00-D17 | HEREDADA_V44 | Inicio lectura contextual local de hasta 4 señales relevantes, sin falsos «todo tranquilo» cuando fallan fuentes y sin sustituir análisis Radar. | V4.4 C2; adoptar o adaptar concretamente en P05/P06, no confundir Inicio con pantalla predeterminada. |
| P00-D18 | HEREDADA_V44 | Finanzas distingue pasado REAL, hoy y proyección, Horizonte/Plan del mes/Compromisos previstos, sin sumar monedas distintas ni simular evidencia ausente. | V4.4 C3/Decision Log; inventariar V5 y precisar cálculo P01–P07. |
| P00-D19 | HEREDADA_V44 | Asuntos por atención/vencimiento; Objetivos por estado, acciones del mes; SmartCompra/Cocina con estados honestos y datos suficientes, no cero ficticio. | V4.4 C2/C3; paridad a evaluar, no cierre funcional V5. |
| P00-D20 | PROPUESTA | Primer mes del horizonte con más detalle y cinco siguientes sintéticos. | Handoff: preferencia a contrastar en P05/P07; no fijar densidad sin pruebas UX. |
| P00-D21 | PROPUESTA | Motor común de proyección usado por Finance/otros owners, sin owner duplicado ni módulo «Proyecciones» independiente por inercia. | Usuario/handoff; arquitectura y dueño en P07, solo si evidencia lo justifica. |
| P00-D22 | PROPUESTA | Árbol inicial S4 A1 Finanzas / A2 Objetivos / A3 Asuntos / A4 SmartCompra / A5 Integración. | Handoff explícito: no congelado ni aprobado como contratos de unidades. |
| P00-D23 | PROPUESTA_DE_PREPARACION | Secuencia P01–P08 del handoff: inventario V5, V4.4, delta, benchmark, función, UX, integración/proyección y paquetes Work. | Handoff 12/11; orden de análisis, no órdenes de programación ni READY. |
| P00-D24 | PENDIENTE | Ubicación exacta de Buscar, Guardados, Actividad y Avisos en drawer y prioridad frente a secciones acordadas. | Surface V5 actual enumera estos destinos secundarios; D-491 fija estructura base. Resolver layout en P06 sin reabrir D-491 injustificadamente. |
| P00-D25 | PENDIENTE | Tratamiento de navegación mientras hay respuesta/Apply en curso; preservar ID, drafts, planFingerprint y receipts sin bloqueo total heredado. | D-493 P07 **excluye copiar el guard global V4.4 literalmente**; requiere investigación por owner. |
| P00-D26 | PENDIENTE | UX definitiva de predicciones: gráfico, detalle, incertidumbre, movimiento de horizonte, cuota a 7+ meses y explicación desde Chat. | D12/D13 aprobadas; mecánica P05/P07 aún sin contrato. |
| P00-D27 | PENDIENTE | Qué se incorpora exactamente de Radar V4.4 (tendencias, anomalías, evolución, historial, feedback) a Chat/Asuntos sin crear nuevo owner/Reality. | V4.4 D CLOSED; V5 D-491 resuelve navegación, no toda la reubicación funcional. |
| P00-D28 | PENDIENTE | Lista final de funciones manuales por dominio y relación Cocina/Compras/Pendientes/Objetivos. | Visión D01, D11, D16–D19; evidencia P01/P02, contratos P05. |
| P00-D29 | RECHAZADA | Recrear **Radar como nueva sección principal independiente** del drawer V5 por copiar V4.4. | D-491/D-493 explícitos y decisión posterior; la función no queda rechazada. |
| P00-D30 | RECHAZADA | Duplicar navegación con accesos rápidos permanentes Chat + drawer o barra inferior. | Decisión usuario 02/11 y 12/11. |
| P00-D31 | RECHAZADA | Truncar/borrar compromisos fuera de la ventana de 6 meses o presentar SIMULADO como REAL. | D12/D13 y atomicidad/verdad V5. |
| P00-D32 | RECHAZADA | Copiar código/runtime V4.4, `chat-main`, el bloqueo global de navegación o importar código de apps externas sin licencias/compatibilidad investigadas. | D-493 §6.1, D-491, protocolo, B3. |
| P00-D33 | RECHAZADA | Activar Groq/Internet, automatización, delivery Android o V2 productiva implícitamente al leer/preparar S4. | AGENTS/permit/pointer. X03 live está abierto de forma independiente en S3. |

## B2 — Comparación y contradicciones resueltas (evidencia histórica vs decisión vigente)
1. **Radar V4.4 tiene sección propia (incluso evolucionó de posiciones en el drawer)**: V4 Decision Log 17–18/10 y C1/D. V5 D-491/D-493 posteriores aprueban no poner Radar como raíz; se preserva responsabilidad analítica sin copiar navegación. RESUELTO navegación; migración funcional P27 PENDIENTE.
2. **Cocina aparece dentro de SmartCompra en V4.4 congelada y C3 física**: se conserva como baseline, no justifica sexta raíz ni asumir que ya existe en V5. RESUELTO clasificación; alcance P05 PENDIENTE.
3. **Abrir Chat vs tener Inicio**: Chat es landing; Inicio es vista contextual elegida por menú. No deben competir ni duplicarse. RESUELTO semántica; contenido V5 de Inicio se contrastará con C2 histórico.
4. **Chat-first vs crear nuevo chat siempre**: Chat como landing no exige crear uno nuevo; D-493 cold start protege no proliferar vacíos. RESUELTO.
5. **6 meses vs compromisos más extensos**: ventana temporal móvil de lectura/proyección no limita persistencia/duración de obligaciones; no inventar eventos o fechas a partir de periodos sin día fijo. RESUELTO principio; modelado concreto P07 PENDIENTE.
6. **Drawer D-491 vs V5 actual con destinos secundarios**: código V5 separa cinco roots primarias de Buscar/Guardados/Actividad/Avisos, y no enumera CHAT dentro de `faroV5PrimaryDrawerRoots`; no es prueba física ni aprobación de jerarquía final. OPEN GAP de presentación P06, no contradicción que autorice rediseño.
7. **D-492 propuestas P01–P14 vs D-493**: D-493 aprobó comportamientos útiles como producto, pero NO el guard V4.4 literal P07 ni una doble lupa. La aprobación funcional no equivale a implementación o tests V5. RESUELTO precedencia.
8. **Estado documental viejo vs vivo**: benchmark S3 del 02/11 decía 172 contratos y Guardados candidatos; pointer actual registra 180 (172+8 aprobadas), M01–M05 B7 scoped. No usar texto antiguo para revocar aprobaciones ni para declarar S3 cerrado. RESUELTO por fecha/HEAD/alcance.
9. **Epoch en pointer**: `writer.expected_release_epoch=842` es snapshot heredado, pero lock real antes de este turno era RELEASED 843 y fue HELD 844 para este P00. Campo informativo no sustituye consulta del writer. Actualizar meta de continuidad al cerrar, nunca usar 842 para adquirir lease.
10. **Árbol S4 A1–A5 frente a P00–P08**: el primero es propuesta funcional, el segundo metodología de preparación; ninguno abre S4. RESUELTO categorías; contratos tras P05–P08.

## B3 — Investigación de alternativas (sin repetir trabajo de P04)
Reutilizado benchmark `docs/v5/FARO_S3_BENCHMARK_APPS_20261102.md` (Obsidian, Notion, Keep, Linear, Todoist, n8n HITL, Anytype, SQLite/Android) como **REFERENCIA**, no contrato ni implementación. La exploración posterior de Actual Budget, Firefly III, KitchenOwl, Grocy, Vikunja y Expense Manager sigue no adoptada; validar licencia/mantenimiento/offline/datos solo en P04 cuando haya delta definido por P01–P03. Precedente V4.4 cita Actual Budget para reportes y Vico para visualización, sin obligar a copiarlos. Alternativas para proyección: Finance como lectura derivada transversal o motor común separado; ambas quedan PROPUESTA, se decidirán con ownership y pruebas adversariales en P07. No existe permiso para importar código ni dependencias.

## B4 — Unificación y límites por próxima unidad
- **P00 termina en una matriz de autoridad y dudas**, no en una arquitectura/programa S4.
- P01 inventariará implementación V5 efectiva (código, host, UI, permisos, pruebas, SHA) sin convertir contrato en PASS.
- P02 documentará paridad real V4.4 (Finanzas, Objetivos, Asuntos, Radar, SmartCompra/Cocina, proyección, UX, seguridad), usando V4 como referencia de comportamiento y no runtime.
- P03 relacionará ambos inventarios por dueño; P04 reutilizará benchmarks ya hechos y buscará huecos precisos; P05–P07 fijarán contratos sometidos a aprobación humana; P08 preparará B1–B5/Work sin emitir READY.
- Conservar para S3 en el pointer la prioridad, el estado y los cierres B7 scoped existentes. No declarar S3.A3/A5 global CLOSED ni X03 live PASS.

## B5 — Auditoría adversarial de decisiones (sin código)
| Riesgo | Control/documento dueño |
|---|---|
| Tratar V4.4 como aprobación funcional V5 | Etiqueta HEREDADA_V44, P01/P02/P03, D-493 §6.1 |
| Copiar Radar independiente por nostalgia | D09/D29; contrastar ownership funcional P05/P07 |
| Tratar 6 meses como caducidad de datos | D12/D31; casos de cuotas +7/+12/+36 meses P07 |
| Confundir hecho, compromiso, estimación y simulación | D13, test de read-model/no-Apply P07 |
| Duplicar botones o drawer | D03/D30; recorrido modal/permanente y accesibilidad P06 |
| Inventar cero sin datos o silencio de owner | V4.4 C2/C3, S3 A5 parcial ≠ cero; pruebas P05/P07 |
| Autorizar S4 o modificar code con permiso BLOCKED | D15/D33, writer doc-only CAS y diff limitado `docs/v5/**` |
| Rehacer CI/gates certificados o declarar G15 físico PASS | Reutilizar matriz S3.A5.7; toda nueva prueba es N/A en P00 |
| Depender de servicio IA/pago para funciones básicas | D10/D11, prueba offline/manual futura por módulo |
| Declarar la investigación de referentes como adopción | B3 REFERENCIA/P04 PENDIENTE, no importar licencias |
| Editar main, repositorio V4 o activar robots | No commits ni resets fuera de rama V5; writer global único |
| Metadatos pointer obsoletos confundidos con writer real | Verificación `writer.json` CAS antes/después; epoch vivo manda |

## Criterios de salida P00 y estado previo a B7
1. Fuentes con rama, rutas y baselines reales recuperadas: **B1 evidenciado**.
2. Decisiones clasificadas sin colar propuestas: **B2 evidenciado**.
3. Alternativas existentes marcadas como referencia, sin falso benchmark nuevo: **B3 evidenciado**.
4. Contradicciones con precedencia y dueño claro (incluyendo OPEN GAP explícitos): **B4 evidenciado**.
5. Riesgos adversariales y futuras pruebas negativas ubicados en P01–P08: **B5 evidenciado**.
6. **B6**: publicar este documento y actualizar pointer como paso separado, bajo writer CAS.
7. **B7**: verificar HEAD, diff exclusivamente documental, contenido SHA, pointer final P00 y writer RELEASED epoch 844; hasta ello **NO afirmar P00 PASS**.
Entregable P00 = **matriz documental completa**; no es autorización para abrir S4, ejecutar P01, adquirir permisos funcionales ni programar. Las incógnitas etiquetadas PENDIENTE son producto de salida deliberado, no excusa para inventar decisiones.
