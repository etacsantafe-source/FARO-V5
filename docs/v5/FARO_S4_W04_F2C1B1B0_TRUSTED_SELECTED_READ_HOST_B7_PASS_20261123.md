# FARO V5 — F2C1B1B0 · trusted selected Android DEV host — B7 PASS
Fecha 2026-11-23. Rama `work/s3-night-20261102`. Unidad `S4_W04_F2C1B1B0_TRUSTED_SELECTED_READ_HOST_DEV`.

## B7 — PASS_SCOPED_ANDROID35_NEGATIVE_5_5

### Evidencia verificada
- B1–B5: `docs/v5/FARO_S4_W04_F2C1B1B0_TRUSTED_SELECTED_READ_HOST_B1_B5_20261123.md`, blob `94ae4bbc344667576956d580597df2e6bf1db13b`, checkpoint `dbe4076f7a3cf3ee93d2b3ff4461e246de1930dc`.
- Permiso READY exact4, checkpoint `70d66a7aae7cb5ac4c3c00d70f948c61bb342628`, B1–B5 verificados; código exclusivamente cuatro rutas autorizadas, commit `eae8d60008f0a9de4599a20706e18adbe9e60ac1` y marcador `FARO-UNIT:S4_W04_F2C1B1B0_TRUSTED_SELECTED_READ_HOST_DEV`.
- Guardia código `45181057574`: **SUCCESS**.
- CI Android API35 `45181057697`: **SUCCESS**, job `137714665115`; `BUILD SUCCESSFUL`; verificador de XML real `FARO_W04_F2C1B1B0_ANDROID35_TRUSTED_HOST_NEGATIVE_XML_PASS_5_5`; `FARO_W04_F2C1B1B0_OFFLINE_PACKAGE_PASS`. Cinco casos distintos, 0 failure/error/skip. Evidencia GitHub artifact `14822830147`, ZIP digest remoto `sha256:baf16d4fac2ac44745953d34f42e393d81d5aef9c8dd20db8df861bd5632302f`.
- Regresiones: S3 Home/Avisos `45181057593` SUCCESS; W04-E visor histórico Android `45181057667` SUCCESS. F2C1B1A workflow SKIPPED por filtro de unidad: no se presenta como prueba repetida.
- Casos Android negativos `S4W04CommercialTrustedSelectedHostAndroidTest`: launcher inerte; dos gestos sin N033/N034; un consentimiento sin segundo dominio; catálogo inválido/policy throwing; root/puntero/epoch y staged sin sello. No se inventó lectura positiva.
- Writer liberar `RELEASED` epoch 1716; permiso consumido `BLOCKED` sin rutas.

### Alcance de la certificación
Se implementó composición DEV para leer un `F2C0` selected ACTIVE desde una raíz/puntero/sesión autorizada con doble consentimiento local, dos callbacks independientes de owner READ, catálogo N-033/N-034, y gate Android foreground. Un error cierra la sesión. Los tests **solo** certifican rechazos fail-closed; callbacks de prueba NO equivalen a grants reales de los owners. No hay montaje automático al launcher ni datos mostrados en D4/E.

**Pendiente F2C1B1B1:** ruta positiva con generación Household10/Evidence2 auténtica, políticas de owners reales, selección explícita de observación comercial, revalidación F1/D4 y presentación E; requiere B1–B5/READY propio. **F3 y S4 integral OPEN**. V2 productiva OFF, sin Apply, pagos, stock, red, G15, main ni V4.4.
