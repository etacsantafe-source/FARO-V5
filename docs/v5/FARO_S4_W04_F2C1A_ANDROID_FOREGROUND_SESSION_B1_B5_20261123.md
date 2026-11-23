# FARO V5 · S4 W04-F2C1A · Sesión de lectura Android DEV revocable · B1–B5

Fecha: 2026-11-23 (Argentina). Unidad propuesta `S4_W04_F2C1A_ANDROID_FOREGROUND_SESSION_GATE_DEV`.
Baseline de revisión: `71493ae88741a88c20ad06ecb7acf46b3ba47e3e`. **PREAUDITORÍA DOCUMENTAL, NO READY**.
Ejecución exclusivamente manual, código aún NO modificado. F2C1B (montaje de identidad/grants y datos reales) y F3 (aceptación integral) permanecen abiertos.

## B1 — Evidencia y brecha binaria
- F2B2A/B/C tienen B7 PASS acotados; F2C0 con código `ff881ffc41e5a2ca719589a49536cb24792ec694`, guardia `45110499796` SUCCESS y CI `45110499993` SUCCESS verificó **6/6 XML SQLite**. Su `FaroV5Household10Evidence2DevSelectionReadPort` garantiza un solo ACTIVE Household10/Evidence2 bajo root lease, puntero+secuencia, callback de epoch/foreground y dos grants independientes; devuelve solo una necesidad y un producto elegidos; `close()` revoca definitivamente.
- W04-E Activity/Panel aislados `ar.com.faro.alpha.w04commercialdev` y manifest offline B7 PASS 4/4 Android API35, pero abre vacío y limpia en pausa. F1 compone D1–D4 solo desde callbacks confiables: no crea autoridad/grants ni datos. El permiso F2C0 se consumió a BLOCKED y el writer quedó RELEASED epoch1672 sobre este baseline.
- Brecha F2C1: **no existe sesión Android acotada al ciclo de vida** que maneje ese port y bloquee display tras pause/stop, sin reabrir en resume. Tampoco existe todavía un emisor real Android DEV de grants Household+Commercial ni un workflow end-to-end de F2C. **F2C1A solo cierra el gate/lifecycle, no F2C1 global ni montaje real**.

## B2 — Reutilización y comparación V4.4
- V4.4 SmartCompra es referencia de elección explícita y presentación humana, no una fuente autorizada ni pago.
- Reutilizar sin tocar: `FaroV5Household10Evidence2DevSelectionReadPort` (F2C0), `S4W04HistoricalAuthorizedReadCoordinator` (F1), `FaroV5W04CommercialReviewPanel` y manifest offline (E). No reabrir F2B1/B2C, C/D ni E tests.
- Añadir gate mínimo en el **mismo build type DEV**, aislado del shipping `FaroV5SurfaceHost` y `FaroV5CompositionRoot`, sin nueva base de datos, owner, segundo root lock, biometría o puertos Apply.

## B3 — Fuentes primarias y alternativas
- Android Activity lifecycle: https://developer.android.com/guide/components/activities/activity-lifecycle : adaptar revocación en `onPause`/`onStop`; `onDestroy` no garantiza limpieza tras kill.
- Android ActivityScenario: https://developer.android.com/reference/androidx/test/core/app/ActivityScenario.html : usar transiciones RESUMED→STARTED→RESUMED en instrumentación API35.
- Android UI state holders: https://developer.android.com/topic/architecture/ui-layer/stateholders : separar gate de estado de vista; la vista solo recibe Display ya validado.
- Android noBackupFilesDir: https://developer.android.com/reference/android/content/ContextWrapper : para futura raíz DEV privada en F2C1B, NO para abrir stores prematuramente en F2C1A.
- Opción A, Activity abre store y mantiene token durante pause: **DESCARTAR** (retención/stale). Opción B, `Intent` o fixture de test como autoridad: **DESCARTAR** (bypass y datos falsos). Opción C, gate de sesión revocable con port F2C0 ya validado, origen confiable explícito, verificación antes y después, close irreversible, presentación vacía por defecto: **ADAPTAR**. Sin dependencias/API pagas ni conexión a red.

## B4 — Diseño preciso / límites de B6
Unidad delimitada F2C1A, **cuatro rutas exactas candidatas**:
1. `v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialDevForegroundSessionGate.kt` — gate interno de una única sesión, con callbacks de foreground, epoch, admisión del host, Household y Commercial; acepta exclusivamente lector tipado F2C0 suministrado por un host probado (nunca por Intent, URL o sourceRef). Cada read exige elección humana vigente; comprueba garantías antes/después; ante excepción/denegación devuelve rechazo sin dato. `revoke()` cierra el port y no permite reanimación.
2. `v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialDevActivity.kt` — amarrar gate al lifecycle; `onPause`/`onStop` revocan y vacían Panel; `onResume` no reabre un port ni restaura autorización. Arranque real sigue en estado `Sin revisión comercial autorizada`.
3. `v5/app/src/w04AndroidTest/kotlin/ar/com/faro/v5/app/S4W04CommercialForegroundSessionAndroidTest.kt` — cuatro **nuevos** casos de frontera/lifecycle, con colaborador de prueba que NO produce precios ni simula un grant real; no repetir los cuatro tests E.
4. `.github/workflows/faro-v5-s4-w04-f2c1a-foreground-session.yml` — Android API35, SDK37/JDK21, cuatro casos XML únicos, prueba de manifest offline y guardia por SHA/permiso exact4, artefacto XML.
**F2C1B separado** será responsable de un issuer Android realmente confiable, raíz DEV ya validada, selección humana concreta de observación, adaptación de `SelectedRead` al contrato D1/F1 y `renderAuthorized` de E. Hasta certificarlo, no colocar datos observados en la vista normal ni afirmar F2C PASS.

## B5 — Adversarial/criterios de aceptación
T01 ausencia de port/elección niega lectura y la Activity real arranca vacía. T02 foreground+epoch+ambos grants admitidos solamente para selección expresa; negar cualquiera antes o después rechaza, sin precio en el fixture. T03 pasar a STARTED/onPause cierra irrevocablemente y limpia vista; volver a RESUMED no reabre ni muestra estado viejo. T04 ABA de epoch/host, doble attach, callbacks que lanzan excepción o cierre duplicado => fail closed; sin fuga ni apertura de red.
- Comprobar namespace/applicationId exclusivo, manifest sin INTERNET/servicios/providers/receivers, sin cambios a F2C0 ni al buildType; no tocar main/V4.4, S2, Finance, Apply, pagos, stock, G15, Auto/Noche, V2 productiva.
- **NO READY todavía**: primero publicar esta preauditoría sola, obtener guardia real SUCCESS, revalidar writer/HEAD y solo entonces permiso separado exact4 (con cinco blob SHA y marcadores correctos). Ejecutar B6 y XML API35, B7 independiente, checkpoint y writer RELEASED. Si rojo, RED_FIX solo de F2C1A.
- W04-F global, F2C1B y F3 permanecen OPEN incluso si estos cuatro casos pasan.
