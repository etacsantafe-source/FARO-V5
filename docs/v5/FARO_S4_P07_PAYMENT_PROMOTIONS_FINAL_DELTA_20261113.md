# FARO V5 — S4 P07 · Cierre documental de integración, compras y finanzas

2026-11-13. Unidad `S4_PREP_P07_INTEGRATION_PROJECTION`. **Cierre documental; no aprobación de código, SQL, host o S4 formal.**

## B1 — Alcance, continuidad y autoridad humana

HEAD base `5fc305449b9fe11bc9f4bb53e29ea2954c3cc085`; rama `work/s3-night-20261102`. Writer RELEASED epoch869 al inicio y HELD epoch870 por CAS, DOC_ONLY. Permiso funcional `FARO_PROTOCOL_WORK_PERMIT.json` BLOCKED, S3 pausada, S4 formal OFF, V2 productiva/Auto/Noche OFF. AGENTS.md, pointer y retroauditoría del protocolo leídos.

Fuente previa: `FARO_S4_P07_INTEGRATION_PROJECTION_B1_B5_20261113.md` blob `97737bf45b996115152bc8ce3d4b050f8d3094c2` ya define 10 fronteras G01–G10, 14 reglas financieras M01–M14 y 22 pruebas P7T01–P7T22, aún no ejecutadas. `FARO_S4_P07_B7_STAGE_HUMAN_PENDING_20261113.md` blob `3ae7e54e7b8eb80b16615cfdd9914c258a7ef550` documentó H-P07-01 antes de resolverla; se conserva como evidencia histórica y este delta la supera.

El usuario aprobó `household` independiente para DISEÑO. Después respondió: «Opción A. El dinero lo maneja finanzas. Compras maneja los precios, las promociones con las formas de pago y genera su presupuesto. Al momento del pago, finanzas y compras solo hacen lo suyo». **H-P07-01 queda APROBADA opción A**, una confirmación humana, dos owners separados; no autoriza ejecución funcional ni pagar en el banco.

Fuentes conservadas: P00–P06 SHAs ya congelados en P07 B1–B5; catálogo V5 `contract-baseline.tsv` blob `5653a9758f682f683a852dcf286211bae71c41d0` tiene 180 operaciones, de ellas 32 owner household; `v5/settings.gradle.kts` aún no tiene módulo `owner:household`. Kernel `ApplyPlan.kt` @`5808ef0021ff91fde19339bdbb06c2f7e255a2e1`, `ApplyTransactionExecutor.kt` @`772decfde8aee398eda6c84149f9f0a4234d6d7f`, RealityStore @`be72978b28d79366452a3f43c7018db72b2ca9f7` son infraestructura interna, **NO prueba de atomicidad de ambos owners**.

## B2 — Comparación V4.4 frente a arquitectura V5

P02 L04/L11–L17 auditó Finanzas, presupuesto SmartCompra, compras, stock, Cocina y fallas G0D3 por identidades/rutas no atómicas en V4.4. P01/P03 descubrió Finance parcial y hogar/compras solo descriptores sin host. P05 F03/S02/S03/S04 propuso flujo manual, separación comercial-financiera y recepción física; P06 fijó gráfico 12 meses, línea cero y rojo por debajo, Radar C. Reusar reglas buenas; **no importar runtime V4.4** ni declarar que los contratos se ejecutan.

## B3 — Alternativas sin repetición

**A APROBADA:** una revisión y autorización humanas con vista de ambas autoridades, commit atómico y recibo verificable. **B NO ELEGIDA:** dos confirmaciones manuales consecutivas. **Prohibido:** escribir Household y llamar después Finance sin garantía, presentando éxito si Finance falla. Si falta integración atómica certificada, DENEGAR con explicación en lugar de ejecutar parcialmente. Benchmarks P04 Actual Budget, Grocy y KitchenOwl sólo como patrones; no servidor/API paga/AGPL importado.

## B4 — Doce reglas de responsabilidad y seguridad

| ID | Tema | Estado | Contrato |
| --- | --- | --- | --- |
| D01 | Una confirmación A | APROBADA_USUARIO | Una sola revisión humana muestra resultado comercial y financiero bajo autoridades distintas |
| D02 | Household independiente | APROBADA_USUARIO_SOLO_DISEÑO | SmartCompra, Stock y Cocina comparten dueño household, sin agregar OperationIds, DB ni writer |
| D03 | Finance dinero | APROBADA_USUARIO | Finance único owner de saldos, cuentas, asientos, obligaciones, financiación y pagos financieros |
| D04 | Compras presupuesto/promos | APROBADA_USUARIO | Household compara comercios, precios, condiciones según forma de pago y genera presupuesto comercial |
| D05 | Instrumento y elegibilidad | PROPUESTA_TECNICA | Finance valida acceso/tipo de medio y comunica a Household solo atributos mínimos autorizados; Household determina descuento aplicable |
| D06 | Atomicidad compuesta | CONDICION_TECNICA_OBLIGATORIA | Dos nodos con autorizaciones propias bajo una Reality transaction, rollback total y receipt consistente, sin medio éxito |
| D07 | Descuento inmediato | REGLA_DERIVADA | Household fija importe rebajado efectivo; Finance registra exactamente lo abonado confirmado |
| D08 | Reintegro posterior | REGLA_DERIVADA | Household detalla promoción; Finance registra pago íntegro y solo acredita reintegro cuando ocurra |
| D09 | Cuotas y crédito | REGLA_DERIVADA | Household muestra precio/cuotas ofertadas; Finance registra deuda/cargos según hechos sin debitar cuota futura como REAL |
| D10 | Compra != stock | REGLA_VIGENTE | Comprar, pagar, recibir y consumir son acciones diferentes y no implican escritura mutua automática |
| D11 | Sin atomicidad comprobada | FAIL_CLOSED | Bloquear la ejecución compuesta; no sustituir por dos commits silenciosos o pago bancario |
| D12 | Proyección financiera | REGLA_DERIVADA | Cotización y carrito no disminuyen saldo; obligaciones autorizadas, cuotas y reintegros tienen cobertura y certeza distintas |

## B4 — Secuencia observable de una compra pagada

1. Compras reúne artículos, cantidades, fuente/vigencia/precio/localidad/moneda, promociones y condiciones de cada medio de pago; compone un presupuesto sin afectar saldos. Si falta precio/cobertura, se muestra estimación o sin dato, no certeza falsa.

2. El usuario selecciona medio de pago. Finance verifica acceso/atributos financieros necesarios bajo grant independiente; Household evalúa elegibilidad de descuentos/promociones sin obtener credenciales ni saldo completo. Una tarifa vencida o cambio de tarjeta exige recomputar.

3. **Review única** con dos paneles: Compras muestra artículos, precio, descuento, reintegro/cupones y presupuesto; Finanzas muestra cuenta/medio, importe REAL a registrar o deuda/cuotas y fechas, moneda, autorización. Cambiar un campo invalida fingerprint y exige nueva revisión. El usuario confirma una sola vez.

4. Si y solo si está demostrada una transacción REALITY compartida bajo WriteCoordinator único, verificaciones ACL/epoch/generation por owner y recuperación idempotente, los nodos separados de Household y Finance se aplican como un solo resultado: ambos COMMITTED o ninguno. La intención y recibo se vinculan y no se ejecutan dos veces por Chat/resumen. UNKNOWN obliga a consultar receipt antes de reintentar.

5. El registro comercial no mueve efectivo ni aumenta stock; la mutación Finance solo registra el hecho financiero correspondiente, NO inicia pagos externos. `stock.receive` exige recepción física confirmada adicional.

## B4 — Promociones y financiación

Descuento inmediato: si efectivamente se paga a precio rebajado, Finance registra ese importe. Reintegro posterior: el pago entero es REAL, mientras que el beneficio futuro está condicionado y solo es ingreso REAL al acreditarse. Cuotas/financiación: Household ofrece costo y modalidad; Finance administra la deuda/cronograma y distingue cargo REAL de cuotas futuras, sin doble descuento. Beneficios no monetarios (puntos) no son efectivo sin hecho/valor comprobados. Importe presupuesto no es saldo disponible. ARS/USD nunca se suman nominalmente sin fuente y permiso FX.

## B4 — Ocho escenarios de pago representativos

| ID | Situación | Resultado |
| --- | --- | --- |
| C01 | Contado con 20% descuento sobre 100.000 | Compras precio final 80.000, Finanzas egreso real 80.000 si pago confirmado |
| C02 | Reintegro futuro 20.000 sobre pago 100.000 | Finanzas gasto REAL 100.000; eventual reintegro ESTIMADO hasta acreditación |
| C03 | 120.000 en tres cuotas con tarjeta | Compras precio/plan; Finanzas obligación y cuotas, no egreso de contado ficticio |
| C04 | Promoción vencida o no válida para medio elegido | Invalidar cotización y pedir nueva revisión; no aplicar precio falso |
| C05 | Chat y carrito proponen misma compra | Una intención idempotente, no dos registros comerciales ni dos gastos |
| C06 | Compra pagada no recibida | Compras registra compra/pago; stock físico permanece sin cambios |
| C07 | Carrito presupuestado sin pago | No movimiento REAL de Finanzas; no disponibilidad financiera inventada |
| C08 | Pago combinado por dos medios sin contrato multicuenta | STOP o nueva revisión autorizada, jamás dos débitos arbitrarios |

## B4 — Proyección, Radar, seguridad, egress y dependencias

Reusar M01–M14 del P07 anterior: gráfico compacto M-6..M-1 + M0 + M+1..M+5, operativa móvil M0..M+5, retener M+7/M+12/M+36; REAL/COMPROMETIDO/ESTIMADO/SIMULADO separados, asOf y source revision, cero siempre visible y tramos bajo cero rojos, reales vs previstos también por patrón/etiqueta, no huecos rellenados. Delta: cotización no equivale a gasto ni deuda; descuento inmediato afecta importe pagado, reintegro posterior NO ingreso REAL, cuotas requieren dedupe de deuda y pago.

Radar C: Inicio tarjeta compacta, Chat explicación y Asuntos contexto, sin raíz/Reality nueva; con source denegada score parcial/NULL, nunca 'todo tranquilo'. Tokens de lectura, grant individual, foreground/generation/epoch y revocación revalidados antes de abrir. Egress opt-in con minimización; operación manual sin IA/Internet sigue posible. Recurrencia, Matters, Avisos y delivery OS no se colapsan. Backup V4.4 no se migra a Reality8 sin compatibilidad certificada.

## B4 — Puertas P08 NO concedidas

P07 define PRODUCTO, no ejecutor. P08 debe preparar por unidad scopes B1–B5 y permiso específico anterior a código, comprobar atomicidad cross-owner bajo una sola Reality transaction (de lo contrario opción A deshabilitada), semántica de promociones/reintegros/cuotas, IDs/receipts, ACL/recovery/outbox, dataset fixtures sin G15 aún, UI accesible. No modificar catálogo180 ni owners existentes por inercia; no SQL/Room schema sin permiso. P08 queda lista para INVESTIGACIÓN READ_ONLY, no Work READY ni S4 formal.

## B5 — 18 pruebas adversariales NUEVAS, no ejecutadas

| ID | Escenario | Aceptación futura |
| --- | --- | --- |
| PX01 | Descuento inmediato 20% | Egreso confirmado 80.000; no 100.000 |
| PX02 | Reintegro futuro aún no acreditado | Gasto REAL íntegro y reintegro no REAL |
| PX03 | Crédito en tres cuotas | No registrar total como salida en efectivo y volver a descontar cuotas |
| PX04 | Oferta vence antes de Apply | INVALIDATE/re-review; cero mutaciones |
| PX05 | Medio de pago no elegible | No aplicar promoción |
| PX06 | Finance ACL/grant revocado | No exponer saldo ni ejecutar efecto financiero |
| PX07 | Doble intento Chat + Compras | Mismo fingerprint/identity, un efecto y receipt verificable |
| PX08 | Falla Finance después de preparar Household | Rollback completo, no medio COMMITTED |
| PX09 | Crash después del COMMITTED | Recovery por recibo; no segundo Apply |
| PX10 | UNKNOWN tras navegación | Bloquear reintento hasta reconciliación |
| PX11 | Compra pagada pero no recibida | Stock físico permanece inalterado |
| PX12 | Recepción parcial 7/10 | Sólo stock +7 y faltantes 3 |
| PX13 | Presupuesto no pagado | No asiento REAL |
| PX14 | Puntos/beneficio sin valor monetario | No inventar efectivo ni reintegro |
| PX15 | Pago en dos cuentas sin contrato | STOP; sin débitos independientes inferidos |
| PX16 | Devolución tras compra COMMITTED | Reversión/compensación por owner con receipts, nunca borrar historia |
| PX17 | Compra USD con cuentas ARS | No suma/conversión automática sin fuente y permiso |
| PX18 | Reintegro en M+3 y cuota en M+12 | Certezas correctas, no duplicación ni truncamiento por ventana |

Se suman a P7T01–P7T22 del P07 B1–B5 existente: **40 pruebas de P07 DISEÑADAS, cero nuevas ejecutadas**. No reutilizar PASS S3/V4.4 como integración funcional P07. La ejecución real y G15 físico siguen para unidades permitidas futuras.

## B6 — Publicación acotada

Publicar solo este delta, `docs/v5/FARO_S4_P07_B7_FINAL_20261113.md` y `docs/v5/FARO_CONTINUITY_POINTER.json`; preservar histórico P07 preliminar, P00–P06, permisos BLOCKED, S3 pausada/S4 OFF y V2 productiva OFF. Writer CAS epoch870. P07 `DONE_VERIFIED_DOC_SCOPE_HUMAN_PAYMENT_POLICY_A` (no código) y P08 `READY_TO_START_READ_ONLY_NOT_EXECUTED`.

## B7 — Checklist POST-RELEASE

Confirmar HEAD y blobs, D01–D12, C01–C08, PX01–PX18, alcance documental y consentimiento A, continuidad de 10 G/14 M/22 P7T previos, diff SOLO 3 documentos, writer RELEASED epoch870 con target_head exacto, permiso funcional BLOCKED, P00–P06 y main intactos, P08 NO ejecutada. Android/CI/APK/G15/Groq nuevas NO_RUN; no declarar P07 funcional PASS.


