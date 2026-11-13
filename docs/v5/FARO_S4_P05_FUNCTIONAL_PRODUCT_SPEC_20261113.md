# FARO V5 — Preparación S4 · P05 · Especificación funcional del producto

Fecha: 2026-11-13 (Argentina). Unidad: S4_PREP_P05_FUNCTIONAL_PRODUCT_SPEC. **Propuesta documental a revisión humana, no autorización funcional.** S3 pausada; S4 formal OFF; V2 productiva/Auto/Noche OFF. No cambiar código, permisos, main, V4.4, APK ni datos.

## B1 — Evidencia y autoridad de alcance

HEAD base b8d1f4653bc64c4a86edacc875665468b4c3b0c0; writer inicial RELEASED epoch866, CAS HELD epoch867 sólo para publicar documentación. AGENTS.md, FARO_CONTINUITY_POINTER.json y FARO_PROTOCOL_WORK_PERMIT.json verificados; permiso funcional BLOCKED. El protocolo FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md exige B1–B7; B6 aquí es DOC_ONLY, nunca implementación.

Fuentes que se reutilizan sin repetir: P00 FARO_S4_P00_DECISION_AUTHORITY_MATRIX_20261112.md @blob 09d47f8a5c2ed5f02a97a4b1c68aec50ffd5ea41 (33 decisiones); P01 FARO_S4_P01_V5_IMPLEMENTATION_INVENTORY_20261112.md @5449938c3e5e18a2c7694989e1e5e4af96 (V01–V30, 22 módulos, 180 descriptores, no 180 acciones activas); P02 FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md @9a6388ae6be298eb6536a3dc81e1266bf4d42385 (L01–L20/K01–K10; fuente histórica etacsantafe-source/FARO @bfa0ee71f5b9cd104baaadd71f1e77a61f203939, READ_ONLY); P03 FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md @4b9789004459eed6e95bc4d4f8032ee3b4c9f340 (R01–R20 y T01–T20); P04 FARO_S4_P04_BENCHMARK_DELTA_20261113.md @3f43ac170e812e3f363f02dea995d4a827de117b (B01–B09, negativos X01–X18). El handoff P00–P08 encadena P05 funcional, P06 UX, P07 integración/predicción y P08 paquetes Work.

**Distinción de autoridad:** D01–D15 aprobadas en su alcance; D16–D19 heredadas V4.4; D20–D28 propuestas o pendientes, NO elevadas a aprobadas; D29–D33 rechazadas. Las propuestas de P05 no son OperationIds, no definen nuevas tablas/owners ni emiten READY.

## B2 — Requisitos funcionales sin recortar visión

1. Chat es la pantalla de entrada; drawer único como navegación, sin barra inferior ni atajos permanentes duplicados. Inicio existe como sección; Radar carece de raíz de menú; Cocina está **dentro de SmartCompra**. Fijados y hasta siete recientes no fijados; el último vacío sólo se reutiliza si cumple D08. Iconografía, secundarios, IME y layouts corresponden a P06.

2. Finanzas, Objetivos, Asuntos y SmartCompra deben poder consultar, registrar y corregir los datos esenciales **manualmente, en local y sin Internet, IA o APIs pagas**. La IA propone; el usuario revisa; FARO ejecuta únicamente bajo autoridad del owner. Un nombre ambiguo provoca aclaración en lenguaje humano, no solicitud de ID técnico.

3. Workspace/draft/conversación, candidato/review, Reality owner, Evidence con provenance y Auxiliary (avisos/guardados) son entidades diferentes. Ni observación web ni preview ni dismiss de un aviso pueden escribir Reality.

4. Las certezas son REAL (confirmado), COMPROMETIDO (obligación registrada pero no cumplida), ESTIMADO (derivación con supuestos) y SIMULADO (hipótesis no escrita). Las etiquetas conceptuales derivan de D13; el cálculo y su presentación definitiva quedan en P07/P06.

5. La vista financiera es **móvil M0–M5**; todas las cuotas, ingresos previstos e historial posteriores o anteriores subsisten, incluidos M7/M12/M36. Mover el horizonte de lectura nunca borra datos. No sumar monedas sin cotización autorizada ni demostrar fechas/valores ausentes como cero.

6. Cualquier mutación canónica requiere parámetros legibles, consentimiento, fingerprint/plan inmutable cuando corresponda, CAS/idempotencia/receipt y recovery/Undo contractual. En estado UNKNOWN consultar recibo antes de reintentar; no auto-retry de Apply. Corregir una acción ya confirmada conserva trazabilidad.

7. Ausencia o revocación de fuente/permiso se indica SIN_DATOS, NO_AUTORIZADO o PARCIAL; no significa saldo cero, ningún pendiente ni un score de Radar completo. Lecturas con ACL y generación revalidadas al abrir, sin snippets filtrados tras revocación.

8. Precios comerciales exigen comercio/localidad/fecha/moneda/fuente/vigencia. Planear compra != pagar != recibir != consumir != cocinar. No depender de servidor externo ni automatización de pago, inventario o cocina. Delivery Android separado de Avisos/Reality.

## B2 — Matriz de 22 recorridos manuales, con entradas/salidas y fallos

Estos son flujos de producto candidatos, no funciones certificadas del host V5 ni cambios del catálogo. En referencias: L=V4.4 histórico, V= inventario V5, R=brecha P03. Los owners de Compras/Stock/Cocina y cruces se determinan posteriormente en P07.

| Flujo | Entrada y resultado humano | Autoridad y evidencia | Fallo/negativo |
|---|---|---|---|
| F01 | Chat e Inicio | Al abrir FARO mostrar Chat; drawer único permite Inicio y módulos; conservar el chat actual, draft y selección sin crear otro salvo regla exacta de vacío legítimo | Workspace/Surface; P01 V10/V18/V19; P02 L01–L03; P03 R01–R03 | Chat viejo vacío no se recicla; Inicio parcial jamás declara todo tranquilo; PF01/PF02 |
| F02 | Finanzas: cuentas | Alta manual nombre humano, tipo, moneda y apertura explícita; listado, saldo y detalle local persistentes con receipt | Finance y Reality; V03/V04; L04; R04 | Sin saldo verificado no se inventa 0; repetir alta no duplica; PF03 |
| F03 | Finanzas: movimientos | Registrar ingreso/gasto eligiendo cuenta, importe positivo, moneda, fecha, concepto, categoría; revisar y confirmar asiento REAL con recibo | Finance/Apply V01/V03/V04/V20; L04/L17; R04/R17 | Doble tap idempotente, cancelar no muta y correcciones dejan rastro; PF04/PF05 |
| F04 | Finanzas: transferencias | Elegir origen y destino, moneda, monto y fecha; mostrar ambos efectos y confirmar una sola operación coherente | Finance/Reality; V01/V03/V04; L17; R04 | No débito unilateral ante crash; conversión necesita procedencia y contrato P07; PF06 |
| F05 | Finanzas: compromisos | Alta de deuda/cuotas/acreedor/recurrencia y vencimientos; ver calendario y estado comprometido; confirmar pago REAL por acción diferente | Finance/MonthlyPlan P07; V04/V08/V25; L04/L05; R04/R05 | Fecha vencida no significa pagada; cuotas M7/M12/M36 persisten; PF07/PF08 |
| F06 | Finanzas: presupuesto | Definir tope del mes por categoría y moneda, comparar gasto REAL/compromisos/saldo disponible, abrir origen y revisar antes de cambiar un dato | Finance + MonthlyPlan pendiente P07; V04/V08/V30; L04; R04 | Budget no mueve fondos, fuente no autorizada no equivale a gasto cero; PF09 |
| F07 | Finanzas: horizonte | Consulta móvil M0–M5 por moneda, movimientos REAL/COMPROMETIDO/ESTIMADO/SIMULADO etiquetados; mover ventana sin tocar Reality | Read-model derivado/owners P07; V24/V25/V30; L05/L06; R05/R06 | Monedas no se suman sin cambio de fuente; M7/M12/M36 intactos; escenario no escribe; PF10/PF11 |
| O01 | Objetivos: alta | Definir nombre, finalidad, métrica opcional, fecha y prioridad; visualizar objetivo creado y su información confirmada | Objectives; V05; L07; R07 | Si no hay medición no inventar porcentaje o monto; PF12 |
| O02 | Objetivos: progreso | Añadir logro con cantidad o evidencia, fecha y explicación; mostrar progreso confirmado e historial, permitir corrección trazable | Objectives/Reality; V03/V05; L07; R07 | Propuesta IA no aumenta progreso, stale no pisa revisión; PF13 |
| O03 | Objetivos: acciones | Crear paso próximo, dependencia de otros objetivos/asuntos y referencia financiera voluntaria; listar avances y obstáculos del mes | Objectives con grants aislados Finance/Matters; V05/V06/V08; L07; R07 | Sin Finance no inventar ahorro posible, sin fecha no inventar vencimiento; PF14 |
| A01 | Asuntos: alta | Registrar tema, prioridad, contexto y fecha de atención opcional; mostrar detalle, historial y orden por atención local | Matters; V06; L08; R08 | No enviar aviso Android implícito; PF15 |
| A02 | Asuntos: ciclo de vida | Acciones explícitas Esperar/Resolver/Cerrar/Reabrir, con razón, revisión y fecha; recibos y estados recuperables según contrato | Matters/Reality; V03/V06; L08; R08 | Carrera de versiones no duplica ni pierde asunto; PF16 |
| A03 | Asuntos: recordatorios | Configurar aviso o recurrencia asociada, consultar pendientes locales; entrega de OS y lectura del Aviso son canales independientes | Matters/Reminders/Auxiliary/Delivery, frontera P07; V06/V07/V17/V26; L08; R08 | Dismiss aviso no cierra Matter, permiso OS revocado no borra origen; PF17 |
| S01 | SmartCompra: necesidades | Alta por nombre humano, unidad, cantidad, frecuencia y prioridad; lista editable y buscable aun sin Internet | Autoridad comercial/stock por determinar P07; V22; L11/L14; R11/R14 | Dos marcas o unidades ambiguas exigen aclaración, nunca ID técnico; PF18 |
| S02 | SmartCompra: precio y plan | Cotizar manualmente comercio, ciudad, fecha, moneda, fuente y validez; armar lista/carrito y evaluar presupuesto y cantidad de visitas | Evidence V12 vs compra futura V22; L11/L15; R11/R15 | Precio viejo no es oferta viva; presupuesto imposible expresa faltante; PF19/PF20 |
| S03 | SmartCompra: compra y pago | Registrar lo efectivamente comprado y precio pagado con comprobante opcional; Finance registra pago solo mediante acción/autorización separada | Franja compra/Finance por resolver P07; V04/V22; L11/L12; R11/R12 | Compra/pago no agrega stock ni gatilla doble commit cross-owner; PF21 |
| S04 | SmartCompra: stock físico | Confirmar recepción con cantidad efectiva y unidad, registrar ajuste o consumo con origen; mostrar físico, reservado y disponible separados | Autoridad de inventario pendiente P07; V03/V22; L12; R12 | Recepción parcial suma lo realmente recibido, reserva futura no consume hoy; PF22/PF23 |
| S05 | SmartCompra: Cocina | Dentro de SmartCompra elegir receta, ingredientes, comensales y menú; vista de faltantes/sobrantes; confirmar preparación real por acto separado | Frontera Cocina/Stock pendiente P07; V22/V23; L13; R13 | Preview jamás consume ingredientes, sobras inventadas no se registran; PF24/PF25 |
| R01 | Radar: señales | En Chat, Inicio o Asuntos pedir atención o tendencias; recibir señales con fecha, fuente, intervalo y cobertura sin crear raíz Radar | Read-model transversal sin owner propio; V18/V24; L09/L10; R09/R10 | Fuente caída implica parcial/NULL, no score global ni calma falsa; PF26 |
| R02 | Radar: hallazgos | Ver historial, período, contexto y explicación; pasar hallazgo a Chat; feedback observacional separado de Reality | Read-model + Workspace/Auxiliary; V10/V11/V16/V24; L10; R10 | Revocar grant bloquea fragmento; feedback no cuenta como acción ejecutada; PF27 |
| C01 | Chat: proponer y aplicar | Escribir consulta natural, crear candidato local o IA opcional, pedir campos ambiguos y revisión literal; aplicar solo con autoría/receipt | Workspace + owners + kernel V01/V10/V20/V21; L14/L17/L18; R14/R17/R18 | Sin IA hay formulario manual; HTTP400/429 conserva draft; UNKNOWN nunca reintenta ciego; PF28/PF29 |
| C02 | Búsqueda y trazabilidad | Buscar chats/dominios con grants, abrir resultados con control de revisión, ver Actividad/receipts y Avisos separados | Workspace/Auxiliary/grants; V11/V14/V15/V16/V17; L16; R16 | Revocación o restauración durante consulta invalida snippet; búsqueda parcial no se presenta como global; PF30 |

## B2 — Estados de interacción y degradación (contrato observable, NO esquema SQLite)

| Estado | Experiencia esperada | Prohibición |
|---|---|---|
| INPUT_LOCAL | Borrador editable local en Workspace | No se contabiliza |
| CANDIDATE | Propuesta con campos, fuente y dudas visibles | No es Apply ni Receipt |
| REVIEW | Parámetros/effectos humanos por entidad, moneda, fecha y owner | No se aplica sin confirmar |
| CANCELLED/REJECTED | Rechazo sin cambios canónicos | Sin efectos ocultos |
| PREPARED/COMMITTED | Solo ejecutor autorizado; COMMITTED exige receipt | No inferir por UI o respuesta IA |
| UNKNOWN_RECOVERY | Consultar receipt, bloquear reenvío incierto | No reintento ciego ni doble débito |
| CORRECTED/UNDONE | Compensación o Undo con vínculo a receipt original y regla owner | No borrar historia para aparentar corrección |
| SIN_DATOS/PARCIAL | Mostrar cobertura y permitir carga manual | No falso cero o score normal |
| NO_AUTORIZADO/STALE | Revalidar grant/epoch/revisión, pedir nueva lectura/review | No mostrar cache protegido |
| SIN_CONEXION | Funciones locales básicas operan; externa opcional indisponible | No forzar Groq ni servidor |
| EVIDENCIA_CADUCADA | Mostrar procedencia/fecha y advertencia | No llamar oferta actual |
| OS_DELIVERY_NO_CONFIRMADO | Aviso auxiliar distinguido de evento OS | No afirmar entrega ni mutar asunto |

## B3 — Comparación de alternativas reutilizada, sin duplicación

Finanzas: Actual Budget (P04 B01) sólo como patrón de reportes y enfoque local-first; se descarta la autocontabilización de schedules y la dependencia de servidor. V5 Finance/Reality/MonthlyPlan todavía no tiene editor completo certificado (P01 V04/V08/V30).

SmartCompra/Cocina: Grocy (B03) aporta separación explícita entre compra, recepción y consumo, KitchenOwl (B04) una experiencia simple de listas/recetas. Sin copia de PHP/Flask/Flutter ni código AGPL; V4.4 SmartCompraStore sí tenía composición persistente, pero V5 no hereda runtime ni dueño automáticamente. V5 V22/V23 aún es SOLO_CONTRATO/AUSENTE_EN_HOST_VERIFICADO.

Radar: legados L09/L10 son hallazgos con score nullable, cobertura e historial, no una quinta/séptima sección a copiar. No hay referente externo que sustituya directamente la autoridad/ACL de FARO. Sin raíz nueva ni score inventado; lugar de gráficos queda P06 y cálculo P07.

Objetivos y Asuntos: conservar lógica útil V4.4 C/D y owners V5 V05/V06/V07; Vikunja no justifica scheduler, Gantt o suite aparte. Android offline-first (B06) es patrón arquitectónico; FARO conserva kernel/recovery propios, no introduce CRDT/servidor cloud ni APIs pagas.

## B4 — Decisiones humanas pendientes y contratos por nivel

| Puerta | Tema | Estado de autoridad | Pendiente |
|---|---|---|---|
| H01 | Manual mínimo por dominio, flujos F01–C02 | PROPUESTA_P05 | Aprobación humana de amplitud, priorización y ejemplos concretos |
| H02 | Horizonte móvil de 6 meses y retención futura | PRINCIPIO_APROBADO_D12 | Matemática, meses y cuota/compromiso en P07 |
| H03 | REAL/COMPROMETIDO/ESTIMADO/SIMULADO | APROBADO_CONCEPTUAL_D13 | Mecánica de cálculo P07 y representación P06 |
| H04 | Autoridad compra, stock, cocina y pagos cruzados | PENDIENTE_P07 | Owner/ACL/atomicidad/compensación, sin inventar owner |
| H05 | Radar como análisis sin raíz independiente | APROBADO_D09 | Ubicación funcional/gráficos en P06 y cobertura P07 |
| H06 | Reminders, Avisos y entrega OS separados | PENDIENTE_P07 | Estados exactos, permisos, revocación y secuencia |
| H07 | Mes actual detallado y otros cinco resumidos | PROPUESTA_D20 | Probar lectura/UX en P06, estructura P07 |
| H08 | Catálogo de precios externo / Open Food Facts | NO_ADOPTADO | Egress, licencias ODbL, calidad, privacidad y permiso aparte |
| H09 | Orden de programación y unidad S4 formal | PROPUESTA_D22 | No aprobar antes de P06–P08 y consentimiento separado |

Reglas de transferencia: P06 toma contenido visible, error y secuencias de los 22 flujos y las decisiones D02–D09 para definir drawer/accesibilidad, secundarios Buscar/Guardados/Actividad/Avisos, densidad de meses y contexto Radar. P07 toma fronteras de owners, matemática de seis meses, latencia/frescura de Evidence, cash flow por moneda, receipts/Undo/ACL/egress y compensación multiowner. P08 podrá preparar B1–B5 individuales **sin READY automático**, una vez exista aprobación humana específica.

## B5 — Auditoría adversarial y 30 pruebas negativas PARA EL FUTURO, no ejecutadas

| ID | Escenario adversarial | Resultado exigible |
|---|---|---|
| PF01 | Cold start con chat reciente realmente vacío y otro vacío archivado | Reutilizar sólo el primero, preservar conversación/draft, no duplicar |
| PF02 | Inicio con cinco señales y Finance inaccesible | Mostrar hasta cuatro de fuentes permitidas + cobertura parcial, nunca tranquilidad falsa |
| PF03 | Cuenta nueva sin moneda o sin saldo de apertura confirmado | Pedir la moneda; importe no comprobado no se transforma en cero |
| PF04 | Doble tap de gasto y caída luego de COMMITTED | Receipt único, efecto único e idempotencia en recuperación |
| PF05 | Cancelar revisión y cambiar importe con huella anterior | Ningún asiento antes de review nuevo, fingerprint anterior obsoleto |
| PF06 | Transferencia con origen debitado y destino que falla | Sin débito unilateral; transacción o compensación documentada por P07 |
| PF07 | Cuota programada en M7/M12/M36 y desplazamiento M0–M5 | Compromisos permanecen y reaparecen, sin borrado ni conversión a cero |
| PF08 | Vencimiento de deuda sin pago registrado | COMPROMETIDO/VENCIDO, nunca PAID_CONFIRMED ni movimiento real |
| PF09 | Presupuesto con Finance read grant revocado | No publicar consumido cero ni falsa disponibilidad |
| PF10 | Cuenta ARS y otra USD consultadas juntas | Series separadas; jamás suma nominal sin tipo de cambio probado |
| PF11 | Simulación de pago futuro | No mutación Reality ni recibo de pago |
| PF12 | Objetivo sin meta cuantitativa | No simular porcentaje/progreso monetario |
| PF13 | Dos avances competitivos basados en misma versión | Rechazar stale, mantener historia/corrección trazable |
| PF14 | Objetivo requiere saldo Finance no autorizado | No inventar capacidad ni filtrar saldo en pantalla |
| PF15 | Asunto creado sin permiso de notificaciones Android | El asunto existe local, delivery OS no se presume |
| PF16 | Doble Cerrar/Reabrir de un asunto | No duplicidad, versiones y receipts correctos |
| PF17 | Dismiss Aviso con Reminder activo, luego permiso OS revocado | Reminder y Matter originales sin cambios |
| PF18 | Pedido comprar leche con dos presentaciones | Aclarar producto y unidad, sin IDs ni suposiciones |
| PF19 | Cotización vieja o de otro comercio | Precio desactualizado/con localidad, no oferta actual |
| PF20 | Carrito supera tope o límite de visitas | Mostrar brecha/sustituciones factibles sin comprar automáticamente |
| PF21 | Compra y pago registrados, sin recepción física | Inventario físico no cambia |
| PF22 | Compra de 10, recepción física de 7 | Sólo +7 inventario y 3 faltantes rastreables |
| PF23 | Reserva para mes futuro | No descontar físico de hoy ni inventar disponibilidad |
| PF24 | Receta añadida a plan o preview | Stock/cocina real sin cambios |
| PF25 | Confirmar preparación con falta de insumos | Bloqueo/re-review, sin consumo ni sobras fantasma |
| PF26 | Radar con un dominio disponible de cuatro | Score total NULL/coverage PARCIAL, sin todo tranquilo |
| PF27 | Abrir hallazgo Radar revocado y enviar feedback | Sin leak, feedback observacional sin tocar Reality |
| PF28 | Sin red, sin Groq, sin API paga | Finanzas/Objetivos/Asuntos/Compras básicas funcionan manuales |
| PF29 | Proveedor IA 400/429 con review o Apply UNKNOWN | Borrador preservado, no retry/fanout ni segundo Apply sin receipt |
| PF30 | Búsqueda multiowner con revocación/restauración intermedia | Invalidar hit/snippet, declarar cobertura parcial, no cruzar generaciones |

Interpretación del gate: un PASS de tests históricos C1–C5/D, un descriptor OperationContract, CI scoped S3 o test de otra app NO equivale a ejecución de PF01–PF30 sobre host/producto V5. P05 no ejecuta código, ninguna prueba física G15/Groq y no avala compatibilidad de backup V4→Reality8.

Riesgos abiertos y responsables documentales: Finance UI V30 y CRUD V04 faltan en host verificado -> P06/P08; SmartCompra/Cocina y Radar sin host -> P05 propuesta/P07 owner/P08 trabajo; score por fuente nullable -> P07; math de M0–M5 + M7/M12/M36 -> P07; operación Finance vs pago compra cross-owner -> P07; delivery OS/avisos y privacidad de montos -> P07; HTTP400/429 y resolver local -> S3 Groq aparte/P07; licencias ODbL/AGPL y datos externos -> sólo si futura integración propuesta con permisos separados.

## B6 — Publicación documental y alcance de B7

Publicar únicamente: (1) docs/v5/FARO_S4_P05_FUNCTIONAL_PRODUCT_SPEC_20261113.md; (2) docs/v5/FARO_S4_P05_B7_20261113.md; (3) docs/v5/FARO_CONTINUITY_POINTER.json. Bajo writer CAS epoch867, actualizar P05 DONE_VERIFIED_DOC_SCOPE y P06 READY_TO_START_READ_ONLY_NOT_EXECUTED, conservando P00–P04, S3 pausada, S4 formal OFF, V2 productiva OFF y permiso funcional BLOCKED. No modificar main, código, workflows, scripts, otros proyectos ni documentación previa.

## B7 — Verificación final independiente POST-RELEASE

Releer GitHub remoto después de liberar el writer. Deben coincidir HEAD exacto, blobs de especificación y B7 referidos por pointer, 22 flujos F01–C02, 30 pruebas PF01–PF30, 9 puertas H01–H09, siete bloques B1–B7, diff de exactamente 3 docs, estados P00–P04 preservados y P06 todavía NO iniciada. Writer RELEASED epoch867 apuntando al HEAD; main V5 e histórico FARO intactos; S3 PAUSED, S4 OFF, permiso BLOCKED, V2 productiva OFF. CI/Gradle/XML/APK/G15/Groq nuevos = NOT_RUN_DOC_ONLY. Si falla un gate, no declarar cierre.

