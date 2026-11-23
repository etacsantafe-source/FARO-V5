# FARO V5 — S4 W04-F2C1A · Android DEV foreground session gate B7 PASS

2026-11-23 Argentina. Unidad `S4_W04_F2C1A_ANDROID_FOREGROUND_SESSION_GATE_DEV`. Rama `work/s3-night-20261102`. Código exacto `4e40dd64fd794ee23b79fc10ca5ebd4da206101b`.

## B7 — PASS_SCOPED_DEV_ANDROID_FOREGROUND_SESSION_4_4

* Preauditoría B1–B5: `docs/v5/FARO_S4_W04_F2C1A_ANDROID_FOREGROUND_SESSION_B1_B5_20261123.md`, blob `c682b4fc2b1b60c8a3fd0bdf731b7f58412bb124`, guardia `45116289335` SUCCESS. Permiso READY exact 4 rutas independiente, en checkpoint `f108ff6a2ee9a65626626d38aff8206712e6c475`; guardia `45116480149` SUCCESS.
* B6 cambió exclusivamente cuatro rutas del permiso: `v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialDevForegroundSessionGate.kt`; `v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialDevActivity.kt`; `v5/app/src/w04AndroidTest/kotlin/ar/com/faro/v5/app/S4W04CommercialForegroundSessionAndroidTest.kt`; `.github/workflows/faro-v5-s4-w04-f2c1a-foreground-session.yml`. Commit contiene `FARO-UNIT:S4_W04_F2C1A_ANDROID_FOREGROUND_SESSION_GATE_DEV`. Sin cambios a F2C0, renderer W04-E, D0–D4, manifest, otros owners ni shipping.
* Guardia obligatoria del código `45116709264` **SUCCESS** en SHA exacto. GitHub Actions dirigida API35 `45116709185`, job `137501093405`, **SUCCESS**; JDK21/SDK37, emulador Android. El verificador original del job imprimió `FARO_W04_F2C1A_ANDROID35_FOREGROUND_XML_PASS_4_4` y `FARO_W04_F2C1A_OFFLINE_PACKAGE_PASS`.
* Artefacto **original** `14792052999`, nombre `w04-f2c1a-android35-xml-apk`, descargado y analizado independientemente. SHA-256 ZIP `d9dcd433769b4ea6780f79e44acd2497994d0d2bc61f8ce04e6f2c214744e187` coincide con digest de GitHub Actions y log de upload. SHA-256 XML `6b24b577c83821936cde445f379c183b7fff62e33a845b71f7c9addaff89cd37`. XML: **4 tests, 0 failures, 0 errors, 0 skipped**, cuatro casos originales únicos:
  1. `t01DefaultLauncherHasNoTrustedSessionOrInjectedCommercialData`
  2. `t02ExplicitChoiceAndIndependentGrantsAreRequiredForEveryRead`
  3. `t03PauseClosesSessionAndResumeCannotReopenOrRestoreReview`
  4. `t04EpochAbaDuplicateAttachAndThrowingCallbackFailClosed`
* APK `app-w04CommercialDev.apk` SHA-256 `7adad856277b81b9f08070d078d8cafb29b1f1918c458df1bf730c736d7b22a4`, verificado como archivo dentro del ZIP, coincide con hash original del job. Package DEV aislado `ar.com.faro.alpha.w04commercialdev`, un launcher y no permisos INTERNET, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, ACCESS_NETWORK_STATE, WAKE_LOCK (aapt en job). No hardware G15.
* Regresión original **W04-E** de mismo código: `45116709151` **SUCCESS**, log `W04_E_ANDROID_API35_FONT200_4_4_PASS`, offline manifest PASS. Regresión S3 Home/Avisos `45116709170` SUCCESS. Ningún fallo comprobado en estas acciones; no repetir la certificación previa de W04-E.

## Alcance exacto y continuidad

* Gate de sesión en Activity DEV revoca en pause/stop/destroy; resume no restaura sesión. Epoch/foreground/host/ambos grants y elección explícita se validan al inicio y al final; excepción/denegación cierra una sola vez y devuelve null. Sin datos ni premios inventados, sin lectura de otra generación, sin Apply, escritura, precio vigente o compra.
* **F2C1A PASS no equivale a F2C1 global**. La app de lanzamiento permanece vacía; F2C1B deberá construir **emisor Android DEV real y confiable** (no tratar UID/debug/Intent como grant), abrir raíz privada y la fuente tipada F2C0 bajo mismo lease/generación, traducir datos a F1/D4 con selección humana de observación y montar vista W04-E; deberá tener B1–B5 y READY propios.
* F3 aceptación Android extremo a extremo, G15 físico y S4 integral quedan **OPEN**. V2 productiva/Auto/Noche, pagos, transferencias, Apply y red NO activados. Permiso F2C1A consumido BLOCKED; writer liberación y guardia del checkpoint documental deben verificarse fuera de este documento.
