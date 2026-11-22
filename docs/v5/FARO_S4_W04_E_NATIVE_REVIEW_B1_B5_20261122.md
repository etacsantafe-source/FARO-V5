# FARO V5 — W04-E · Pantalla Android de revisión comercial histórica · B1–B5

Fecha 2026-11-22. Unidad: S4_W04_E_NATIVE_HISTORICAL_REVIEW_DEV. DOCUMENTAL NO READY. Baseline 0332c360fc7a56a20da5fcb6f69d2b408fe8e010. W04-D4 B7 PASS scoped JVM, código 5fe51ae3286e92bf8b7575c93e9e77dd0d6afe7f, CI 45034045914 8/8, guardia 45034045852, final 45035541300 SUCCESS. Writer RELEASED epoch1561. Permiso D4 BLOCKED correctamente consumido.

## B1 — Evidencia, brecha, aceptación

D4 S4W04CommercialReviewPresentation.kt (blob b51335ad892a7389ce0d1ccaf7ac7a1800fe0713) tiene Decision.Display/Rejected: grupos por moneda/comercio/localidad/medio, histórico observado, escenario hipotético, notas no verificadas, faltantes, fuente/asOf y checkedAt. W04-E no existe. FaroV5SurfaceHost.kt aún usa UnavailableDestination para SMART_COMPRA. No hay host foreground que genere D4 con grants Household y Evidence reales; W04-F queda encargado del montaje integral. E será **renderer Android nativo, DEV, aislado, read-only** con pantalla sin datos por defecto y entrada interna de Display tipado solo desde caller autorizado. No atribuir B7 global de SmartCompra a su prueba. No usar demo con precios fabricados.

## B2 — V4.4, reuso y delta

P02 L11/L14/L15: SmartCompra histórico presentaba presupuesto por comercio con contexto humano; lectura histórica no es cotización vigente. P05 S02, P06 E06/UX21: SmartCompra como sección, Cocina interna posterior, accesibilidad fuente 200%, TalkBack, scroll y no mostrar IDs como títulos. D4 carece de nombre humano verificado: mostrar artículo del carrito N, packs y fuente, no inventar nombres, no revelar ID técnico. Reusar D4 sin modificar D0-D4 ni V4.4, W01, W02, W03 o SurfaceHost. Nueva build type/UID DEV aislada, Android Views y test instrumentado; ningún runtime externo.

## B3 — Alternativas documentadas y amenazas

- Android Views accessibility: https://developer.android.com/guide/topics/ui/accessibility/views/apps-views : ADAPTAR TextView, headings, orden natural y descripción inequívoca.
- Android 14 nonlinear font 200%: https://developer.android.com/about/versions/14/features : ADAPTAR wrap_content y unidades sp, scroll; DESCARTAR alturas fijas.
- Android instrumented tests: https://developer.android.com/training/testing/instrumented-tests : USAR emulador API35 y AndroidJUnitRunner.
- MITRE CWE-367 https://cwe.mitre.org/data/definitions/367.html y OWASP transaction authorization https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html : lectura posterior no es garantía de precio ni autorización de compra.
- Patrones Grocy/KitchenOwl (P04) solo REFERENCIA; sin AGPL, backend, Internet ni servicios. Hipótesis falsables: confundir WHAT_IF con precio vivo, sumar monedas, sumar cashback futuro, ejecutar URL/HTML del sourceRef, conservar vista stale en pausa, leak de permisos por manifest merge, ilegibilidad 200%.

## B4 — Alcance y rutas exactas

Seis rutas autorizables **después** de guardia documental SUCCESS y permiso separado:
1. v5/app/build.gradle.kts
2. v5/app/src/w04CommercialDev/AndroidManifest.xml
3. v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialDevActivity.kt
4. v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialReviewPanel.kt
5. v5/app/src/w04AndroidTest/kotlin/ar/com/faro/v5/app/S4W04CommercialAndroidTest.kt
6. .github/workflows/faro-v5-s4-w04-e-native-review.yml

1) build.gradle: nuevo build type w04CommercialDev (applicationId ar.com.faro.alpha.w04commercialdev) y selector exclusivo de test Android; no cambios a variantes ya certificadas.
2) Manifest: Application DEV simple, un Activity launcher explícito, remover INTERNET/ACCESS_NETWORK_STATE/POST_NOTIFICATIONS/boot/servicios/providers/receivers y launchers de shipping.
3) Activity: ComponentActivity aislada, valida UID/debug/V2 productiva OFF, crea panel sin datos; onPause invalida toda lectura.
4) Panel: ScrollView de texto no clickeable. renderAuthorized toma únicamente Decision.Display tipado, sin Intent extras, serialización, HTTP ni permisos nuevos; Rejected/ausente limpia contenido. Cabecera «Revisión comercial histórica · solo lectura»; asOf/checkedAt/cobertura; faltantes/no modelados por cantidad, no ceros inventados; grupo por moneda/comercio/localidad/medio, histórico por grupo, WHAT_IF aparte, beneficios futuros no verificados, fuentes literales sin enlaces, artículo ordinal y packs. ARS/USD dos decimales de minor, otras monedas en minor sin escala adivinada. Sin total global, FX, Apply, Pagar, Compra, Finance ni stock.
5) androidTest: pruebas T01-T04 solo para representación, fixtures sintéticos dentro de test; el lanzamiento de la aplicación normal NO muestra datos de prueba.
6) workflow: Java21, SDK37, emulador API35 font_scale 2.0, 4 tests únicos y XML artifact + APK; manifest y package exactos. No activar G15 físico. No afirmar host real.
El código funcional requiere FARO-UNIT:S4_W04_E_NATIVE_HISTORICAL_REVIEW_DEV y preflight exacto de seis rutas contra HEAD padre READY.

## B5 — Adversariales y gate

T01: lanzamiento limpio muestra «Sin revisión comercial autorizada»; package propio, sin botones de pago, fuentes falsificadas o precio inventado.
T02: fixture solo UI test con ARS/USD y parcial, WHAT_IF/cashback separados, sin total/FX ni financiación real.
T03: onPause borra detalle y no restaura al volver; Rejected borra incluso una vista previa.
T04: font 200%/scroll/heading accesibles; fuente URL/HTML texto inerte, sin links/botones de checkout.
Exigir CI Android API35 exact SHA, cuatro testcases distintos XML 4/4, cero failures/errors/skipped, APK y manifest sin permisos prohibidos, guardia seven-block-evidence SUCCESS. Si falla, registrar RED_FIX antes de corrección. W04-F debe unir la vista con lectura real y pruebas de host. W04-E podrá cerrar solo alcance renderer Android aislado; jamás aceptación global.
**Gate:** PREAUDIT_DOC_ONLY, NO READY/B6 hasta guardia documental en verde; emitir READY exact6 en segundo commit documental. V2 productiva, Apply, pagos, stock, G15, Internet, Auto/Noche OFF.