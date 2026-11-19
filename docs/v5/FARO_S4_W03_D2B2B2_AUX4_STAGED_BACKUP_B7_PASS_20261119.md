# FARO V5 — W03-D2B2B2 · B7 backup Auxiliary4 STAGED

Unidad S4_W03_D2B2B2_AUX4_STAGED_BACKUP_DEV. Código 0b1efa6b958276b7137287faaa98b3ce3c1b09d3. Scope: owner Auxiliary4 aislado. W03 y D2B2B siguen OPEN hasta archivo autenticado, cutover e integración.

## B1–B5
docs/v5/FARO_S4_W03_D2B2B2_AUX4_STAGED_BACKUP_B1_B5_20261119.md, audit a378321a7995bf37769103b4359c6612b888edf1, guardia 44601921963 SUCCESS. READY 1caf0c2b5347015d6ee239f6f5957e759f2872c1, guardia 44602172208 SUCCESS, preflight exacto de siete rutas.

## B6
SQLite real: snapshot de Notices, Guardados y ledger bajo una lectura, restore durable bajo una sola transacción. Se valida revisión, FK, fingerprint e integridad y se descarta DeliveryIntent. Si falla el segundo recibo o cambia el pointer antes de COMMIT, rollback íntegro. Replay tras reapertura no repite efecto. Destino no fresco ajeno se preserva. Permite completar ledger vacío solamente sobre base idéntica sin DeliveryIntent. Los jobs W03-D2A/D2B1/D2B2A se limitan a commits de su unidad y quedaron SKIPPED. Guardia obligatoria sigue activa.

Run 44602951476 SUCCESS, job 135838951510. Verificado ZIP de artefacto 14570135556 SHA-256 4565126d7ea17f5294071d53f46fae3a88080d189ad62f81a86a99fcc60b23b5, XML 5/5, sin fallas, errores ni skips. Guardia de código 44602950902 SUCCESS. Regresión relevante de backup/restore Guardados 44602951197 SUCCESS.

## B7
PASS acotado: permite crear y restaurar el snapshot de Auxiliary4 STAGED. READY consumido. El checkpoint requiere guardia y writer RELEASED. El archivo autenticado completo sigue pendiente; se publica su preauditoría en docs/v5/FARO_S4_W03_D2B2B2_AUTHENTICATED_ARCHIVE_B1_B5_20261119.md. Próximos: READY de ese alcance, archivo autenticado, luego cutover/preflight ACTIVE4 y host, cierre D, E y G. V2 productiva OFF; W3-F diferido; G15 NOT_RUN.
