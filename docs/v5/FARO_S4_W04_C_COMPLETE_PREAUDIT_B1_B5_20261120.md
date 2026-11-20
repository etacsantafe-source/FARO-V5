# FARO V5 — W04-C Preauditoría integral B1–B5 · Precios, cotizaciones y evidencia comercial
Fecha local: 2026-11-20. Base exacta de lectura: `a60c184d116bd0c8a9ddc6941a6eb3478b5455a6`. Alcance DOC_ONLY; W04-A/B0/B1 B7 PASS no se repiten. Autoridad del usuario: preparar W04-C, no iniciar B6.

## B1 — Evidencia real, contratos y brechas
- Fuente viva: `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt` @blob `cf9d53089cafd93538e6b03f9873c98264d20b65`: `CommercialProductKey` (1–160, no vacío, trimmed), `CommercialMerchant`, `CommercialCurrencyCode` (3 letras mayúsculas), `CommercialPriceObservation` (amountMinor > 0, sourceKind, sourceRef opaca, observedAt nullable), `CommercialEvidenceRecord` (acquiredAt), `CommercialEvidenceStore.record/priceHistory(productKey,1..200)`, `CommercialPriceHistoryReadExecutor`. El orden SQL usa `COALESCE(observedAt,acquiredAt)` para ordenar; la UI **NO** puede presentar acquiredAt como fecha observada. SQLite Evidence versión 1.
- Contratos catálogo180 @blob `5653a9758f682f683a852dcf286211bae71c41d0`: `commercial.price_history.read` READ_ONLY, `commercial.quote` READ_ONLY, `commercial.evidence.record` OBSERVATIONAL_WRITE, executor `commercial-evidence`; `shopping.snapshot.read` Household READ_ONLY; `purchase_plan.preview` Household READ_ONLY. No agregar OperationIds.
- Household `HouseholdNeed` @blob `224b4aac04238b2241adb046161761c931389bdc`: id opaco, nombre humano, cantidad milésimos + unidad, prioridad/estado, revisión y fecha opcional. `HouseholdNeedsSnapshot(revision,needs)` y SPI @blob `f48e6528ffcdf4986274dc5111ca0b8a10255c00`. Nombre de necesidad NO es clave de producto, cantidad de PACK/GRAM/LITER no es cantidad de unidades comerciales.
- Grafo `FaroV5GenerationStoreGraph` ya contiene `commercialEvidence` y `reality` en una misma generación; el `Household10DevApplySession` actual expone Workspace+Reality, **NO** un puerto admitido comercial. Evitar abrir otra sesión/root lease concurrente.
- Evidencia histórica ya probada: `CommercialEvidenceT2Test.kt` persistencia, reopen, idempotencia/record y read puro, **NO** afirma integración W04; W04-B1 B7 `52db8e5d7c5c4d4c8f013af5d2d81fd3f29724d8`, CI 44766456887 17/17, alcance DEV JVM/SQLite, Android físico NOT_RUN.
- Campos *ausentes*: localidad, disponibilidad, vigencia, inicio/fin promo, elegibilidad, embalaje/precio por unidad, y corrección/supersesión tipada. No inferirlos de sourceRef ni de fecha adquirida. No hay cotización con contexto completo ya certificada.

## B2 — Comparación V4.4 y reuso sin regresiones
P02 L11/L14/L15 @blob `9a6388ae6be298eb6536a3dc81e1266bf4d42385` documenta necesidad humana, catálogo/comercio, carrito con visitas y presupuesto; V4.4 inyectaba store persistente real desde CompositionRoot (NO solo InMemory). P03 R11/R14/R15 y P05 S02/PF18–20 exigen precio/fuente/fecha/moneda/localidad; conservar UX útil, no copiar gateway, DB ni semántica de certeza V4.4. En V5 Reality=necesidades, Evidence=observaciones; no crear tabla espejo Household para Evidence, ni guardar precios comerciales en Finance.

## B3 — Alternativas investigadas (hipótesis falsables)
A. ADAPTAR/USAR: modelo local read-only con `CommercialPriceHistoryReadExecutor`, binding explícito need→product y cobertura; cumple offline, sin licencia/API externa nueva y mantiene permisos. **Elegida** para C0.
B. ADAPTAR: `CommercialEvidenceRecordExecutor` con fuente MANUAL para observaciones manuales actuales. Para localidad/vigencia persistente, **se necesita delta versionado real** en Evidence con backup/restore y migración, o rechazar completitud; no esconder campos en sourceRef. Elegida *condicional* para C1.
C. REFERENCIA: SmartCompra V4.4, Grocy/KitchenOwl (P04/P08) para UX/cotización/comparación sin copiar runtime/licencias. SQLite WAL y AndroidX SQLite son infraestructura ya presente, no permiso para otra DB.
D. DESCARTAR: scraping/red o catálogo de terceros obligatorio, precio "actual" por default, normalización fuzzy silenciosa, FX automático, sumar monedas, nuevas bases, writer separado y precios de venta imaginados. `commercial.quote` es READ_ONLY; **no** autoriza mutación por nombre.

## B4 — División comprobable por fronteras de código/contrato
| Orden | Unidad | Producto de B6 posterior | Dependencia | Estado B1–B5 |
|---|---|---|---|---|
| 1 | `S4_W04_C0A_EXPLICIT_NEED_PRODUCT_MATCH_DEV` | Selección explícita needId/productKey vinculada a revisión de need y snapshot; solo modelo/guardas sin lectura Evidence ni persistencia | B1 B7 | COMPLETA para permiso individual tras guard |
| 2 | `S4_W04_C0B_AUTHORIZED_PRICE_HISTORY_READ_DEV` | Puerto de lectura de Evidence *en sesión ya admitida*, doble autorización y proyección de observaciones | C0A B7 + host/generation | COMPLETA diseño; rutas candidatas condicionadas |
| 3 | `S4_W04_C1_MANUAL_QUOTE_EVIDENCE_DEV` | Carga manual de observaciones con `commercial.evidence.record`, términos/localidad/vigencia persistidos solo con migración tipada y backup si el alcance los exige | C0B, evaluación de compatibilidad Evidence v1 | COMPLETA diseño; schema/paths condicionados |
| 4 | `S4_W04_C2_C3_HISTORY_COMPARE_REFRESH_DEV` | Comparar historia por comercio, moneda, localidad conocida y contexto; revisión y actualización anexando evidencia nueva, no editando evidencia vieja | C0B+C1; términos verificables | COMPLETA diseño; rutas candidatas condicionadas |

No crear quinta unidad por defecto. Si el contraste de C1 comprueba dos gates independientes, dividir C1a (observación manual con esquema v1) y C1b (contexto versionado) *solo con evidencia nueva*, sin permisos heredados. C2 y C3 pueden implementarse juntos si tocan el mismo read-model; separarlos si C3 requiere mutación/migración. El cambio de estructura exigirá delta B1–B5/guardia antes de nuevo READY.

## B4 — Contratos y límites por unidad
**C0A**: selección humana explícita (no inferencia por coincidencia de texto), identidad de need + revisión de registro + revisión global + generation/pointer/epoch + identidad de sesión. ProductKey canónica, origen de selección marcado como candidato hasta verificar existencia real de evidencia en C0B. Invalidar ante estado completado/eliminado, cambio de cantidad/unidad/nombre/revisión, revocación/foreground/generation y selección concurrente; sin nueva columna SQLite, sin registrar correspondencia permanente. Ausencia/ambigüedad = `NO_MATCH` o `NEEDS_CLARIFICATION`, sin exponer IDs técnicos al usuario. No declarar persistencia que no existe.

**C0B**: el grafo ya tiene Evidence y Reality; exponer mediante puerto mínimo de sesión DEV con comprobaciones de doble permiso Household + Commercial, catálogo oficial READ_ONLY, foreground, revision/generation/ABA antes y después, límite 1..200 y cierre consistente, sin reabrir segundo root lease. `sourceRef` texto opaco no URI ejecutable. Tipar `observedAt=null` como `SIN_FECHA_OBSERVADA` sin sustituir por acquiredAt. Localidad desconocida y validez no verificada siempre visibles. Leer no crea receipt, compra, plan, saldo ni mutación.

**C1**: manual `commercial.evidence.record` exige autorización OBSERVATIONAL_WRITE y gate compartido; idempotencia/fingerprint del record existente, no `household Apply` ni fecha inventada. Moneda expresa; precio >0 minor con precisión definida por moneda en capa de visualización (no multiplicar implícitamente). Para una *cotización contractual completa* capturar comercio, localidad, fecha observada, fecha de emisión, vigencia, unidades/pack, fuente, moneda, condiciones y desconocidos. Si el codec/schema v1 no los puede persistir, no inventar ni declarar cotización confirmada: preauditar v2 con migración sellada, respaldo, export/import y read de registros históricos, antes de código. `commercial.quote` sigue READ_ONLY para cálculo con coverage, no mutación.

**C2/C3**: historial append-only y consulta por producto/moneda/comercio/localidad según fuentes realmente presentes. Comparar importes homogéneos por unidad verificable, sin mezclar paquetes/gramos o ARS/USD; no aplicar FX sin fuente/autorización. Orden temporal deja fecha observada separada de adquisición. Vigencia: expired/unknown no indica stock ni oferta actual; una nueva cotización es nuevo record con nueva procedencia, la anterior permanece. Si no existe enlace de reemplazo durable, mostrar "nueva observación" y no afirmar supersesión certificada. Revalidar selección y precio en Review antes de derivar carrito W04-D; C no llama Finance.

## B4 — Rutas candidatas posteriores (NO son permisos)
- C0B: `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`; `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialPriceReadHost.kt`; `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialPriceReadHostTest.kt`; workflow dirigido nuevo `.github/workflows/faro-v5-s4-w04-c0b-price-history.yml`. Validar puerta API y leases tras C0A.
- C1: `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`, `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceBackupPort.kt`, `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceT2Test.kt` y variantes de migración/host a determinar tras inspección del codec completo; no autorizar rutas aún.
- C2/C3: read-model y tests en `v5/app/src/main/kotlin/ar/com/faro/v5/app/` y `v5/app/src/test/kotlin/ar/com/faro/v5/app/`, rutas finales tras confirmar C0B/C1. No conceder glob `v5/**`.

## B5 — Matriz adversarial completa (DISEÑADA, no ejecutada)
| Caso | Inyección/fallo | Aceptación binaria |
|---|---|---|
| C-T01 | Need ausente, eliminado o estado COMPLETED | Sin mapping/apertura silenciosa |
| C-T02 | Dos productos, nombre casi igual, marca/pack diferentes | Aclaración humana, no fuzzy match |
| C-T03 | Need cambia revisión/unidad durante selección | STALE y nueva selección |
| C-T04 | ProductKey inválida, id extraño, sourceRef con URL o código | Rechazo/opaque text, jamás ejecutarla |
| C-T05 | Revocar Household o Evidence entre consulta y UI | Sin datos/snippet posterior |
| C-T06 | ABA de generation/pointer tras leer | No publicar resultado obsoleto |
| C-T07 | Evidence null observedAt y acquiredAt presente | SIN_FECHA_OBSERVADA, jamás observado=adquirido |
| C-T08 | Historial 0, 200, >200 solicitado | SIN_DATOS / 200 máximo / rechazo >200 |
| C-T09 | ARS y USD y precios por pack vs kilo | Sin total ni unidad convertible automática |
| C-T10 | Localidad y vigencia ausentes | Desconocida/no confirmada, no "oferta hoy" |
| C-T11 | Registro precio persistido, proceso cierra/reabre | Igual record/fingerprint/idempotencia y provenance |
| C-T12 | Doble ingreso manual y crash + retry | No duplicar record, verificar commit existente |
| C-T13 | Schema v1 antiguo e intento de metadatos nuevos | No pérdida; migración o bloqueo explícito |
| C-T14 | Backup/restore Evidence con campos nuevos | Roundtrip, source ids/fechas/suma de registros exacta |
| C-T15 | Quote sin precio, moneda, local o vigencia verificada | Cobertura PARCIAL, presupuesto no confirmado |
| C-T16 | Misma observación retocada por otro comercio | Historial original intacto + nueva provenance |
| C-T17 | Abrir comparación/cambiar filtros | Evidence, Reality, Finance y stock sin mutación/receipt |
| C-T18 | Refresh de cotización, precio/moneda/generation cambia antes de Review | Review anterior inválida; sin transferencia a W04-D |
| C-T19 | Offline, sin proveedor, fuente de terceros bloqueada | Datos manuales siguen disponibles sin egress |
| C-T20 | Promoción por medio de pago sin atributos válidos | No aplicar descuento ni leer cuenta Finance |
| C-T21 | Carga de otra localidad que parece más barata | No atribuir ahorro local sin localidad/cobertura |
| C-T22 | Lectura de 2 comercios sin mismo corte temporal | Comparación marca fecha/uncertainty, nunca promesa de disponibilidad |

## Repaso de dependencias W04-D/E/F y W05/W06
- W04-D = carrito, optimización, presupuesto comercial y promociones; consume cotizaciones *con certeza/cobertura*, no puede reescribir Evidence ni generar pagos. Cambios de precio/local/medio/validez invalidan presupuesto previo.
- W04-E = interfaz Android DEV y pruebas API35; si la lista actual C solo tiene API interna, UI y G15 siguen NOT_RUN. No prometer aceptación humana.
- W04-F = auditoría integral DEV/B7 y luego fase física independiente; no anticipar F por C0/C1.
- W05 = recepción/stock/Cocina; C nunca aumenta existencias. W06 = atomicidad Household+Finance para la opción A pagada; no usar C para autorizar gasto, préstamo o pago bancario.
- Faltantes explícitos: esquema tipado locality/vigencia, permisos comerciales OBSERVATIONAL_WRITE, import/export y backup versionados, UX de elección de producto, identidad de precio por unidad, integridad cross-store de read-session, pruebas Android. Ninguno bloquea el modelo puro C0A.
## Estado de este artefacto
PREAUDITED_DOC_ONLY. Solo C0A tiene rutas exactas y no depende del schema/quote. Publicar este documento **no** habilita B6 de ninguna unidad. Guardia/READY C0A requeridos aparte.
