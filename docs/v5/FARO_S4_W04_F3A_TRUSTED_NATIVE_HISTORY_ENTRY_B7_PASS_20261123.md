# FARO V5 — W04 F3A · B7 PASS scoped Android DEV botón nativo de historial

2026-11-23 local / evidencia CI UTC 2026-11-24. Rama `work/s3-night-20261102`. Unidad `S4_W04_F3A_TRUSTED_NATIVE_HISTORY_ENTRY_DEV`. Código `9d2f63e046696f0df731c3c5f29acc25fe6d20df`. **PASS limitado a entrada explícita DEV**, no cierre de F3 integral ni activación productiva.

## B7 — Evidencia exacta de implementación y prueba
- B1–B5 previo documento `docs/v5/FARO_S4_W04_F3A_TRUSTED_NATIVE_HISTORY_ENTRY_B1_B5_20261123.md`, blob `da80123e1cbdc26d366f0711ee12350fd377bf9f`, guard preauditoría `45308425391` SUCCESS. Permiso READY exact4 `107ca3b4c51ea30626e4cfeed2b3dab42e305cb5` guard `45308578571` SUCCESS. Unidad F2C1B1B1B1B B7 3/3 ya cerrado, NO repetir t01–t06.
- B6 código `9d2f63e046696f0df731c3c5f29acc25fe6d20df` dentro de cuatro rutas autorizadas: `FaroV5W04CommercialDevTrustedSelectedHost.kt`, `FaroV5W04CommercialReviewPanel.kt`, `S4W04CommercialOwnerBackedPositiveAndroidTest.kt` (solo t07–t09) y workflow `faro-v5-s4-w04-f3a-native-entry-android.yml`. La actividad sigue inerte al arrancar. Después de sesión y dos consentimientos, el host confiable ofrece `Abrir revisión histórica`; solo el tap de apertura pide la lista, y otro tap elige observación. Ninguna decisión de precio es automática.
- Guardia del commit funcional `45308808174` **SUCCESS**, incluidos 14 tests sintéticos de protocolo y permiso scoped.
- CI Android 35 real `45308808271`, job `138159103171` en SHA `9d2f63e046696f0df731c3c5f29acc25fe6d20df`: **COMPLETED SUCCESS**, `BUILD SUCCESSFUL`. Exactly 3 new instrumented tests, original XML parseado en paso `Verify ONLY F3A new XML 3 of 3 and offline APK` SUCCESS, emitió marcador `FARO_W04_F3A_NATIVE_ENTRY_ANDROID35_XML_PASS_3_3`: clase única, nombres t07,t08,t09 exactos, sin failure/error/skipped.
  1. `t07NativeEntryButtonRequiresTapBeforeHistoricalChooser` PASS: botón de apertura separado de observación; selección explícita después, histórico ARS 240.00 con fuente REAL Household10/Evidence2 local DEV y ningún importe antes de taps.
  2. `t08ClosedOwnerHostCannotUseDetachedHistoricalEntryButton` PASS: botón detenido sin permiso después de host.close; no resucita vista.
  3. `t09InvalidLiveClockAfterNativeEntryTapCannotDisplayHistoricalEstimate` PASS: reloj anterior al asOf => F1 fail closed, sin importe en el panel.
- APK DEV aislada y offline: marcador `FARO_W04_F3A_ANDROID_OFFLINE_ISOLATED_APK_PASS`; paquete `ar.com.faro.alpha.w04commercialdev`, launcher único; sin INTERNET/ACCESS_NETWORK_STATE/POST_NOTIFICATIONS/SCHEDULE_EXACT_ALARM/RECEIVE_BOOT_COMPLETED/WAKE_LOCK. SHA256 APK según log `558f8670cc471e18a0d22626de4f37ad52f7be6b587ce8f56ce26ac9494f73d5`.
- Original ZIP artifact GitHub Actions `14883345925`, nombre `w04-f3a-android35-native-entry-xml-apk`, size `24,792,092` bytes, digest `sha256:e8bc6979e3c6ce4b6260ba36a365623d03b5a38759e4b6445fe758dc5afa7797`, idéntico al upload log. Se inspeccionó script CI y sus marcadores de XML: no se afirma una extracción ZIP fuera de CI.

## Alcance adversarial y próximos pasos
La UI no adquiere ACL de una bandera/Intent/modelo; cada tap vuelve al host owner-backed y al F1 live source con los W_full/W_selected ya certificados. Fecha pasada/negación o revocación no pintan importe; un tap instrumentado NO es autenticación humana. ARS y USD permanecen separados; historial no es cotización; sin pagos/Finance/Apply/stock ni red. G15 físico NO ejecutado, main/V4.4 intactos, V2 productiva, Auto/Noche OFF.

**Cierre:** F3A B6 DONE, B7 PASS_SCOPED_ANDROID_DEV y permiso F3A consumido/BLOCKED. W04 F3 integral global **OPEN**: requiere alcance independiente de aceptación combinada y sus gates, no extrapolar esta UI DEV a producción. Siguiente unidad F3B/integral B1–B5 propio, no repetir trabajos anteriores.
