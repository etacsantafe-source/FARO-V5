# FARO V5 · W05-A2B1A · Admisión tipada Review reserve/release

Unidad: `S4_W05_A2B1A_STOCK_RESERVE_RELEASE_DEV_ADMISSION`. Base c4d1901549005e7fdb990eb5b83aba62099eb01c. Estado **PREAUDITADO CONDICIONAL, NO_READY; requiere B7 predecesor y delta audit**. Evidencia B1–B5 de diseño, NO son resultados de tests. V2 productiva, stock real, pagos, Auto/Noche OFF. Moto G15 NOT_RUN.

## B1 — Evidencia

Entrada: A2B0C1 y A2B0C0 B7 certificados; catalogo N-035 real. HouseholdStockReceiveDevAdmission/Review controla contract, context y consentimiento de recepción; reserve/release solo declarados en catálogo, sin grant efectivo.

## B2 — V4.4 y V5

V4.4 semántica reserva/liberación separada de comprar/recibir. Reusar CanonicalPayload, OperationContractLookupPort, HouseholdNeedQuantity, epoch, principal, pointer, review de stock.

Matriz V4.4: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md`; reutilización: `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`. No copiar legado ni repetir B7 previo.

## B3 — Soluciones externas

REFERENCIA Grocy https://github.com/grocy/grocy/blob/master/grocy.openapi.json sin servidor ni código; ADAPTAR Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first y SQLite transactions https://www.sqlite.org/lang_transaction.html; DESCARTAR preview mutador y heredar permiso de stock.receive.

Sin nuevas dependencias, servicios ni APIs pagas. Ante límites reales, retener control local y pruebas adversariales; los proyectos externos son patrones/referencia, no permisos.

## B4 — Diseño y límites

ReviewOnly estrictamente sin mutar: operación y capability ID **verificados del catálogo**, principal/epoch/foreground/consentimiento fresco, item+unidad homogéneos, físico neto real de receipts menos Undo y reserved ledger14. RESERVE solo cabe si neto no comprometido; RELEASE refiere a evento existente y saldo; payload binario canónico incluye sequence/ID, source ledger revision, intent, quantity y fingerprint. `revalidate` exige lectura owner actual, no chat ni fecha auto autorizan.

**Rutas candidatas; reconfirmar tras B7 de dependencia, sin READY anticipado:**

1. `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockReservationReview.kt`
2. `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockReservationDevAdmission.kt`
3. `v5/owner/household/src/test/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockReservationDevAdmissionTest.kt`
4. `.github/workflows/faro-v5-s4-w05-a2b1a-dev-admission.yml`

No main, V4.4, ni activación de stock productivo. Toda ruta adicional obliga a rehacer solo el delta del permiso.

## B5 — Pruebas negativas propuestas

- T01 Reserva 2 con físico3 => ReviewOnly sin filas; reserva4 => BLOCKED.
- T02 Release parcial y principal/SKU erróneo => rechazar indebidas.
- T03 Capability/grant/catálogo no exacto => STOP, no inventar N-ID.
- T04 Cambio pointer/epoch/revisión/consentimiento entre pasos => DENIED.
- T05 Codec Review bytes no canónicos y cantidades overflow => DENIED.

Exigir guardia 7 bloques documental SUCCESS, evidencia blob SHA, permiso READY individual y preflight de **cada ruta** antes de B6; código `FARO-UNIT:S4_W05_A2B1A_STOCK_RESERVE_RELEASE_DEV_ADMISSION` en línea propia, CI del SHA real, XML JUnit original tests>0 y failures=errors=skipped=0, B7 independiente, permiso consumido, writer CAS RELEASED. Para sucesores hay que reconfirmar HEAD y contrato real después del predecesor. Nunca declarar B7 desde este documento.

## Última revisión B1-B5, delta previo a Work (26/11/2026)

Catalog CONFIRMED in v5/spec/generated/contract-baseline.tsv: stock.reserve and stock.release both N-035, executor household, REALITY_MUTATION, BINDABLE. Same capability as stock.receive does NOT share a consent/grant/intent. Do not automatically convert quantity units.

Estado: PREAUDITADO_CONDICIONAL/NO_READY hasta B7 del antecesor, nueva guardia documental, permiso propio y preflight exacto. Este delta NO ejecuta B6/B7.

## Delta contra B7 de A2B0C0

C0 entrega selección completa, revisión de ledger y valores Long de físico y
reservado filtrados por principal. El dominio Household no debe importar el
grafo ni Reality, que dependen de él. Definir en las rutas candidatas un port
de lectura para testigo del owner, y ligar su principal, selección, item, unidad,
revisión y, para RELEASE, referencia y saldo del evento original. B1B1 montará
ese adaptador con la fuente SQL14. El preview calcula el límite internamente y
no publica un campo available ni escribe eventos.

stock.reserve y stock.release aparecen en el catálogo real con N-035,
household, REALITY_MUTATION y BINDABLE. El workflow valida dichas filas
y el test comprueba que una variante de contrato, consentimiento, epoch,
puntero, revisión o unidad falla. El consentimiento se verifica para el
request exacto antes y después de leer y no se hereda de stock.receive.
El payload re-encode byte por byte conserva el código de operación y el
intent. Mantener la aprobación foreground de Review separada de la
confirmación Apply que tendrá su propio recibo.

Fuentes del delta: https://developer.android.com/topic/architecture/data-layer/offline-first
y https://sqlite.org/lang_transaction.html. Se adapta el origen local
canónico, con chequeo de revisión y recaptura, sin previsualización mutadora.
