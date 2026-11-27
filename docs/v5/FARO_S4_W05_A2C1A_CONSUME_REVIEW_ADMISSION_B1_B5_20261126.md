# FARO V5 — W05-A2C1A · stock.consume Review y admisión B1–B5 (PREAUDITORÍA)

**Unidad:** `S4_W05_A2C1A_CONSUME_REVIEW_ADMISSION`
**Base verificada:** `6cbee2adc887c62e931765eba5c4f5d31747e89b`, rama `work/s3-night-20261102`, 26/11/2026.
**Dependencia:** A2C0C2 B7 PASS acotado DEV, evidencia `docs/v5/FARO_S4_W05_A2C0C2_B7_PASS_SQL15_JOURNALED_FIVESTORE_DEV_20261126.md`; XML original 35/35, guardia del checkpoint 45917998619 SUCCESS.
**Estado de esta unidad:** B1–B5 DOCUMENTADOS Y CONTRASTADOS, todavía **NO CERTIFICADOS POR GUARDIA**; B6 NOT_RUN, B7 NOT_RUN. A2C1B–F mantienen dependencias secuenciales y NO tienen permiso READY.
**Frontera:** V2 productiva, stock real, pagos, FARO Auto histórico, Moto G15 físico y consumo de reservas: OFF.

## B1 — Evidencia y brecha observada

- `v5/spec/generated/contract-baseline.tsv` blob `5653a9758f682f683a852dcf286211bae71c41d0` define `N-035 stock.consume household REALITY_MUTATION BINDABLE`; no hay `stock.consume.undo` en el baseline.
- `HouseholdStockReservationReview.kt` blob `2746ca957ac967a4b185a6ef18012cb1f367b641` demuestra payload canónico ligado a operación, puntero generación+secuencia, principal, hostEpoch, item, unidad, cantidad, intentKey y revision. `HouseholdStockReservationDevAdmission.kt` blob `16d3b7bfaddcddf985ed5d47f9b522a96c01f77b` exige descriptor, consentimiento revocable y posición actual.
- `RealityHousehold15ConsumptionRowsPort.kt` blob `4af7a385640977f6e984d5a49878926b7ad60287` contiene `require(row.reservationRef == null)`, con error explícito para reservar-consumir; este gate **no se relajará**.
- Faltan `HouseholdStockConsumptionReview.kt` y `HouseholdStockConsumptionDevAdmission.kt` certificados. Una lectura SQL15 DEV no habilita Apply ni crea permisos.

## B2 — Comparación V4.4 y reutilización

- Matriz `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md` blob `4b9789004459eed6e95bc4d4f8032ee3b4c9f340`, R12/R13: compra y pago no aumentan stock; reservar y consumir tienen efectos distintos; Cocina permanece dentro SmartCompra.
- ADAPTAR `HouseholdStockReservationReview` y `HouseholdStockReservationDevAdmission`, no copiar runtime V4.4 ni reutilizar grants de recibir/reservar. Review solo lectura, Apply diferido a A2C1B/C, Undo a A2C1D.

## B3 — Investigación de alternativas (solo referencia externa)

Fuentes verificadas: [SQLite transacciones](https://www.sqlite.org/lang_transaction.html) (`BEGIN IMMEDIATE` puede fallar con `SQLITE_BUSY`), [SQLite WAL](https://www.sqlite.org/wal.html), [Android offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first), [Room Transaction](https://developer.android.com/reference/androidx/room/Transaction.html) y [Grocy OpenAPI](https://github.com/grocy/grocy/blob/master/grocy.openapi.json) (`/stock/bookings/{bookingId}/undo` y `/recipes/{recipeId}/consume`).
- ADAPTAR: solicitud canónica explícita offline con consentimiento propio y validación de posición.
- DESCARTAR: inferencia automática de receta/Chat/compra y consumo sin confirmación; transacción remota de stock y APIs pagas.
- REFERENCIA: Grocy para separar semánticas, **no** para incorporar su servicio/código ni habilitar consumo de recetas por defecto.
- Pruebas negativas derivadas: colisiones de intent, `SQLITE_BUSY`, revisión obsoleta y operación distinta en catálogo.

## B4 — Solución mínima y rutas exactas propuestas para un futuro permiso

Request DEV: CONSUME, contexto principal + `generation(id, sequence)`, hostEpoch, foreground, stockItemRef, unidad, cantidad positiva en milésimas, sourceRef verificable, intentKey y ledgerRevision. `reservationRef = null` obligatorio. Leer descriptor N-035 y consentimiento de consumo; `review()` compara posición/principal/epoch/generación/revisión y NO escribe. No inventar saldo o fuente al faltar SQL15 real.

Rutas propuestas, de alcance cerrado para A2C1A **únicamente tras guardia documental**:
1. `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockConsumptionReview.kt`
2. `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockConsumptionDevAdmission.kt`
3. `v5/owner/household/src/test/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockConsumptionDevAdmissionTest.kt`
4. `.github/workflows/faro-v5-s4-w05-a2c1a-consume-review.yml`

No tocar `RealityStore`, otros owners, A2C0 ya certificado ni operaciones/productividad real. Una ruta adicional requiere nueva preauditoría y permiso separado.

## B5 — Auditoría adversarial previa

- **A01** Review, cancelar y preview no mutan ledger, WAL ni recibos.
- **A02** cantidad cero, negativa, overflow o mayor que físico disponible/reservado => BLOCKED.
- **A03** fuente ajena/no autenticada o unidad divergente => BLOCKED.
- **A04** consentimiento ausente/revocado, foreground falso, principal/hostEpoch/generation/revision cambiado => BLOCKED.
- **A05** descriptor N-035 ausente, owner/exposure/ejecutor incorrecto => BLOCKED.
- **A06** `reservationRef != null` => STOP; no habilitar consumo reservado.
- **A07** canonical payload alterado o intentKey duplicado incompatible => BLOCKED.
- **A08** Chat/receta/compra/pago nunca hacen Apply; no autopago ni stock real.
- **A09** dos intentos de la misma hipótesis roja => volver a B2/B3/B5; conservar CI/XML y no declarar PASS artificial.

## Gates futuros

Publicar esta preauditoría bajo writer CAS y verificar guardia SUCCESS de SHA nuevo. Solo entonces emitir permiso READY individual **en commit aparte** con `reviewed_head`, cuatro rutas exactas, blob SHA de esta evidencia y B6/B7 tipados. Verificar preflight por cada ruta. B6 y B7 requieren CI/JUnit XML originales y auditoría independiente. No certificar A2C1 global antes de A2C1F. La publicación documental por sí sola **NO** autoriza código.
