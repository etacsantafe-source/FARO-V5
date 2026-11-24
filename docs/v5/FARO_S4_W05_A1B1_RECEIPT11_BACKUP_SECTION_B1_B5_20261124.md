# FARO V5 · W05-A1B1 · Sección tipada de respaldo de recepciones Reality11 (B1–B5)

Fecha: 2026-11-24. Unidad `S4_W05_A1B1_RECEIPT11_BACKUP_SECTION_DEV`. HEAD de revisión `92c8ab2f71a1f30a4fc18d8afaf24d7cf1947ebf`. Rama `work/s3-night-20261102`.
**Alcance exacto**: codec binario canónico e independiente para transportar **solo el estado y eventos de recepción física** de Reality11 en memoria/byte arrays, sin conectar aún a FBS2, capture/restore, generación, SQL11 activo, Apply, inventario ni UI. No reemplazar ni alterar FBS2v6/v7 o Household10 existente.

## B1 — Evidencia y necesidad

- W05-A1A schema11 B7 PASS (CI `45389909485`, 10/10), tablas `household_stock_receipt_state` y `household_stock_receipt` con `receipt_id`, `intent_key`, `purchase_ref`, `stock_item_ref`, `quantity_thousandths`, `unit`, `received_epoch_day`, `recorded_at_ms`, `revision`.
- W05-A1B0 generación sellada B7 PASS (código `f5a34e1d1aebd070cf1ccba14d281bad77c3f392`, CI `45415512280`, 8/8, guard `45416513785` SUCCESS), checkpoint actual `92c8ab2f71a1f30a4fc18d8afaf24d7cf1947ebf`. Escritor RELEASED época 1804; permiso BLOCKED.
- `FaroV5Household10BackupSectionCodec.kt` usa cabecera tipada, orden canónico, UTF-8 estricto, límites por columna/fila y tamaño total. `FaroV5Household10Fbs2Codec.kt` usa FBS2v6/v7; ninguno representa receipt rows v11. Una copia de inventario que omita recibos sería pérdida de datos cuando haya recibos reales; por ahora tablas vacías.
- Contrato: un pago o una necesidad no es recepción; 7 de 10 confirmado, 3 pendiente y no se genera stock al restaurar la necesidad.

## B2 — V4.4 y reuso

- V4.4 SmartCompra L11–15 distingue compra, recepción, consumo y sobras; reusar semántica sin copiar runtime. Persistencia REAL de productos debe depender de usuario/Apply futuro, no del backup.
- Reusar patrón de sección semántica `Household10BackupSection` con cabecera propia y tipos sin agregar `owner:stock` ni permitir un segundo writer. No modificar clase o bytes del codec Household10; un FBS2v6/v7 no debe decodificar nuestra nueva sección por accidente.

## B3 — Investigación de alternativas

- Java `DataInputStream`/`DataOutputStream` documentación `https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/DataInputStream.html` y `https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/DataOutputStream.html`: serialización determinista de primitives Big Endian, lectura cerrada y EOF explícito. Preferir `byte UTF-8 length + bytes` propio frente a `writeUTF` (modified UTF-8).
- SQLite `https://www.sqlite.org/stricttables.html` y `https://www.sqlite.org/lang_createtable.html`: restricciones SQL11 sobre fechas, IDs y cantidades requieren volver a comprobarse en la frontera de backup, para que el archivo inválido falle antes del restore.
- Alternativa A elegida: sección tipada propia v11 de filas de recepción, máximo 8192 rows/4MiB, orden por receipt_id y unicidad de receipt_id e intent_key, revisión de fila ≤ revisión global. Alternativa B descartada: reutilizar payload Household10 sin receipts (pérdida). Alternativa C descartada: serializar tablas completas como SQL arbitrario o JSON sin límites (riesgo de tamaño, SQL y no-canonicidad). Ninguna de estas alternativas requiere API de terceros.

## B4 — Esquema binario acotado y rutas exactas

**3 rutas nuevas**:
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11ReceiptBackupSectionCodec.kt`: `Household11ReceiptBackupRow` (strings referenciales con 128 caracteres y máximo 384 bytes UTF-8 cada uno, cantidad Int positiva, unidad de seis valores, fecha epoch_day civil 0001..9999, recorded_at_ms≥0, revisión>0); `Household11ReceiptBackupSection(revision≥0, receipts sorted by receiptId)`; MAGIC distinct `H11R`, FORMAT 1, SCHEMA11, max 8192/4MiB. No cifrado propio: la futura envoltura FBS2 autenticada hará su función. Canonical decode rechaza UTF-8 inválido, count/trailing bytes, duplicados de recibo o intent, filas fuera de revisión.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11ReceiptBackupSectionCodecTest.kt`: 12 tests dirigidos de roundtrip, límites, tamper, orden/duplicados y cero pérdida semántica.
3. `.github/workflows/faro-v5-s4-w05-a1b1-receipt-backup-section.yml`: preflight parent exact3, Gradle dirigido, XML original exact12 y artefacto.

La sección de stock no reutiliza las necesidades como recibos. El codec **no calcula saldo** ni valida la cantidad pactada de la compra; requiere reglas futuras A1C contra hechos comerciales. No habilitar FBS2 de v11 hasta pasar A1B2 con examen del manifest/cifrado/capture/restore, y A1B3 cutover.

## B5 — Auditoría adversarial y pruebas previstas (NOT_RUN hasta B6)

1. Sección vacía revision=0 roundtrip byte-exacto.
2. Una recepción completa con todos los campos y límites fecha/revisión.
3. Varias filas ordenadas comparten compra y artículo sin deduplicarlas; bytes deterministas.
4. Receipts fuera de orden se rechazan (no sort silencioso).
5. Duplicado de receipt_id rechazado.
6. Duplicado de intent_key rechazado aunque receipt IDs distintos.
7. Revisión fila negativa, cero o mayor que global rechazada.
8. Cantidad cero/negativa y unidad desconocida rechazadas.
9. IDs vacíos o sobre límites, hora negativa y fecha fuera de rango rechazadas.
10. Cabecera/magic/versión/schema alterados rechazados.
11. Truncamientos a mitad y bytes sobrantes rechazados.
12. Count forjado, longitud UTF-8 hostil, bytes UTF-8 inválidos y payload mayor 4MiB rechazados antes de asignar en forma no acotada.

STOP si la sección permite colisiones de intención, deja un payload sin limite, acepta UTF-8 reemplazado o modifica codecs previos. **Límites negativos posteriores**: generación/archives/restore/Undo/Apply, crash recovery, apertura activa, idempotencia real (único constraint no basta), productos reales, G15. READY solo tras guardia propia; sin pagar ni activar V2 productiva, Auto/Noche o egress.
