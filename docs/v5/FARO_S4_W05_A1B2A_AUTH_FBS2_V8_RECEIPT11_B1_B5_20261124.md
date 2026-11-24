# FARO V5 · W05-A1B2A · FBS2v8 autenticado Reality11 · B1–B5

Fecha 2026-11-24, rama `work/s3-night-20261102`, unidad `S4_W05_A1B2A_AUTH_FBS2_V8_RECEIPT11_DEV`.
HEAD revisado `e6f41e50c8c88dee1b04f69033d3ae7bed3a1c87`. DEV aislado, no apertura productiva.

## B1 — Evidencia de partida y aceptación

- Checkpoint exacto e6f41e50; A0 y A1A B7 PASS; A1B0 B7 8/8; A1B1 sección recibos Reality11 B7 12/12 en run 45417574919, código 23621ac756057fe865439c97dd9cac06a37ccdb7. No se repiten.
- `FaroV5Household11ReceiptBackupSectionCodec.kt` blob `4628d59935ce81c4e1e0bccb5a5a1e7df2d9cf9b` define H11R schema11, revisión/cantidad/IDs validados, 8192 eventos y 4MiB.
- `FaroV5Household10Fbs2Codec.kt` blob `9faa8245467bc79e1b36c7ba9e1b6be42251d31a` conserva perfiles FBS2v6 (Evidence1) y v7 (Evidence2) con Household10 y secciones SHA-256; ninguno transporta H11R.
- `FaroV5FiveStorePortableBackupCrypto.kt` blob `4485cca4b089783f50b3423a891212a6fd8fb4ee` define FAROBAK2 AES-256-GCM, PBKDF2-HMAC-SHA256, AAD del encabezado, salt y nonce aleatorios; expone seal/open separados para cada versión. En apertura, autenticación precede al decodificador.
- Permiso anterior `BLOCKED` consumido tras B7; nuevo permiso requerido. Writer CAS adquirido para documentación; liberar tras checkpoint. Ninguna captura/restore SQL11 ni cutover en esta unidad.
- Aceptación binaria: FBS2 **versión nueva 8** con sección obligatoria H11R, snapshot tipado base Household10 v6 **o** v7, sellado/abierto solo por nuevo endpoint FAROBAK2; lectores anteriores rechazan v8; alteraciones fallan cerrado y se preservan registros/revisiones sin fabricar stock.

## B2 — Comparación V4.4, reuso y delta

- La revisión V4.4 SmartCompra recogida en la preauditoría A1B1 separa necesidades, compras, recepción, consumo y sobras. Se reutiliza la separación semántica; V4.4 no aporta el contenedor FBS2 versionado de V5, por lo que **no se trasplanta runtime**.
- Reutilizar directamente los codecs A1B1 H11R, Household10 FBS2v6/v7 y la caja FAROBAK2 existente. NO cambiar los bytes ni los decodificadores históricos FBS2v5/v6/v7.
- Delta mínimo: un nuevo FBS2v8 tipado con dos tramos anidados (FBS2v6/v7 certificado + H11R), ambos con digest y longitudes acotadas, y dos métodos crypto exclusivos v8. El envoltorio no inventa propietario, generación, receipt ni saldo y no añade protocolos OS/Internet.

## B3 — Investigación técnica independiente y elección

Fuentes primarias comprobadas 2026-11-24:
- Oracle JDK21 JCA `https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html`: AEAD valida AAD y ciphertext, facilita reusar el `sealPayload/openPayload` existente sin cifrado casero.
- Oracle JDK21 Cipher `https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html`: `AES/GCM/NoPadding`, tag autenticado y errores `AEADBadTagException`.
- NIST SP800-38D `https://csrc.nist.gov/pubs/sp/800/38/d/final`: unicidad crítica de IV/nonce GCM; mantener `SecureRandom` por mensaje y no usar nonce de revisión o generación.
- OWASP Cryptographic Storage Cheat Sheet `https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html`: preferencia por cifrado autenticado; reutilización JCA y borrado de secretos temporales, sin dependencias/API pagas.

Alternativa A **ADAPTAR**: FBS2v8 nuevo de segmentos FBS2v6/v7 completo + H11R con validación, digest por tramo y cifrado envolvente FAROBAK2. Ventajas: no toca formatos ni validadores previos; desventaja medida: cabeceras anidadas, no duplicar filas completas fuera del tramo.
Alternativa B **DESCARTAR**: insertar H11R dentro de FBS2v6 o v7 rompiendo archivo histórico e interoperabilidad.
Alternativa C **DESCARTAR**: nueva biblioteca AES/JSON/ZIP o cifrado propio: aumenta riesgo, dependencia y superficie, sin ventaja en DEV local. Digest SHA-256 interno no sustituye autenticación de GCM exterior.

## B4 — Contrato congelado y rutas exactas

Unidad `S4_W05_A1B2A_AUTH_FBS2_V8_RECEIPT11_DEV`, **cuatro rutas exclusivas**:

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11Fbs2Codec.kt` — `FaroV5Household11Fbs2Snapshot(base: FaroV5Household10Fbs2Snapshot, receipts: Household11ReceiptBackupSection)`, nuevo encode/decode version=8, magic FBS2, 2 longitudes y 2 digest SHA-256, límites 4MiB H11R y máximo plaintext existente. El tramo base almacena FBS2v6/v7 *anidado*, no reinterpreta su schema. Decoder comprueba magic/version, longitudes exactas, digest constant-time y payload H11R canónico antes de devolver snapshot.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStorePortableBackupCrypto.kt` — solo dos overloads internos `sealWithHouseholdReceipts11` y `openWithHouseholdReceipts11` que llaman las primitivas privadas FAROBAK2 existentes. Ningún cambio en APIs anteriores.
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11Fbs2CodecTest.kt` — diez pruebas JUnit5 dirigidas, mezcla de perfiles v6/v7 y negativas.
4. `.github/workflows/faro-v5-s4-w05-a1b2a-fbs2-v8-receipt11.yml` — preflight de padre READY 4 rutas, Gradle test dirigido, validación del **XML original** 10/10 y preservación del artefacto.

La versión 8 es **solo el perfil de archivo**, no esquema SQL ni versión del envelope FAROBAK2 (sigue v2). No se permite decodificar v8 mediante readers v6/v7 ni abrir archivos heredados por el nuevo método.

## B5 — Auditoría previa adversarial (expectativas, no PASS de ejecución)

1. FBS2v8 roundtrip con recibos vacíos explícitos, identidad Household10 y mismos bytes al re-encode.
2. Recibos Reality11 ordenados en compras parciales (7 + 3) preservan IDs/intents/quantity/revision; jamás crean unidades desde necesidades.
3. Base nested v6 Evidence1 y v7 Evidence2 aceptadas sin cambiar sus bytes, ambas con H11R obligatorio.
4. Aislamiento cruzado v5/v6/v7/v8: cada reader rechaza versión ajena; ninguna inferencia de schema11 desde v6/v7.
5. Tamper de segmento base y segmento H11R rechazado por SHA-256; no aceptar truncamientos, bytes extra, versión/magic falso, longitudes cero/negativas/enormes.
6. Passphrase equivocada, tag GCM adulterado y cabecera envelope alterada retornan rechazo tipado, sin revelar contenido ni tocar almacenamiento.
7. H11R con receiptId duplicado, intentKey duplicado, cantidad/revisión imposible falla antes de sellar.
8. La suma de tamaños usa `Long` antes de asignar y límite existente del plaintext; no se aceptan asignaciones arbitrarias hostiles.
9. Decodificar o abrir archivo es *solo lectura*: no activa generación, ni SQL11, ni stock, pagos, Apply, Undo, red o UI.
10. Verificar el parent READY, HEAD y scope de cuatro rutas con guardia, CI SHA exacto, XML y B7 independiente. Ante falla, RED_FIX acotado, sin inventar PASS.

**STOP de alcance:** A1B2B captura/restauración SQL11, A1B3 cutover/reopen, A1C Apply real, datos de stock efectivos y G15 físico son futuros. V2 productiva, pagos y Auto/Noche OFF; sin APIs pagas. No activar stock hasta certificar restauración y recuperación.
