# FARO V5 — S4 W04-E · Presentación nativa de revisión comercial histórica

Fecha 2026-11-22. Unidad S4_W04_E_NATIVE_HISTORICAL_REVIEW_DEV. Rama work/s3-night-20261102.

## B7 — PASS_SCOPED_DEV_ANDROID_NATIVE_RENDER_ONLY

Preauditoría B1–B5 docs/v5/FARO_S4_W04_E_NATIVE_REVIEW_B1_B5_20261122.md, blob 4c88de0112d9d84f88dde2235da713a12b34cb54, guardia documental 45036952299 SUCCESS. Permiso READY exact6 en 6a911f80183eb24a745279d0b6d8666a25bf8f6e, guardia 45037064780 SUCCESS. B6 funcional **bbcdb021ced4688ede8a41533d22deb90bf2e1ea** contiene exclusivamente las seis rutas del permiso: v5/app/build.gradle.kts, manifest y dos Kotlin en src/w04CommercialDev, test instrumentado en src/w04AndroidTest, workflow faro-v5-s4-w04-e-native-review.yml. No tocó D0–D4, SurfaceHost ni código W01–W03.

**Guardia de código** [45037284359](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45037284359) SUCCESS para SHA exacto. **Android emulador API35** [45037284360](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45037284360) SUCCESS (job 137253734938), Java21, SDK37, fuente 200%. Gradle BUILD SUCCESSFUL in 6m14s, marker W04_E_ANDROID_API35_FONT200_4_4_PASS; marcador W04_E_ISOLATED_OFFLINE_MANIFEST_PASS.

Se descargó independientemente el artifact 14760840249, nombre w04-e-android35-xml-apk, con dos archivos: XML JUnit y APK. Comprobación ZIP SHA256 **2a3954c5ed8fcc91d379ae3bad8961be5fa5d3368a0b49efad66e75ac01239ab** (coincide con digest GitHub Actions). XML test AV(D) SHA256 **c9bedc648db3afee8068954e9c8354d75cc2fb7d8b154bf7662f0f2828eb29fe**. Suite tests=4 failures=0 errors=0 skipped=0; cuatro casos distintos:
- t01IsolatedOfflineLaunchStartsWithoutCommercialReview
- t02ReadOnlyHistoricalWhatIfAndUnverifiedBenefitsKeepCurrenciesSeparate
- t03PauseAndRejectedDecisionErasePreviouslyPresentedData
- t04Font200HeadingsAndLiteralUnclickableSource

APK app-w04CommercialDev.apk SHA256 **687b4def2b7823ea979d9136f99cc849a5c6025e6ac400ea5c0ae6d16bf5e37b**, verificado independientemente contra APK ZIP; package ar.com.faro.alpha.w04commercialdev y launcher único; permisos INTERNET, POST_NOTIFICATIONS, ACCESS_NETWORK_STATE, WAKE_LOCK, SCHEDULE_EXACT_ALARM y RECEIVE_BOOT_COMPLETED ausentes según aapt del workflow. Sin servicio, provider ni receiver en manifest de variante.

El componente Android ofrece headings accesibles, scroll, monto histórico por moneda/comercio, WHAT_IF separado, reintegro declarado sin verificar, fuente literal no clickeable, falta de precio visible, cero totales ARS+USD y sin Apply/pago. **El Activity real abre SIN ninguna revisión ni precios sintéticos**; fixtures existen exclusivamente en androidTest. En pausa se borra el display. No hay owner real comercial montado, ni host de read-grant activo, ni navegación SmartCompra productiva; W04-F necesita integración propia con lectores reales y aceptación. Este B7 **NO certifica W04-E completo con host vivo ni W04 global**, únicamente presentación nativa aislada DEV, sin físico Moto G15.

El workflow de E pasó por SHA; otras regresiones desencadenadas por build.gradle no se declaran completas ni se repiten si siguen en ejecución. W04-F, W05–W11, IA, V2 productiva, pagos bancarios, stock, Apply y pruebas G15 no autorizadas en este cierre.

READY consumido a BLOCKED en el checkpoint documental. Writer CAS RELEASED época 1565 después de publicación del B7 y HEAD exacto, guardia final pendiente de comprobación independiente posterior a este commit.