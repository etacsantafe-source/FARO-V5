# FARO V5 · W05-A1C2B · FBS2v10 autenticado para compensaciones

## B1 — Delta de evidencia

Reusa la preauditoría FARO_S4_W05_A1C2_STOCK_UNDO_RECOVERY_B1_B5_PREAUDIT_20261125.md. A1C2A tiene B7 PASS en 2b5eb70498013a238b91b8d620a9de66ec287ba5, CI 45609176004, guardia 45609176127 y XML 6/6. FBS2v9 certifica compras y recibos positivos e impone que la suma histórica no supere lo comprado. Con Undo y recepción posterior, lo físico neto puede ser correcto aunque el acumulado histórico sea mayor. El archivo nuevo debe conservar cada evento original y su compensación, incluido el caso de recepción posterior al Undo.

## B2 — Reutilización

Reusar codec FBS2v8 byte-exacto como sección base de los cinco owners y recibos. Reusar representación de la sección de compras ya certificada en v9, separando sus validaciones de la suma histórica específica de v9. Reusar FAROBAK2 AES-GCM, derivación de claves, autenticación de cabecera, límites y rechazo de múltiple lectura insegura. Citar P02/P03 y fuentes técnicas del codec de compras. Sin nueva dependencia, almacenamiento remoto o API.

## B3 — Delta de diseño

FBS2 versión 10 con tres secciones longitud/digest canónicos: v8 vigente, compras y compensaciones ordenadas. Esta versión aplica recibos y devoluciones en orden de revisión, con Long acotado, neto intermedio entre cero y cantidad comprada, y un Undo por recibo. Validar correspondencia con compra confirmada manual, principal, timestamp, cantidad íntegra, unidad, revisión de ledger, intent único y rechazo de compensar un recibo legacy sin compra. Los lectores v9 conservan su contrato y bytes. La apertura de v10 devuelve datos tipados autenticados. SQL13 restore/capture y cutover requieren sus propios permisos. El import explícito de un v9 autenticado a SQL13 crea cero compensaciones, sin generar autorizaciones de Undo.

## B4 — Rutas exactas

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household13Fbs2Codec.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12Fbs2Codec.kt`
3. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStorePortableBackupCrypto.kt`
4. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household13Fbs2CodecTest.kt`
5. `.github/workflows/faro-v5-s4-w05-a1c2b-compensation-fbs2v10.yml`

## B5 — Aceptación

Round trip auténtico de 10 compradas, 7 y 3 recibidas, Undo de 7, nueva recepción válida de 7, neto correcto. Rechazar exceso previo al Undo, falta de compra o receipt, referencias de otro principal, compensación repetida, unidad/cantidad incorrectas, orden o revisión incompatible, truncamiento, tamaños, bytes/digests erróneos y ciphertext o passphrase inválidos. Verificar regresión dirigida v9 y base v8. CI exacta, XML original y guardia por SHA.
