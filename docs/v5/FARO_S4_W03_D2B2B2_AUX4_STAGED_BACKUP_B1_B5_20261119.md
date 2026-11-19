# FARO V5 — W03-D2B2B2 · Captura y restore transaccional Auxiliary4 STAGED

Preauditoría de S4_W03_D2B2B2_AUX4_STAGED_BACKUP_DEV. Base exacta 318fb374bc5217a847cb9d28ed155f2415898bc8; guardia de cierre D2B2B1 44601673172 SUCCESS, writer RELEASED 1330.

## B1 — Evidencia
D2B2B1 entregó el formato FBS2v5 y verificó 5/5 XML. AuxiliaryDeliveryStore tiene locks, SQLite BEGIN IMMEDIATE, backup v3 y un único puerto quarantined de migración v3→v4. El puerto estándar backup/portability falla en v4 para evitar omisión de ledger. Falta exportar datos + ledger bajo una sola lectura y reponer ambos en SQLite bajo fence de generación. Aceptar solo rollback probado, reapertura exacta de backup y key replay sin revisión adicional. Este es el tramo de owner de D2B2B2; queda por conectar al coordinador autenticado de FBS2v5.

## B2 — Reutilización
V4.4 FARO@bfa0ee71f5b9cd104baaadd71f1e77a61f203939 FaroV4FollowUpSurface.kt separa el recordatorio durable del permiso Android. Usar solamente las filas Notice/Guardados/receipt de Auxiliary4 y el material gate, locks y verificador de salud de V5. Reusar inserción Notice y Guardados y política de no sobreescritura del puerto v3. Class D DeliveryIntent queda excluido del backup; conservar esa separación y restaurar su proyección local en la integración del host.

## B3 — Investigación
USAR transacción SQLite única para eliminar el intervalo entre Notice/Guardados y el último recibo. Reusar el owner, schema y motor ya presentes. SQLite https://www.sqlite.org/lang_transaction.html establece modificación atómica y BEGIN IMMEDIATE. https://www.sqlite.org/pragma.html distingue integrity_check y foreign_key_check; correr ambos. Dominio público https://www.sqlite.org/copyright.html. DESCARTAR API Backup física https://sqlite.org/backup.html como interfaz semántica porque copiaría además DeliveryIntent y no comprueba el conjunto. Sin dependencias ni permisos Android.

## B4 — Unificación y alcance
Tipo de snapshot Auxiliary4 explícito con proyección Auxiliary3 y ledger, incluido vacío. Mantener la ruta estándar v3 y reutilizar sus registros y transacción de restore. El nuevo puerto es exclusivo del owner Auxiliary4 STAGED y nunca cambia el puntero. Admite destino fresco, repetición exacta o complemento de ledger vacío sobre una proyección base exacta y sin DeliveryIntent, para reutilizar la futura restauración autenticada de cinco owners. En otro destino, bloquear. CI nueva exacta, tests SQLite en owner graph. Los tres workflows ya certificados W03 que escuchan AuxiliaryDeliveryStore tienen preflight hardcoded al ID anterior (errores 44591846604 y 44591846535); limitar sus jobs a commits de su propia unidad para no recertificar ni producir falsos rojos. Guardia global se mantiene.
- v5/store/auxiliary/src/main/kotlin/ar/com/faro/v5/store/auxiliary/AuxiliaryBackupPort.kt
- v5/store/auxiliary/src/main/kotlin/ar/com/faro/v5/store/auxiliary/AuxiliaryDeliveryStore.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Auxiliary4ReceiptBackupTest.kt
- .github/workflows/faro-v5-s4-w03-d2b2b2-auxiliary-backup.yml
- .github/workflows/faro-v5-s4-w03-d2a-reminder-notice.yml
- .github/workflows/faro-v5-s4-w03-d2b1-attention-cas.yml
- .github/workflows/faro-v5-s4-w03-d2b2a-stage4-receipts.yml

## B5 — Auditoría previa
Fijar fence antes y después de lectura; en restore revisar el fence antes de BEGIN y COMMIT. Verificar Notice/Guardados y corpus de recibos en un solo resultado, hash/relación/revisión y sin DeliveryIntent, más integridad y FK. Si falla la inserción de un recibo, ninguna fila del restore se confirma en Auxiliary. Probar stage reabierto, restore idempotente, llave reutilizada, backup legacy aún bloqueado, inyección SQL real de fallo, destino no fresco y fence revocado por cambio del puntero. No abrir ACTIVE4, ni migrar ACTIVE3, ni exportar datos reales. Para la siguiente integración comprobar además autenticación antes de materializar, pin del ciphertext original, base exacta y recuperación del journal en cada corte.
