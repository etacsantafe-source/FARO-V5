# FARO V5 — W04-D4 · Proyección segura de revisión comercial para presentación — B1–B5

Fecha 2026-11-22 (Argentina). Unidad propuesta: S4_W04_D4_HISTORICAL_REVIEW_PRESENTATION_DEV. **B1–B5 exclusivamente documentales; NO READY/B6 hasta CI documental verde independiente.** Rama work/s3-night-20261102; baseline D3C checkpoint 0470063ffcb407e7dd3a9a60738d0fe40c9aa4eb; guardia 45028339081 SUCCESS. Sin aplicaciones productivas, Apply, pagos, captura de cotización actual, stock, HTTP, Android UI, G15 físico ni nueva autoridad. El propósito de D4 es exclusivamente un *read-model* tipado e inmutable para W04-E; W04-E (render Android DEV y pruebas propias) permanece separado. La preauditoría inicial de D (2026-11-20) llamaba «D4 integración Android/UI en W04-E»; se resuelve esa frontera reservando Android/UI para E y D4 únicamente para proyección previa. No atribuir certificación UI a D4.

## B1 — Evidencia y brecha del último checkpoint

- HEAD base exacto 0470063ffcb407e7dd3a9a60738d0fe40c9aa4eb; D3C funcional b94912508fd6df0829bb047bc09f6ef865370c3d. Guardia del checkpoint 45028339081 SUCCESS. D3C B7 PASS documentado en docs/v5/FARO_S4_W04_D3C_EXPLICIT_OBSERVATION_TRACE_B7_PASS_20261122.md (blob 0dee3ec09b62822d65de0c2360ea3af686280bff): CI 45027864372, guard 45027864245, 8/8 JUnit5 distintos y XML real. No repetirlos.
- Writer remoto faro-v5-writer-lock:writer.json observado RELEASED epoch1556, target_head b94912508fd6df0829bb047bc09f6ef865370c3d, last_checkpoint 0470063ffcb407e7dd3a9a60738d0fe40c9aa4eb. Ese target_head documenta la adquisición anterior y NO es necesariamente el HEAD actual; para nueva escritura adquirir CAS con target_head igual al nuevo HEAD, sin reescribir directamente un estado RELEASED ni forzar takeover.
- Permiso docs/v5/FARO_PROTOCOL_WORK_PERMIT.json BLOCKED: D3C READY consumido correctamente; D4 requiere permiso específico nuevo, con B1–B5 probados, guard y ruta exacta antes de B6.
- D0 S4W04CommercialCartDraft + D1 S4W04CommercialCartCoverage + D2A S4W04ObservedCartBudget + D2B S4W04ObservedPromotionScenario + D2C0 S4W04MerchantWhatIfSummary + D2C1 S4W04DeferredCommercialBenefitNotes + D3A S4W04CommercialPreReviewEnvelope + D3B S4W04CommercialLiveReadRecheck + D3C S4W04ExplicitObservationTrace están certificados dentro de sus alcances DEV; NO reimplementarlos.
- D3C entrega TracedForDisplay (previewDigest, traceDigest, asOfEpochMillis, checkedAtEpochMillis, coverage, líneas tipadas de origen histórico y dos warnings). D3A conserva grupos, subtotales observados, escenarios hipotéticos y notas de beneficios NO verificados. **Brecha D4**: no existe una salida unificada para una futura vista E que separe inequívocamente «importe histórico», «escenario hipotético», «condición no verificada» y «faltante», manteniendo por grupo moneda, procedencia y cobertura; tampoco hay display safe state para rechazo. D3C no es UI, no proporciona rótulos humanos ni permite comprar. NO inventar UI, nuevos nombres comerciales ni permisos.

## B2 — V4.4 comparado con V5; delta solamente

- P02 V4.4 L11/L14/L15: SmartCompra mostraba lista y presupuesto por comercio con texto humano; evidencia de historial NO significa cotización viva ni pago. En V4.4 había composición de store real; no copiar runtime, servidor, reglas de pagos ni optimizador automático.
- P05 S02 y P06 E06 definen SmartCompra legible: artículos/plan/budget; stock y Cocina diferenciados; fuente, fecha, localidad y moneda visibles. Accesibilidad futura: fuente 200%, TalkBack y targets >=48dp. P07: Household mantiene precios/promociones/presupuesto y Finance el dinero; una confirmación humana compuesta requiere atomicidad y permisos independientes NO disponibles aquí.
- Delta mínimo: D4 solo agrupa salidas ya validadas de D3A + D3C y avisos D2C1 en un estado de presentación tipado con cantidades minor por moneda; no crea nueva verdad, no vuelve a consultar host ni datos, no elige historia ni implementa un Review con autoridad. La versión gráfica W04-E resolverá rótulos humanos desde el owner/grant apropiado; D4 NO expondrá ID interno como título definitivo visible.
- Reuse de S4W04ExplicitObservationTrace.trace para revalidar en el mismo método las entradas exactas y detectar sustitución; no aceptar un digest aportado por UI como autenticación. Para D4, llamar D3C.trace con las seis entradas originales es obligatorio; D3C y D3B siguen sin modificación.

## B3 — Investigación externa, amenazas y decisión

1. Android Developers «UI layer» https://developer.android.com/topic/architecture/ui-layer — ADAPTAR flujo unidireccional y UI state; el read-model D4 no emite eventos ni comandos.
2. Android Developers «State holders» https://developer.android.com/topic/architecture/ui-layer/stateholders — REFERENCIA para W04-E, NO introducir ViewModel/Compose ni Lifecycle en D4 JVM.
3. Android Developers «Make apps more accessible» https://developer.android.com/guide/topics/ui/accessibility/apps y «Android 14 features» https://developer.android.com/about/versions/14/features — REFERENCIA para futura W04-E con fuente 200%, TalkBack, orden semántico y áreas táctiles >=48dp; D4 debe dar estados y textos inequívocos, no prometer smoke Android.
4. OWASP «Transaction Authorization» https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html — ADAPTAR separación datos mostrados vs acción autorizada; DESCARTAR toda transición automática a compra/finanzas a partir de un display.
5. MITRE CWE-367 https://cwe.mitre.org/data/definitions/367.html — ADAPTAR rechazo fail-closed ante recheck ausente/obsoleto, sin afirmar validez de precio posterior. No añadir I/O o refresh del host.
6. Grocy/KitchenOwl mencionados y evaluados en P04: patrón UX solamente. No importar AGPL, servidor, scraping ni API; sin dependencia nueva.
- Hipótesis adversarial falsable: la transformación de D3A/D3C a grupos visuales puede introducir un total global mezclando ARS/USD, sumar cashback futuro o presentar WHAT_IF como importe vigente aun si D3C es exacto. D4 será un adaptador puro de presentación con separación nominal y rechazo explícito; E hará navegación y accesibilidad de plataforma.

## B4 — Contrato acotado para ejecución futura Work

Unidad: S4_W04_D4_HISTORICAL_REVIEW_PRESENTATION_DEV, DEV JVM READ_ONLY, sin host/DB/Android/HTTP/receipts. API interna propuesta S4W04CommercialReviewPresentation.project(draft, historicalCoverage, estimate, explicitChoices, preview, rechecked): Decision. No selección automática, no entrada de rótulo/no trust de UI, no permiso productivo. Invocar D3C.trace con exactamente estas entradas, y si Rejected retornar Rejected con causa acotada sin transportar vista parcial. No aceptar directamente TracedForDisplay desconectada de las seis entradas.

- Decision.Display: immutable previewDigest, traceDigest, cartFingerprint (solo interno), sourceWatermark (solo interno), asOf, checkedAt, coverage PARTIAL/OBSERVED_ALL_SELECTED, grupos ordenados por GroupKey, faltantes, no-modelados y avisos fijos READ_ONLY_HISTORICAL_NOT_CURRENT, WHAT_IF_IS_NOT_PRICE, NO_FINANCE_STOCK_APPLY, RECHECK_AGAIN_BEFORE_ACTION.
- GroupDisplay: GroupKey (currencyCode/merchant/locality/payment), selectedNeedCount, observedHistoricalSubtotalMinor, escenario hipotético separado como ScenarioDisclosure(modeledBaseMinor, hypotheticalDiscountMinor, hypotheticalAfterMinor, scope PARTIAL/ALL_MODELED), benefit notes D2C1 con provenance USER_DECLARED_UNVERIFIED; lista de líneas tipadas D3C solo para el grupo. Nunca sumar grupos entre monedas ni presentar escenario parcial como presupuesto completo. No tomar medios de pago de Finance.
- Identidad/correspondencia: grupos D3A y líneas D3C biunívocos; todos los needId de grupos deben coincidir sin duplicados; no recuperar faltantes con cero; sourceWatermark/fingerprint/asOf de D3A/trace provienen de D3C validado; checkedAt no crea TTL/validez durable. Notas y textos fuente son cadenas inertes (no URL abrir, HTML/render automatizado ni acción).
- Aritmética minor con Math.addExact; overflow => Rejected(OVERFLOW) sin estado parcial. Copia profunda/inmutabilidad de listas/grupos/notas; salida orden determinista, sin dependencias nuevas ni datos financieros. Sin totales globales, FX o scraping. Mostrar a la futura UI valores *separados* con etiquetas de certeza, incluido incompleto/UNKNOWN y observación histórica.
- Rechazos específicos: INVALID_TRACE, GROUP_MISMATCH, INCOMPLETE_OR_INCONSISTENT_DISPLAY, OVERFLOW. Ninguna salida autoriza operación: no ApplyPlan, grants, payments, current quotes, Finance, receipt, stock, SmartCompra mutation, SQL o activación Android.
- Tres rutas **nuevas** exactas B6 autorizables SOLO después de guard documental y READY independiente:
  1. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialReviewPresentation.kt
  2. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialReviewPresentationTest.kt
  3. .github/workflows/faro-v5-s4-w04-d4-review-presentation.yml
- CI futura Java 21, SDK 37, JUnit5 por nombre de clase con 8 casos únicos y artifact XML; gradle :app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialReviewPresentationTest. No cambiar D0–D3C. Commit código FARO-UNIT:S4_W04_D4_HISTORICAL_REVIEW_PRESENTATION_DEV en línea propia, primero revalidar permiso READY en PARENT y preflight exacto tres rutas, HEAD y writer CAS.

## B5 — Ocho adversarios definidos (NO ejecutados)

T01 referencias correctas y salida canónica inmutable, grupos y marcas de tiempo preservados; cero acceso host/Finance.
T02 sustituir observación por otra del mismo comercio/moneda o manipular estimate/choices/fingerprint/watermark => INVALID_TRACE y ningún display parcial, usando D3C como único validador.
T03 D3A grupo sustituido, needId duplicado, subtotal forjado o total de grupo inconsistente => GROUP_MISMATCH/INVALID_TRACE; no usar total suministrado por UI.
T04 dos monedas ARS/USD y dos comercios: agrupaciones separadas, sin suma global ni FX; orden independiente de entrada.
T05 escenario WHAT_IF descuento inmediato, cashback futuro y condición bancaria sin verificar: campos distintos; **no** tratar reintegro futuro como saldo, ahorro inmediato o precio pagado.
T06 PARTIAL, faltantes, no modelados, 0 promociones: no completar con ceros ni inventar cobertura; badges/avisos honestos y ausencia de checkout.
T07 fuente literal maliciosa, URL de sourceRef, HTML, caracteres Unicode inválidos: cadena inerte/rechazo tipado, sin ejecución ni egress; controles de caracteres adecuados.
T08 200 líneas y listas mutables, overflow de Long, reorder de entradas: copia inmutable, salida determinista, overflow fail-closed. Exigir exactamente 8 JUnit5 distintos, tests=8 failures/errors/skipped=0, ZIP/XML verificables en B7 futuro.

## Gate y estado de frontera

**B1_B5_DOC_ONLY_NO_READY.** Las tres rutas no se conceden por este documento. Primero commit documental, luego guardia real del SHA documental SUCCESS; inspeccionar permisos y writer RELEASED; SOLO en un commit documental posterior establecer READY acotado a las tres rutas y evidencia SHA de este B1–B5. No programar B6 desde Chat y no iniciar Work automáticamente. Si guard rojo o condiciones no verificadas, mantener BLOCKED.

Work podrá realizar B6–B7 únicamente si después se confirma READY individual y gate, sin nueva preparación, y debe certificar CI+XML reales, consumir READY y liberar writer. W04-E UI y W04-F cierre global son unidades posteriores con su propio B1–B7; G15 y V2 productiva OFF, no pagos ni Apply en esta misión.
