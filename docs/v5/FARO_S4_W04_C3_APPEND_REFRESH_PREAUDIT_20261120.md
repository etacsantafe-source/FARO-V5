# FARO V5 — W04-C3 · Refresco append-only tipado con confirmación humana — preauditoría inicial de diseño

2026-11-20. Base C1B2 `4bffc59b59458c185932bad8c8c127d8b84bbb00`; C2 se prepara en unidad separada. **DESIGN_ONLY, NO READY, NO B6**.

## B1
`CommercialEvidenceStore.recordWithTypedTerms` en Evidence2 STAGED (opt-in) inserta registro y terms en una transacción SQLite y verifica replay contra idempotencyKey, payloadFingerprint y terms. `S4W04ManualPriceObservationHost.kt` C1a usa `CommercialEvidenceStore.record` de schema1 y rechaza terms conocidos; no actualizarlo silenciosamente ni montar Evidence2 en ACTIVE. `S4W04CommercialPriceReadHost` tiene grants Household+Commercial y fences C0A/C0B para lectura legacy. No existe un refresh tipado host con autorización/Review end-to-end: no declarar implementado.

## B2
V4.4 SmartCompra refrescaba cotización; V5 no debe reemplazar observación original ni atribuir vigencia con dato incompleto. Nueva observación distinta con source, observedAt opcional y acquiredAt del owner; no relacionar old/new como supersession certificada sin durable link. Read-only C2 no crea receipt, Finance no registra gasto, Household no aumenta stock.

## B3
ELEGIDA CONDICIONAL: adaptar `recordWithTypedTerms` STAGED con contrato de confirmación humana, idempotencia estable y revalidación antes y después, más history C2 para demostrar append; si la sesión única no tiene un puerto de escritura tipada autorizado, STOP y dividir `C3A` (prueba owner STAGED) y `C3B` (admisión host). DESCARTAR UPDATE de observaciones, segundo root/gate, `commercial.quote` como autoridad de escritura, refresh automático de red o monedas mezcladas.

## B4
C3 requiere Review capturado de referencia `productKey`, snapshot watermark, fecha/corte, sourceKind MANUAL, currency, amountMinor >0, merchant, terms tipados, `localRequestId`, principal, generation epoch/pointer y grants. Confirmar humano, comprobar snapshot/fingerprint actual y grants antes de insert. New idempotency per request and canonical payload (observación+terms), EXACT replay returns existing record with no duplicate; diferente payload mismo key rechaza. Tras commit nunca declarar rollback por revocación tardía: recovery por idempotency/receipt existente. Historial anterior intacto; devolver `NEW_OBSERVATION`, no reemplazo. Sólo DEV STAGED; no MONEY, UI ni G15.

## B5
Casos: nuevo record preserva antiguo+terms, misma key idéntica replay, key igual payload distinto conflict, crash/rollback SQLite, revocación y ABA antes/durante/después del commit, fuente opaca, observedAt nula no inferida, promo caducada no se transfiere, limit 1..200 de historial, no segunda transacción Household/Finance, reinicio reaparece evidencia previa, guardias XML. Pruebas NO RUN.

## Gate
Después de C2 B7 revisar firmas, actualizaciones de esquema, contrato existente OBSERVATIONAL_WRITE, rutas exactas y riesgo de mutación. Hacer B1–B5 ESPECÍFICOS de C3 con SHA real antes de READY; no heredar C2 ni C1a. El alcance debe preservar C1B2 certificado.
