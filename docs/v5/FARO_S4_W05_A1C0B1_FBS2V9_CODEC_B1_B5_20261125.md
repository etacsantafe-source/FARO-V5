# FARO V5 · W05-A1C0B1 · FBS2v9 compras autenticadas, codec DEV · B1–B5

Fecha 2026-11-25; base comprobada `6a18acc43ae5aa2f749735b0ca51ca7ef6b4aa64`, rama `work/s3-night-20261102`; unidad `S4_W05_A1C0B1_PURCHASE_FBS2V9_AUTHENTICATED_CODEC_DEV`. Solo documentos de preauditoría: NO implementar, NO habilitar stock ni escritura sin permiso exacto y guardia exitosa.

## B1 — Evidencia y criterio verificable
- Antecesor obligatorio `S4_W05_A1C0B0_PURCHASE_STAGED_SCHEMA_DEV` B7 PASS: código `d2f0a7a60be0ddf1cce9c02c80879ef0f79d5b27`, CI `45538967777` SUCCESS, XML original **6/6**, artifact `14988217949`; guard código `45538967902` SUCCESS, guard checkpoint `45540204924` SUCCESS sobre `6a18acc43ae5aa2f749735b0ca51ca7ef6b4aa64`. Permiso anterior BLOCKED/consumido, writer inicialmente RELEASED epoch 1950. B7 evidencia `docs/v5/FARO_S4_W05_A1C0B0_B7_PASS_PURCHASE_STAGED_SCHEMA_20261125.md` blob `b391703f53076baa5d91e7e68571ed405c4076f2`.
- Reality12 migrada sobre **copia STAGED**: `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseSchema.kt` blob `fb93638abb908afbb59516a7bfdac15edcd2746b`: tabla STRICT `household_confirmed_purchase` con `purchase_ref` PK, `stock_item_ref`, `quantity_thousandths`, `unit`, `confirmed_by`, `confirmed_at_ms`, `provenance='MANUAL_EXPLICIT'`, `revision>0`. Migración no crea compras, conserva recibos existentes, datos de necesidades y finanzas. **No hay writer ACTIVE ni ingreso manual acreditados.**
- Drafters previos `docs/v5/FARO_S4_W05_A1C0B1_PURCHASE_BACKUP_DESIGN_DRAFT_20261125.md` blob `56dad2507d749c84969a2f211431c5f78ac858a2` y `docs/v5/FARO_S4_W05_A1C0B_PURCHASE_PROVENANCE_DESIGN_DRAFT_20261125.md` blob `911385665f7321197b96b3e90d71a5458d610b30`; sus rutas eran *candidatas*, ahora se congelan cuatro rutas exactas abajo.
- `FaroV5Household11Fbs2Codec` (blob `5c66681a0ee2b85fe498a174857aff007a930528`) v8, inner v6/v7, sección H11R, digests SHA256, presupuesto plaintext agregado. `FaroV5FiveStorePortableBackupCrypto` (blob `24d9969bbbc3d51df94fee94a9534db99a81f6bf`) expone `sealPayload/openPayload` privados y `sealWithHouseholdReceipts11/openWithHouseholdReceipts11` internos; FAROBAK2 AES-GCM autentica header+ciphertext **ANTES** de decodificar. `FaroV5Household11ReceiptBackupSectionCodec` (blob `4628d59935ce81c4e1e0bccb5a5a1e7df2d9cf9b`) protege estructura H11R.
- Objetivo medible de esta unidad: **FBS2v9 semántico autocontenido + sellado/apertura FAROBAK2 de compras+recibos**, con v8 intacto y recibos heredados preservados. No captura SQL real, no importación staged, no cutover, no aplicación ni activación. Esos efectos son unidades posteriores independientes, no declarar B7 global.

## B2 — Comparación histórica y reutilización V5
- P02 L11/L12 y P03 R11/R12: separar compra, recepción, finanzas/pago. Reusar implementación V5 exacta: v8 como payload nested autenticado bajo nueva versión 9, H11R codificado con su codec, `FaroV5FiveStorePortableBackupCrypto` para envelope FAROBAK2. No copiar V4.4 ni romper `FaroV5Household11Fbs2Codec`/lectores viejos/SQL11.
- El **digest interno solo detecta corrupción**; no provee autoridad ni autenticidad. El wrapper GCM conserva la única autenticación. Serializar `confirmed_by`/MANUAL_EXPLICIT es provenance, NO una autorización de sesión al restaurar.
- Datos de cart/price W04 son estimaciones y no son un registro durable de compra, ni generan compras durante decode/import.

## B3 — Alternativas técnicas e investigación
- A ELEGIDA: crear nuevo `FaroV5Household12Fbs2Codec` y snapshot tipado con `base: FaroV5Household11Fbs2Snapshot` (sección v8 encapsulada **byte-exact**) y sección v9 de compras Reality12, más dos métodos internos independientes de seal/open en crypto. Formato `FBS2`+versión9, longitud/digest de secciones, campos estrictos/canónicos, single 16 MiB max incl headers, y límite explícito para compras y filas. En decode primero tamaño/longitud/overflow, luego digest, decode exacto v8 y compras. Un lector v9 NO autoabre v8 por falla de autenticación ni lo reinterpreta; importación v8 es ruta explícita futura con compras vacías.
- B DESCARTADA: editar codec v8 o reemplazar métodos existentes con versión9: incompatibilidad con lector/backup certificado.
- C DESCARTADA: cifrado propio o nuevas claves; innecesario y podría crear fallos de autenticación.
- D POSPUESTA: SQL capture/restore de misma snapshot, FBS2v8→v9 staged import/cutover, grant de compra, Android. No mezclar efectos ni permisos.
- Reglas de diseño: ordenar referencias `purchase_ref` strict ascending y únicas, UTF-8 byte exacto, campos no vacíos dentro límites Reality12, `quantity_thousandths` entre 1 y `Int.MAX_VALUE`, unidad enumerada, principal/revisión/fecha canónicos, origen solo MANUAL_EXPLICIT. Verificación cruzada para cada recibo cuyo `purchase_ref` esté en el conjunto confirmado: mismo artículo/unidad y suma recibida ≤ comprada usando `Long` sin overflow; recibos legacy sin compra asociada son permitidos **sin fabricar una compra**.
- Fuentes primarias: https://csrc.nist.gov/pubs/sp/800/38/d/final (GCM authenticated encryption); https://www.sqlite.org/lang_transaction.html y https://www.sqlite.org/wal.html (una vista al capturar SQL, **fuera de este codec**). No añadir red, API paga ni licencias.

## B4 — Unidad exacta B6, rutas congeladas (4)
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12Fbs2Codec.kt` **NUEVO**: snapshot+sección compras tipados, encode/decode estricto FBS2v9, cross-check receipts, presupuesto, digests; **no SQLite**, no grants, no active.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStorePortableBackupCrypto.kt` **EXISTENTE**: añadir SOLO métodos internos `sealWithHouseholdPurchases12` / `openWithHouseholdPurchases12` delegando a los mismos `sealPayload/openPayload` ya certificados; no tocar clave, header, algoritmo, API v8, KDF ni seguridad.
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household12Fbs2CodecTest.kt` **NUEVO**: pruebas de unidad y sello/apertura.
4. `.github/workflows/faro-v5-s4-w05-a1c0b1-purchase-fbs2v9.yml` **NUEVO**: JDK21, Android SDK 37.0 del repositorio, preflight READY en padre para cuatro rutas, ejecutar `:store:generation:test --tests ar.com.faro.v5.generation.FaroV5Household12Fbs2CodecTest` y regresiones v8/V11 específicas; XML originales sin fallos/errors/skips; artefacto Actions.
Ningún otro cambio: si se necesita `v5/store/reality`, app, test fixture adicional o gradle, PARAR Y REAUDITAR, no ampliar permisos tácitamente. Commit funcional debe incluir línea `FARO-UNIT:S4_W05_A1C0B1_PURCHASE_FBS2V9_AUTHENTICATED_CODEC_DEV`.

## B5 — Auditoría adversarial y objetivo de CI
T01 v9 Evidence2 con compra y dos recibos parciales 7+3 de 10 -> encode/decode/canonical y FAROBAK2 round-trip; ninguna escritura.
T02 v9 sin compras + recibos legacy sin compra vinculada -> preservados; sin inferencia de compras.
T03 codec v8 histórico intacto, v8 NO se decodifica como v9 y viceversa, v6/v7 internos preservados.
T04 compra con referencia duplicada, no ordenada, UTF8 inválido/no canónico, revision=0, cantidad cero/overflow, fecha errónea, provenance no manual -> rechazada.
T05 recibos ligados a compra con item/unit distintos o total recibido mayor comprado -> rechazar, sin rechazar recibos legacy sin compra conocida.
T06 longitudes falseadas/truncadas, bytes extra, digest alterado, versión desconocida, padding, presupuesto de filas/bytes 16 MiB -> rechazar antes de reservar memoria excesiva.
T07 passphrase errónea, tag GCM adulterado, salt/nonce/header modificado -> sin fallback a v8, siempre rechazo.
T08 datos de purchase trusted snapshot no equivalen a grant al decode; no mutación, no permiso.
T09 reabrir exactamente mismos bytes de v9 y recodificar sin mutaciones -> igualdad semántica y canónica.
T10 regresión de encrypt/decrypt v8 con su codec y su crypto intactos -> PASS dirigida y XML propio, no alterar pruebas antiguas.
Meta **10 tests nuevos, además de suites v8 existentes cuyo conteo se comprueba en CI**. Work puede subdividir caso por cobertura, pero debe verificar XML exacto y guard del SHA. Todos los tests de autenticidad requieren archive FAROBAK2 real, no mocks que sustituyan GCM. Negativos no crean stock ni compra ACTIVE.
- B7 independiente puede certificar **solo codec/envelope DEV** y dejar A1C0B2 SQL capture/restore y A1C0B3 grant/manual ingreso PENDING.
- Criterio para READY: documento presente SHA blob exacto, cinco marcas B1–B5, guard documental SUCCESS, last_code_review A1C0B0 PASS, scope cuatro rutas exactas, writer CAS RELEASED al acabar, producción OFF.