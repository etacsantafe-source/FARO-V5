# FARO V5 — W01 D1B0 · host Android real foreground sin permiso de lectura — B1–B5
Fecha 2026-11-17. Unidad `S4_W01_D1B0_REAL_ANDROID_FOREGROUND_READ_SHELL_DEV`. Estado DOC_ONLY, NO READY, NO B6/B7. W1 OPEN, W2 STOP.

## B1
HEAD base `add03673a5707f4d74f0d4ef725e1d0bb427a4c7`; D0 launcher B7 `docs/v5/FARO_S4_W01_D0_DEV_LAUNCH_B7_PASS_SCOPED_20261117.md`; D1A 2 PID API35 2/2 B7 `docs/v5/FARO_S4_W01_D1A_TWO_PID_INVALIDATION_B7_PASS_SCOPED_20261117.md` (blob `2ef14eb26d4e67f478e653be2abcc145167fc690`). Reconciler `S4W01FinanceReadForegroundReconciliation.kt` blob `0919ac9a1d1d2d17ad4927cc980a1d6f3cb9dac6`, lifecycle adapter blob `63dcc87a905960579d3ae9208e5e6bf5cfbbc83e`, private observer blob `ff022304228e77a9c0ed741f4163456b6a933d85`, W01 manifest blob `389afecc2a6a5d5199733a7af06efb4647915206`, launcher v2CoreDev blob `724e8f3c6bdc4697914851d9c449697f2f78ae36`. Ausencia real: ningún Activity W01 monta reconciler y ContentObserver; host READ biométrico requiere `FragmentActivity` mientras el launcher genérico extiende `ComponentActivity`. La política SQL durable no se enlaza aquí.

## B2
Reutilizar SIN modificar: launcher `open FaroV5V2CoreDevActivity` y hook `addAdmissionControls`; reconciler, lifecycle adapter, ContentObserver. Añadir sólo subclass W01 en source-set `w01FinanceDev`; no introducir FragmentActivity en S3/V2 Core ni reemplazar Chat. La nueva Activity `FragmentActivity` no exportada se abre por botón sólo en W01 DEV. Referencia V4.4: revisar separación dueño material/superficie; no migrar lógica V4.4 en este acotado. No repetir CI D0/D1A.

## B3
Alternativas: (A) convertir launcher global a FragmentActivity — rechazada por contaminación S3 y compilación de variantes. (B) cambiar launcher W01 a Activity nueva y perder Chat — rechazada por regresión UX. (C) subclass W01 del launcher genérico, botón explícito y Activity separada no exportada — ELEGIDA. Android lifecycle `LifecycleEventObserver` entrega ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY; `ContentObserver` asíncrono no equivale a COMMIT ni a policy epoch. Referencias primarias ya examinadas en D1A: https://developer.android.com/reference/androidx/lifecycle/LifecycleEventObserver y https://developer.android.com/reference/android/database/ContentObserver ; no hacer nuevo benchmark. `ActivityScenario` del test Android puede verificar transitions de OS reales, no biometría humana.

## B4
EXACT4 rutas funcionales:
1. `v5/app/src/w01FinanceDev/kotlin/ar/com/faro/v5/app/FaroV5W01FinanceDevActivities.kt`: subclass launcher W01 + pantalla `FragmentActivity` DEV. En onCreate validar package exacto, DEBUGGABLE, V2 productiva OFF; montar reconciler/adapter/observer en Main. Cache sólo bandera visual “sin autorización”; sin fuente SQL, `newObservedFence` siempre DENY; ni cache ni acceso a cuentas, epoch, grants, BiometricPrompt o datos reales. Nunca llamar lectura automática en ON_RESUME.
2. `v5/app/src/w01FinanceDev/AndroidManifest.xml`: launcher cambia sólo en paquete W01 a subclass, registra Activity lectora `exported=false`; mantener URI privada, Messenger no exportado, cero INTERNET.
3. `v5/app/src/w01AndroidTest/kotlin/ar/com/faro/v5/app/S4W01FinanceForegroundOwnerAndroidTest.kt`: Android API35 prueba lanzar Activity, resume/stop/resume, cierre y no datos; comprobar no lectura ni permisos ficticios. No repetir test interPID D1A.
4. `.github/workflows/faro-v5-s4-w01-d1b0-foreground-owner.yml`: preflight parent EXACT4, build W01 / test, inspect manifest (dos Activity separadas, private reader), Android API35 pruebas nuevas, sin tocar variantes productivas.
Salidas son sólo pantalla y lifecycle Android DEV. D1B1 posterior conectará SQLite propietario real + autorización fuerte verificada + host epoch y ciclo de vida fail-closed; no afirmar W1 terminado por D1B0.

## B5
Riesgos que fuerzan STOP: launcher global modificado; bandera de autorización derivada de Lifecycle o ContentObserver; balance/identidad visible sin SQL+humano; fragmento exportado; acceso Internet; BiometricPrompt lanzado sin petición; cambio en provider o servicio D1A; tests falsos JVM; leaks tras onDestroy; fallback de Activity a V2 productiva; pruebas en emulador no ejecutadas; CI sin XML y SHA. Mitigación: W01-only source + manifest y dos pruebas Android negativas; B5 independiente específico antes de READY.

## B6
NO RUN. Código todavía BLOCKED.

## B7
PENDING. Esta preauditoría NO autoriza aplicar cambios.