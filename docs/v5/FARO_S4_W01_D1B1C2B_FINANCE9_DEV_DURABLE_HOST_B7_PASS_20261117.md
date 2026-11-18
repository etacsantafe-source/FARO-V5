# FARO W01 D1B1C2B — B7 del host duradero y lease DEV

Unidad `S4_W01_D1B1C2B_FINANCE9_DEV_DURABLE_HOST`. Código `29cbcf8458bf49f4bf4eba56093b54cdd2e0e23e` EXACT5, READY revisado con guard 44228985596 SUCCESS.

## B6
La raíz DEV combina exclusión JVM y lock de archivo. Conserva la decisión de mantenimiento, origen completo, target, journal y fingerprint mediante archivo privado verificable. Un PREPARE sin autorización de switch conserva la generación original. Una autorización durable exacta permite recuperar el mismo intento. Se actualizó exclusivamente al verificador del checkout el preflight del workflow de regresión de C2A.

## B7
**PASS SCOPED** en JVM. [CI dirigida 44229157510](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44229157510), [regresión 44229155940](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44229155940), [guard 44229155879](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44229155879) SUCCESS sobre mismo SHA. XML verificados independientemente de los jobs: 8/8 del host y 7/7 de regresión C2A. Artefactos 14394825811 y 14394730981; XML SHA-256 `a31610e7ae27c7da7145545e33a0b28ced622bab63263d836ed80fd33fd9fef6` y `2e84a6ff9974bf7a869cab7a35dccab8de890ab65420f19cb0021e0aa1330725`.
- Evidencia de un segundo proceso OS denegado mientras existe lease y admitido tras cierre. Confirmación negada/revocada, retorno a origen con reintento nuevo, recuperación después de CAS, source drift y checksum roto verificados.
- Este cierre certifica operación y exclusión de la raíz de fixture JVM. Falta entrega del único grafo a la sesión y montaje del arranque W01 Android. La regresión automática M2 44229155943 queda consignada por separado. W2 STOP, V2 productiva OFF.
