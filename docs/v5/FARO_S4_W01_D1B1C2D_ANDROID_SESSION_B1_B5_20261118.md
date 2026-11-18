# D1B1C2D — sesión Finance9 DEV Android

## B1
HEAD c6d381029ddf917c4f07e4008ef9c2816efed3f1, guard 44286839666 SUCCESS y aprobación humana de ADB solo en emulador de CI. C2C 5/5 y su regresión 8/8 conservadas. Authority admite solo Workspace7 y Planning8; launcher ya presta su Session al lector W01.

## B2
Conservar AtomicApply, receipts y recuperación V4.4. Reusar Session, préstamo W01, gate y Finance9DevCutoverRuntime. Planning8 del root privado como origen. Sin segundo grafo ni nuevas políticas de lectura/escritura.

## B3
ADAPTAR https://developer.android.com/guide/components/activities/activity-lifecycle para comprobar RESUMED en gesto y cerrar Session al destruir. REFERENCIA https://github.com/android/nowinandroid (Apache-2.0), lifecycle separado de fuente canónica; DESCARTAR segunda Room DB por pantalla o lease del lector. REUTILIZAR https://github.com/ReactiveCircus/android-emulator-runner (Apache-2.0) en Ubuntu API35, sin dependencia de aplicación ni API paga.

## B4
Opt-in W01 por paquete más launcher. Lease para todas las aperturas W01, desde bootstrap a cierre. Recovery Finance9 precede Planning8 para W01; rollback usa journal y fence previos más selección exacta del runtime. Gesto dedicado y validación foreground antes de autorizar. El lector conserva préstamo y certeza del snapshot. Rutas exactas:
- v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt
- v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt
- v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2Planning8DevHost.kt
- v5/app/src/w01FinanceDev/kotlin/ar/com/faro/v5/app/FaroV5W01FinanceDevActivities.kt
- v5/app/src/w01AndroidTest/kotlin/ar/com/faro/v5/app/S4W01Finance9SessionAndroidTest.kt
- .github/workflows/faro-v5-s4-w01-d1b1c2d-finance9-session.yml
- .github/workflows/faro-v5-s4-w01-d1b0-foreground-owner.yml
- .github/workflows/faro-v5-s4-w01-d0-isolated-launch-allowlist.yml
- .github/workflows/faro-v5-s4-w01-d1b1b-single-session-ui.yml

## B5
Comprobar foreground, segundo lease, activación de origen real, reapertura y préstamo sólo lectura. Host ajeno conserva entrada legacy. Registro Finance9 falla cerrado al perder identidad o lease. S3 Search, X03 Consult, A4 y D1B0 incluyen ADB de emulador autorizado. En D0, D1B0 y D1B1B reemplazar exclusivamente el preflight histórico por auditoría --mode ci --head HEAD para conservar pruebas sobre el SHA real. Sin cambios a manifiesto, producto, teléfono, W2 ni APIs.
