# FARO V5 — W04-C1b1 · términos tipados, SQLite Evidence2 aislada STAGED · B1–B5

Fecha 2026-11-20, base SHA `12ea63d37d697d64dd755da7bef9808fd72f8c5a`; unidad `S4_W04_C1B1_STAGED_TYPED_TERMS_DEV`. Solo DEV. C0A 12/12, C0B 9/9, C1a 9/9 B7 PASS y NO repetir. Esta preauditoría no autoriza código por sí misma.

## B1
- `AGENTS.md`, pointer, permiso C1a BLOCKED, checkpoint y C1b preaudit leídos. HEAD `12ea63d37d697d64dd755da7bef9808fd72f8c5a`, guardia `44805285298` SUCCESS, writer RELEASED epoch 1441.
- `CommercialEvidence.kt` blob `cf9d53089cafd93538e6b03f9873c98264d20b65`: schema1; tabla `commercial_evidence`; `record` transacción IMMEDIATE, idempotency key+payload fingerprint, timestamps observación/adquisición separados y sourceKind intacto. `priceHistory` lee v1. No metadata de localidad, plazo, presentación ni promociones.
- `EvidenceBackupPort.kt` blob `23859d874c6203edf8cebdf078a5771315bdc5b0`: snapshot v1 por registros; `EvidencePortabilityPort.kt` blob `104522e7605f3a2924fcf7ccbcf8f40a04a08399`: watermark `faro-portability-commercial-v1`; `CommercialEvidenceMaintenancePort.kt` blob `06b2e5f912fd274e80dd0acb2e923f78c3a09d83`: importFresh no equivale a preservar identidad de MANUAL.
- `SemanticBackupSetCodec.kt` blob `4c090ca1c1e819fb83eee6ce822c3628edbb868e` escribe solo campos v1 de Commercial; `FaroV5FiveStoreSemanticCodec.kt` blob `2624bbe4c89a5a8a3c9276223190e1d1face8328` lo envuelve sin agregar terms. `FaroV5GenerationStoreGraph.kt` preflight exige schema1 y `FaroV5FiveStoreBackupCapture.kt` delega snapshot de Commercial. Modificar schema sin versionar codecs perdería datos: STOP.
- No se han ejecutado tests de C1b.

## B2
V4.4 SmartCompra L11/L15 admitía contexto comercial. En V5 observación no significa oferta vigente: se mantienen todos los campos v1, evidencia/realidad separados, sourceRef opaco, moneda sin FX, localidades y vencimientos UNKNOWN salvo prueba. Reutilizar store Commercial existente, WAL FULL, gate común, sin nuevos owners/roots. C1a, C0A, C0B, W1 Finanzas y V2 productiva intocables. La migración solo opera sobre STAGED DEV explícita; el opener legacy permanece schema1.

## B3
Investigación externa primaria: SQLite ALTER TABLE `https://www.sqlite.org/lang_altertable.html` (migración transaccional; ADAPTAR), SQLite PRAGMA `https://www.sqlite.org/pragma.html` y WAL `https://www.sqlite.org/wal.html` (USAR versionamiento y durabilidad), SQLite Online Backup `https://www.sqlite.org/backup.html` (REFERENCIA; no reemplaza semantic backup), Android Room migration testing `https://developer.android.com/training/data-storage/room/migrating-db-versions` (ADAPTAR patrón de tests sin convertir owner a Room).
A elegida: nueva tabla 1:1 `commercial_evidence_quote_terms` FK evidence_id y fingerprint de terms; conserva datos/identidades de tabla original y permite términos UNKNOWN para historia v1.
B descartada: columnas opcionales en tabla antigua, más riesgo de regresión a lecturas v1 y de mezcla de identidad/precio.
C descartada: JSON oculto en sourceRef, SQLite extra, API paga, inferencia de promos, conversión FX o copiar registros MANUAL como IMPORT.

## B4
**División controlada** por el alcance real de backups/versiones. C1b1: implementar tipos, migración 1→2 opt-in aislada STAGED, nueva observación + terms en una sola transacción; idem key/fingerprint de base y metadata fingerprint; legacy v1 sin upgrade; la lectura de v1 migrada retorna terms UNKNOWN, jamás inventa localidad ni vigencia. Reabrir esquema2 solo en constructor/factory explícita, sin generación activa.
Localidad UNKNOWN/código canónico; issuedAt/validFrom/validUntil opcionales con intervalos; presentación cantidad milésimos >0, unidad UNIT/GRAM/MILLILITER, packCount >0; pago UNKNOWN/CASH/DEBIT/CREDIT/TRANSFER/OTHER; elegibilidad UNKNOWN/GENERAL/RESTRICTED; promo UNKNOWN/NONE/DISCOUNT_PERCENT/FIXED_AMOUNT con fuente literal separada; texto condiciones acotado; promociones nunca inferidas. Monto >0, currencyCode 3 mayúsculas sin conversión.
Mientras C1b2 no exista, **backup/restore, semantic migration y portability de Evidence2 fallan cerrados** sin emitir snapshots parciales, y graph de generación debe rechazar schema2, nunca activarlo.
Rutas funcionales EXACTAS para C1b1 (4):
1. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`
2. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceTypedTerms.kt` (nuevo)
3. `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceTypedTermsTest.kt` (nuevo)
4. `.github/workflows/faro-v5-s4-w04-c1b1-terms-staged.yml` (nuevo)
No ampliar sin otra guardia/permiso. C1b2, independiente, debe contemplar `EvidenceBackupPort`, `EvidencePortabilityPort`, `CommercialEvidenceMaintenancePort`, `SemanticBackupSetCodec`, `FaroV5FiveStoreSemanticCodec`, `FaroV5GenerationStoreGraph`, `FaroV5FiveStoreBackupCapture` y tests/codificación. Sin READY de C1b2 todavía.

## B5
T01 esquema2 nuevo, v1→v2 MANUAL/TOOL/IMPORT con IDs/fingerprints/sourceRef/timestamps iguales; T02 consulta terms UNKNOWN y exact typed tras restart; T03 legacy opener v1 no migra y rechaza v2; T04 one-transaction insert terms y rollback/replay/conflict; T05 FK/UNIQUE, integrity_check, WAL FULL, falla de migración no deja esquema mixto; T06 fechas, locality UNKNOWN, moneda ARS/USD sin FX, packs, source hostile, elegibilidad/promos; T07 Evidence2 backup/restore, migration y portability FAIL-CLOSED hasta C1b2; T08 generación schema1 rechaza v2 sin activar V2; T09 material write gate y no modificacion Finance/stock; T10 CI exacta y XML, guardia parent, B7 independiente. Ningún test C1b fue ejecutado antes de B6.

## Puerta
Esperar guardia documental SUCCESS y emitir READY individual antes de cualquier cambio funcional. El PASS de C1b1 será únicamente parcial staged, NO certifica C1 completo ni cinco-store backup, portabilidad ni activación. C1b2 requiere su B1–B5/READY y evidencia real B6–B7.
