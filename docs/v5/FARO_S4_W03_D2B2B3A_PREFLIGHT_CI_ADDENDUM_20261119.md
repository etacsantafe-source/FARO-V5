# W03-D2B2B3A — Ajuste de alcance del guard de CI

Base f1e100709d441008558bf62e1feb803b0bdcf86e, permiso inicial de cuatro rutas. El archivo funcional AuxiliaryDeliveryStore.kt también dispara el workflow dirigido de la unidad S4_W03_D2B2B2_AUX4_STAGED_BACKUP_DEV. Ese workflow ejecuta preflight de su propio ID para cualquier cambio del archivo compartido.

## B4
Agregar solo .github/workflows/faro-v5-s4-w03-d2b2b2-auxiliary-backup.yml al scope. Restringir su job a commits con FARO-UNIT:S4_W03_D2B2B2_AUX4_STAGED_BACKUP_DEV, siguiendo D2A/D2B1/D2B2A ya observados SKIPPED al correr otra unidad. La CI dirigida de ACTIVE4 y la guardia obligatoria se ejecutan por separado. Sin cambio funcional extra fuera del preflight.

## B5
Corroborar que el job backup anterior quede SKIPPED en el commit de preflight ACTIVE4 y que la guardia global y CI nueva evalúen el SHA. No repetir certificación previa. Bloquear permiso hasta guardia de este documento y nuevo READY exacto de cinco rutas.
