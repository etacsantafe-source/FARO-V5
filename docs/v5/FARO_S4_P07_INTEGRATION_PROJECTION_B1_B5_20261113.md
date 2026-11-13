# FARO V5 — P07 · Integración, ownership, predicción | B1–B5 con decisión humana pendiente

Fecha 2026-11-13 (Argentina). Unidad `S4_PREP_P07_INTEGRATION_PROJECTION`. **Estado: B1–B5 documentados, P07 OPEN/NEEDS_HUMAN_CHOICE, no aprobado ni implementado.** La aprobación humana para diseñar `household` independiente fue dada expresamente en este chat, sin autorizar código ni abrir S4 formal.

## B1 — Baseline, fuentes y permisos

Repositorio `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`, HEAD origen `6ddf733026662e109471ace2d75744622a5a5e45`, escritor RELEASED epoch868 observado al entrar, CAS HELD epoch869 solo para checkpoint documental. Permiso funcional `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` BLOCKED, S3 pausada (formal 0/5), S4 formal OFF, V2 productiva/Auto/Noche OFF, sin APIs pagas. AGENTS.md y `FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md` consultados; B6 aquí SOLO DOCS, nunca código ni CI.

Autoridades reutilizadas: P00 decisiones D01–D33 (blob `09d47f8a5c2ed5f02a97a4b1c68aec50ffd5ea41`), P01 inventario V01–V30 (`5449938c3e5e8fbe5e18a2c7694989e1e5e4af96`), P02 legado L01–L20 (`9a6388ae6be298eb6536a3dc81e1266bf4d42385`), P03 delta R01–R20 (`4b9789004459eed6e95bc4d4f8032ee3b4c9f340`), P04 benchmark B01–B09 (`3f43ac170e812e3f363f02dea995d4a827de117b`), P05 22 flujos F01–C02 (`76eba232db78237e53d06bf9725dd73f6ec5dd2d`), P06 11 decisiones U01–U11 y 30 negativos UX (`c347360157a14bea7e6a3c5571ef1903be3ecb3c`). No se repiten certificaciones pasadas.

Inventario inspeccionado: `v5/spec/generated/contract-baseline.tsv` blob `5653a9758f682f683a852dcf286211bae71c41d0`: 180 operaciones catalogadas, **32 con campo owner = household**; los 32 son descriptores, NO ejecutores. `v5/settings.gradle.kts` blob `a2b6f7e4294537ff8add383ea8c620796d8d88ee` incluye módulos owner:finance/objectives/matters/reminders/monthlyplan/profile, pero NO `owner:household`. La autorización humana nueva contempla diseñar autoridad household aparte de los cinco owners de referencia, sin romperlos ni crear otro writer o DB.

Fuentes transaccionales: `v5/kernel/contracts/.../ApplyPlan.kt` blob `5808ef0021ff91fde19339bdbb06c2f7e255a2e1` valida identidad/topología; `v5/kernel/apply/.../ApplyTransactionExecutor.kt` blob `772decfde8aee398eda6c84149f9f0a4234d6d7f` controla validación/cancelación/recovery scoped; `v5/store/reality/.../RealityStore.kt` blob `be72978b28d79366452a3f43c7018db72b2ca9f7` tiene Apply/receipts y persistencia Finance/Objetivos/Matters, **no demuestra transacción HOUSEHOLD+FINANCE implementada**. FinanceModel `22f9444f931aff356836bf61bffb10e9975142b7`; MonthlyPlanModel `6a896bf5bce2bc2371a0babf1b5b42388dbfb267` confirma que vencimiento no implica pago; ReminderModel `4121862c038b8e3a4e333ba6880a2a50dc8c6436` sin scheduler integrado.

## B2 — Comparación real V4.4 frente a V5, sin copia de runtime

V4.4 L04/L05/L06 tenía finanzas, horizonte de seis meses y gráficos que históricamente se reubicaron en Radar; L11–L15 tenía necesidades, plan/catálogo, pago, inventario y Cocina dentro SmartCompra con store real inyectado. L17 tenía Atomic Apply/Undo/recovery pero G0D3 descubrió caminos no atómicos e identidad duplicada. V5 trae núcleo Apply+Reality con controles scoped, contratos para `household`, Finance/Planning parciales y Radar no montado; ningún PASS de V4.4 ni contrato V5 confirma los flujos de usuario P05 ni el gráfico P06. La preferencia actual de usuario prevalece: gráfico financiero con cero y rojo en Finanzas, Radar C sin raíz, cuota futura retenida.

Catalogar `household` como authority futura es **reusar la taxonomía aprobada**, no sumar 32 operaciones ni copiar `FaroV4SmartCompraGateway`. El id/canonical binding final, el modelo SQLite y la ruta exacta `owner:household` requieren contrato B1–B5, permiso individual y validación humana antes de código.

## B3 — Alternativas investigadas y límites

Alternativa A (elegida para diseño de `household`): autoridad canónica independiente para necesidades, compra, inventario y Cocina, con cuatro subflujos, manteniendo Kernel y WriteCoordinator único; coherente con los 32 contratos ya catalogados. Alternativa B (descartada): absorber compras en Finance; confundiría dinero con stock. Alternativa C (descartada): crear varias DB/writers nuevos o duplicar owner por subsección; aumenta mutaciones incompatibles, recovery y divergencias. P04 Grocy aporta transiciones; Actual Budget aporta contabilidad/lectura; KitchenOwl UX; no importar código AGPL ni crear servidor, CRDT, servicios pagos o egress involuntario. B3 se REUTILIZA desde P04, sin redescubrir apps.

## B4 — Matriz de ownership y lectura transversal (10 fronteras)

| ID | Dominio / frontera | Qué es verdad | Autoridad y dependencias | Prohibición |
| --- | --- | --- | --- | --- |
| G01 | Finance | Cuentas, saldos, asientos REAL, transferencias, deudas y hechos de pagos financieros | Finance owner existente y Reality, ACL y Apply por operación | Una compra HOUSEHOLD no crea asiento Finance tácito |
| G02 | Objectives | Objetivos y su progreso confirmado | Objectives owner; referencias Finance solo por grant | Dato monetario denegado jamás se sustituye por cero |
| G03 | Matters | Estados, prioridad y hechos propios del asunto | Matters owner; Reminder/Delivery fuera del estado del Matter | Dismiss Aviso no cierra Matter |
| G04 | Planning | Obligaciones, recordatorios y recurrencias tipados, según dueño canónico aún por finalizar | Owners/contratos ya catalogados, disponibilidad host limitada | Due ≠ PAID_CONFIRMED y regla ≠ ejecución automática |
| G05 | HOUSEHOLD | Necesidades, planes de compra, operaciones comerciales, recepción, stock, recetas/preparados y cocina | **NUEVA autoridad independiente aprobada para diseño P07**, runtime todavía ausente | 32 OperationContracts catalogados bajo `household`, NO 32 ejecutores ni nueva tabla autorizada |
| G06 | Evidence comercial | Observaciones de producto y precio: fuente, moneda, comercio, zona, fecha, frescura, calidad | `commercial-evidence` observacional, no saldo ni stock canónico | Cotización pasada no es precio pagado ni disponibilidad real |
| G07 | Radar | Análisis derivado READ_ONLY de fuentes múltiples y feedback observacional por contrato | Owner lógico `radar` del catálogo, sin crear Reality canónica ni nueva raíz | Score full con owner faltante es inválido |
| G08 | Workspace/Auxiliary | Conversaciones/drafts/candidatos, guardados, avisos/feedback e historial según autoridad | Workspace/Auxiliary separados de Reality; permisos de read-source por dueño | Leer, generar resumen o guardar no es Apply |
| G09 | Kernel + Reality + generation | Coordinación de transacciones, receipts, recovery, fencing/CAS y backup/restore | WriteCoordinator único; ApplyPlan + Policy + receipt bajo permisos separados | No writer paralelo, banco nuevo ni importación automática V4.4 |
| G10 | Delivery / red / IA | Entrega Android y consumo opcional de proveedores externos | Permisos explícitos, control de egress, no verdad canónica por UI | IA no ejecuta por sí sola ni bloquea el trabajo offline |

## B4 — Principios de transacción HOUSEHOLD ↔ Finance (propuesta, decisión pendiente)

El catálogo define `shopping.payment.record` (`household`, REALITY_MUTATION, MERGE) como registro del hecho comercial **sin mover dinero**; `finance.entry.record` y `finance.transfer.record` son autoridad de asientos reales. La operación `shopping.receipt.record` se distingue de `stock.receive`: recepción de comprobante o mercancía no implica alterar inventario físico salvo acto explícito de recepción. La misma compra debe conservar identidad estable y evitar cobrar dos veces aun con retry, restore, duplicación de Chat/Resumen y reimportaciones.

**Puerta humana H-P07-01 PENDIENTE:** decidir experiencia de confirmación al registrar una compra pagada. Opción A: UNA revisión humana que muestra el efecto comercial y el gasto Finance, implementable solo cuando se pruebe una transacción compuesta verdaderamente atómica, con autorizaciones separadas, mismo recovery/receipt y rollback total; si falta capacidad, se debe DENEGAR o degradar explícitamente a estados parciales sin auto-escritura silenciosa. Opción B: confirmaciones independientes: primero registro comercial en HOUSEHOLD y luego el gasto Finance en su propio Review; enlace por identidad y receipts, nunca asentar gasto automáticamente desde el primero. **Ninguna está aprobada todavía.** En ambos casos no se hace pago real bancario ni transferencia física; FARO solo registra información y no puede afirmar que una compra fue pagada si el usuario no lo confirmó.

Protecciones comunes: `planFingerprint`, idempotency key por intento, versión/epoch de cada owner, generation, grant de lectura/escritura por autoridad, transacción Reality con receipt y journal, rollback/compensación conforme contrato. Si UNKNOWN después de crash, consultar receipt antes de reejecutar. El `household` autorizado para DISEÑO no implica que el runtime haya ganado host grants ni que `shopping.payment.record` ahora ejecute.

## B4 — Contrato propuesto de proyección y gráfico (14 reglas)

| ID | Componente | Algoritmo/lectura a exigir en P08 futuro | Riesgo que bloquea |
| --- | --- | --- | --- |
| M01 | Rango de lectura | Calendario en zona local configurada: compacto gráfico M-6..M0..M+5 =12 meses; detalle operativo M0..M+5=6 meses, desplazable | Datos M+7/M+12/M+36 y anteriores conservados, sin retención truncada |
| M02 | Series históricas | Por cuenta y moneda, saldo al cierre de cada mes pasado basado solo en ledger REAL con fechas de efecto y opening conocido | Sin historia/calidad suficiente: punto faltante; no backfill ni cero ficticio |
| M03 | Mes presente | Punto o detalle M0 distingue saldo REAL `asOf` de estimación de cierre de mes; UI no mezcla ambos como un mismo hecho | Corte temporal y timezone obligatorios; no inventar gasto ya ocurrido |
| M04 | Serie futura | Partir del saldo REAL conocido y aplicar compromisos explícitos futuros por fecha y moneda, con supuestos/tasa de cobertura declarados | La ausencia de futuros ingresos no implica saldo seguro, resultado es ESTIMADO |
| M05 | Recurrentes | Regla/template y ocurrencias/materializaciones separadas; expandir solo para cálculo READ_ONLY con occurrenceId estable y freshness | Lectura no escribe ni duplica cuota, ni marca pagada por llegar vencimiento |
| M06 | Dedupe hechos | Cada obligación pagada se reconcilia por identidad/referencia a movimiento REAL/receipt; no sumar simultáneamente previsto y realizado | Si identidad/estado es incierto: marcar cobertura PARCIAL, sin descontar dos veces |
| M07 | Multimoneda | Agregación permitida únicamente entre cuentas de la misma moneda; ARS/USD series independientes, conversión siempre explícita y con fuente | No sumar pesos+dólares nominalmente ni suponer cambio vivo |
| M08 | Cuatro certezas | REAL confirmado; COMPROMETIDO registrado a futuro; ESTIMADO derivación con premisas; SIMULADO escenario aislado fuera de Reality | Ni simulación ni observación IA pueden convertirse en REAL sin Apply |
| M09 | Línea cero y rojo | Eje cero visible siempre; solo segmento y zona bajo cero van en rojo; distinguir línea real vs futura también por trazo/etiqueta | Cero de eje es referencia, nunca saldo inferido cuando falta información |
| M10 | Cobertura | Registrar por punto: fuente/grant, revisión/generación, fecha asOf, moneda, capacidad de cálculo, faltantes y certeza | Si falta fuente no inventar tramo ni score ni declarar normalidad |
| M11 | Vista expandida | Rangos, filtros por moneda/cuenta, series y explicación de supuestos son lectura derivada; no navegación raíz Proyecciones | Horizonte largo se consulta sin prometer exactitud; supuestos deben ser visibles |
| M12 | Capacidad financiera | Saldo previsto no equivale a dinero libre: distinguir obligaciones, reservas, presupuesto, límites y gastos no observados | No convertir resultado en promesa de capacidad ni efectuar transferencias |
| M13 | Revisión de corte | Al cambiar fecha/huso/mes, recalcular read-model puro con snapshot consistente y revisar fuentes/epoch | No mutar data al desplazar ventana o abrir gráfico |
| M14 | Degradación segura | Fallas por cuenta o owner reportan PARCIAL/SIN_DATOS/NO_AUTORIZADO y fuentes disponibles con contexto | No ocultar huecos ni mostrar déficit seguro por datos incompletos |

Regla base de cálculo por cuenta y moneda: `saldo histórico real = apertura + ∑ ingresos REAL de fecha efectiva <= corte − ∑ egresos REAL de fecha efectiva <= corte`, siempre que existan apertura, fechas, revisión completa y fuente autorizada. `saldo estimado futuro = saldo REAL al asOf + ∑ ingresos comprometidos explícitos pendientes − ∑ pagos/compromisos pendientes`, deduplicados contra asientos REAL e identificados con supuestos/cobertura. El segundo resultado es condicional, NUNCA saldo REAL ni compromiso de resultado. Mantener para cada punto `period`, `currency`, `account/aggregate`, `asOf`, `sourceRevision`, `generation`, `coverage`, `certainty`, `valueOrUnknown` y `explanation`. No calcular valores cuando falta fuente o monto conocido. Cualquier esquema o nombre de clase acá es **candidato documental**, sin migración SQL ni API.

Gráfico compacto P06 cubre 12 períodos consecutivos: seis pasados M-6..M-1, presente M0 y cinco futuros M+1..M+5. Línea horizontal cero visible, todo segmento/área debajo de 0 en rojo y negativos proyectados visual/textualmente separados. El detalle de seis meses es móvil M0..M+5. Obligaciones de +7/+12/+36 y demás se conservan sin límite artificial; no confiar en forecast sin datos. Cuando las fuentes difieren de tiempo de corte, no fusionar sin aviso.

## B4 — Radar, permisos, egress, offline y lifecycle

Radar C = Inicio tarjeta compacta con no más de 4 señales en total, Chat explicación/consulta, Asuntos hallazgo contextual; no nueva raíz, store Reality ni acceso global incondicional. `radar.read/history` pureza READ_ONLY; `radar.refresh` observacional, `radar.feedback.record` AUXILIARY_MUTATION; ninguna acción derivada modifica owner fuente. Para cualquier owner fallido/revocado, `coverage=PARCIAL` y `scoreGeneral=NULL`, no 'todo tranquilo'. Cada apertura revalida grant, foreground, epoch y source revision; un snippet cacheado revocado no vuelve a UI. El proyecto conserva política LOCAL_ONLY/CLOUD_ALLOWED_MINIMIZED/EXPLICIT_SHARE_REQUIRED con consentimiento específico y nunca secretos/montos en egress por inferencia. Los básicos manuales operan sin red/IA.

Reminders (intención, fechas y recurrencia), Matter (estado), Avisos (bandeja) y Delivery Android (envío/confirmación OS) tienen autoridades distintas. Un cron no materializa al leer; recibir una notificación no paga obligación ni marca Reminder confirmado. No reclamar OS delivery positivo hasta tener confirmación real. Backup/generation fencing mantiene corte por generación; ningún import V4.4→Reality8 está certificado.

## B5 — Pruebas adversariales planeadas (22 NO EJECUTADAS)

| ID | Escenario | Aceptación futura |
| --- | --- | --- |
| P7T01 | Double tap de compra pagada | Una intención/receipt HOUSEHOLD, sin doble entrada Finance |
| P7T02 | Caída antes/tras COMMITTED con dos owners involucrados | No doble gasto, no mutación unilateral no reconciliada |
| P7T03 | Revocación del grant Finance durante revisión HOUSEHOLD | No leer/sumar saldo ni insertar gasto después de revocación |
| P7T04 | Pago registrado sin recepción física | Stock físico sin cambios |
| P7T05 | Recibir 7 de 10 comprados | Sólo stock +7 y 3 pendientes, nunca +10 |
| P7T06 | Reserva del futuro | Stock físico hoy no disminuye sin consumo real autorizado |
| P7T07 | Plan de receta no confirmado | Stock/preparados sin cambios |
| P7T08 | Precio comercial pasado | No cotizar como precio vigente ni asentar como importe pagado |
| P7T09 | Mensualidad M+12 frente a ventana seis meses | Conservación total, aparece al desplazar rango |
| P7T10 | Ingreso estimado de M+3 sin cuenta confirmada | Proyección condicional etiquetada, nunca saldo REAL |
| P7T11 | Compromiso liquidado a mitad de M0 | No doble descuento con ledger y cuota prevista |
| P7T12 | ARS y USD simultáneos | Serie separada; no total nominal mezclado |
| P7T13 | Gráfico sin dato histórico M-2 | Hueco/no datos, eje cero permanece, ningún saldo $0 ficticio |
| P7T14 | Serie futura cruzando cero dos veces | Solamente tramos debajo de cero rojos, real/proyectado distinguidos |
| P7T15 | Radar con una de cuatro fuentes autorizada | Score general NULL o PARCIAL, cero calma inventada |
| P7T16 | Apertura Radar desde Asunto tras revocación de owner | No leak de snippets/precios/montos previos |
| P7T17 | Cierre Aviso/entrega OS no confirmada | No cerrar Matter ni marcar mensaje entregado |
| P7T18 | Sin Internet ni IA | Finanzas/Objetivos/Asuntos/SmartCompra manuales no bloqueados |
| P7T19 | Apply UNKNOWN + regreso de navegación | Consultar receipts antes de reintentar; no Apply duplicado |
| P7T20 | Restauración generación mientras pantalla abierta | Descartar snapshots y referencias stale, no mezclar generaciones |
| P7T21 | Intento importar backup V4.4 en Reality8 | Rechazar sin pérdida; migración no certificada |
| P7T22 | Intento crear owner nuevo sin permiso funcional | STOP, no código, no tablas, no operaciones nuevas |

Adversarial de arquitectura: `household` no se puede crear y ejecutar solo por decisión de diseño; catálogo de 180 inmutable; no SQL schema8 productivo sin permiso; no duplicar writer; no mover gasto desde receipt comercial implícitamente. Para P08 cada subunidad tendrá B1–B5 aprobados individualmente, alcance CAS y pruebas de integración/Android antes de G15 físico final.

## B6 — Checkpoint documental antes de resolver H-P07-01

Publicar SOLO `docs/v5/FARO_S4_P07_INTEGRATION_PROJECTION_B1_B5_20261113.md`, `docs/v5/FARO_S4_P07_B7_STAGE_HUMAN_PENDING_20261113.md` y `docs/v5/FARO_CONTINUITY_POINTER.json`, writer CAS epoch869. Actualizar P07 a `B1_B5_PREPARED_HOUSEHOLD_APPROVED_WAITING_HUMAN_PAYMENT_POLICY`, dejando P08 NOT_STARTED. No poner `P07 DONE`, sin permiso funcional ni código. Liberar writer y verificar salida. Posterior respuesta del usuario define H-P07-01 y permite retomar B4/B5 delta, formalizar B7 y transferir a P08 solo si el resto es suficiente.

## B7 — Estado de auditoría intermedia (NO cierre final)

El B7 de esta publicación verifica durabilidad de B1–B5, la autorización humana delimitada, el gate H-P07-01 pendiente y las restricciones. **P07 completo NO PASÓ B7** porque la modalidad de registro comercial-financiero aún requiere decisión explícita. No simular CI o pruebas Android/G15/Groq. El cierre definitivo P07 necesitará documento B7 posterior y writer RELEASED, no este estadio.


