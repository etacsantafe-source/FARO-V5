# FARO V5 — W04-F2C1B1A · Android DEV read consent por dominio · B7 PASS

2026-11-23 Argentina. Unidad `S4_W04_F2C1B1A_ANDROID_LOCAL_SCOPED_READ_CONSENT_DEV`, rama `work/s3-night-20261102`. HEAD de código funcional `d0009049f35adf0fae0407ca5d035639f4740b87`.

## B7 — PASS_SCOPED_ANDROID35_DUAL_LOCAL_READ_CONSENT_5_5

### Protocolo, rutas exactas y alcance
- B1–B5 específicos `docs/v5/FARO_S4_W04_F2C1B1A_ANDROID_LOCAL_SCOPED_READ_CONSENT_B1_B5_20261123.md` blob `f37c930d2d2eaebf8374ef2df3d38c87caaf8929`, checkpoint `999d55a28da04d228101931e72876e1979b1bbe4`, guard `45174657978` **SUCCESS**.
- Permiso independiente READY exact4 checkpoint `7daf76b6c7ada85351f57090bef70c5080191dd1`, guard `45174881687` **SUCCESS**. El commit funcional `d0009049f35adf0fae0407ca5d035639f4740b87` contiene únicamente 4 rutas autorizadas y marcador exacto `FARO-UNIT:S4_W04_F2C1B1A_ANDROID_LOCAL_SCOPED_READ_CONSENT_DEV`.
- Código nuevo `FaroV5W04CommercialDevReadConsentIssuer.kt`, Activity DEV existente para lifecycle/attachment, tests instrumentados nuevos y workflow dirigido. No se alteran F2C0, F2C1A gate, F2C1B0 typed projection, los owners Store, `main` ni V4.4.

### CI real, XML y offline
- Guard obligatorio de código **`45175563106` SUCCESS** en `d0009049f35adf0fae0407ca5d035639f4740b87`.
- CI dirigida **`45175563251` SUCCESS**, job `137695777222`; build Android SDK37, emulador API35 Google APIs x86_64 para `w04CommercialDev`. Gradle **BUILD SUCCESSFUL**, comprobador `FARO_W04_F2C1B1A_ANDROID35_SCOPED_READ_CONSENT_XML_PASS_5_5` y `FARO_W04_F2C1B1A_OFFLINE_MANIFEST_PASS` presentes en log real.
- Archivo original de GitHub Actions `14820221677` (`w04-f2c1b1a-android35-xml-apk`) descargado e inspeccionado de manera independiente. ZIP SHA256 **`a118ba34059247b064da20a9b17582308bb11491dad89c2e9620c54c5f1f25d4`**, coincide con el digest remoto. JUnit XML SHA256 **`13d55cf2203b7216d1e9e04cf414f82c75124d30c1e0232aa7f8dc7b2654c3b9`**. APK SHA256 **`e20e025efac6bd8a6d062650ff60e71c59f0c2bda04f1c89f7d124733dd52480`**, también coincide con stdout CI. Package `ar.com.faro.alpha.w04commercialdev`, 1 actividad launchable, permisos de Internet, red/notificaciones/alarmas/boot/wakelock ausentes.
- **5/5 PASS XML real**: 5 tests, 0 failures, 0 errors, 0 skipped; clase exacta `ar.com.faro.v5.app.S4W04CommercialReadConsentIssuerAndroidTest`, casos distintos:
  1. `t01FreshLauncherHasNoIssuerConsentOrLiveSource`.
  2. `t02RealAndroidSandboxPrivateRootAndTwoIndependentVisibleTaps`.
  3. `t03PauseRevokesBothScopesAndResumeNeverRestores`.
  4. `t04PointerSwitchAndEpochAbaPermanentlyPoisonOldConsent`.
  5. `t05ForeignRootInvalidScopeThrowingOwnerAndDuplicateAttachFailClosed`.
- Regresiones de otros ámbitos activadas por este commit: S3 Home/Avisos **`45175563156` SUCCESS**, visor histórico comercial W04-E **`45175563048` SUCCESS**. F2C1A workflow `45175563164` SKIPPED por ID de unidad distinto — NO contarlo como test repetido ni fallo.

### Comportamiento efectivamente certificado
- En Android DEV la factory comprueba Activity aislada, Application respaldada por plataforma, UID/process sandbox mediante `AndroidPlatformSandboxFactsReader`, pointer ACTIVE exacto bajo root canónico en `filesDir`, host epoch, ownerReady y foreground.
- La clase solo administra consentimiento humano **independiente** para una necesidad y un producto seleccionados por host de confianza: requiere eventos de botones locales visibles, no `Intent`, modelo, startup ni `Boolean` remoto. Deja de ser válido ante cambio de fuente/puntero/epoch, denegación del host o salida a fondo; nonce anterior jamás renace.
- El lanzamiento Android es deliberadamente inerte: NO instala emisor, NO presenta selección, NO lee la base. El test instrumentado monta controles temporales exclusivamente como fixture de gesto, con puntero privado de prueba que NO tiene datos comerciales.
- **Es solo consentimiento local, no un grant N-033/N-034, token de ACL, identidad humana ni autorización de precio actual.** No abre el grafo F2C0, no genera propuestas de compra ni muestra observaciones reales.

### Límites pendientes y estado
- **F2C1B1B OPEN:** integrar selección real del owner Household10 + Evidence2 SAME-GENERATION, verificación independiente de las dos ACL reales y los dos consentimientos, obtención de F2C0 selected READ con lease, relecturas del F1 coordinator, opción humana de observaciones y D4 → pantalla E. Requerirá B1–B5/READY separado y pruebas reales; no afirmar F2C1B global cerrado.
- **F3 OPEN:** aceptación Android integral, acceso G15 según autorización y posterior decisión humana; S4 integral no certificada.
- V2 productiva OFF; sin Apply, pagos, inventario, red ni API paga; writer se liberará **RELEASED epoch1708** y el permiso F2C1B1A se consumirá **BLOCKED**. No repetir certificaciones previas.
