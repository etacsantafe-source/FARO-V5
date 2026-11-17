# FARO V5 — S4 W01 D1A Android ContentObserver real entre dos PID — B7 PASS acotado
Fecha: 17/11/2026 (Argentina). Unidad `S4_W01_D1A_ANDROID_TWO_PID_PRIVATE_NOTICE_TRANSPORT_DEV`.
**B7 PASS_SCOPED_REAL_ANDROID_API35_TWO_PIDS_SYNTHETIC_NOTICE_ONLY.**
No certifica la emisión desde SQL real ni autorización humana, Activity Finanzas, ciclo de vida de dueño DEV, saldos, G15 físico o cierre de W1.

## B1 — Evidencia de autorización por SHA
- D0 launcher W01 B7 PASS scoped previo `docs/v5/FARO_S4_W01_D0_DEV_LAUNCH_B7_PASS_SCOPED_20261117.md` blob `aa4c6f35247dea93ef10b7a42731d2ad9e12676f`.
- D1A preauditoría corregida `docs/v5/FARO_S4_W01_D1A_TWO_PID_INVALIDATION_B1_B5_20261117.md` blob `8255db8b8e5c270033f5110587bc48e02b2ccdc7`; B5 independiente `docs/v5/FARO_S4_W01_D1A_TWO_PID_INVALIDATION_INDEPENDENT_B5_20261117.md` blob `1cc4275d6f6732cf56f59aa48c47b28619316e75`. Guardia B5 `44167472171` COMPLETED SUCCESS.
- Permiso READY EXACT5 separado `43819d566a1e351ebc3c063a74a226dc80973fdb`, guard `44167689508` SUCCESS. Commit de código `a6a15af2ff0cce1a9874336c66f3f8643ff27f35` con FARO-UNIT; guard funcional `44168268447` COMPLETED SUCCESS, preflight parent EXACT5 SUCCESS en job dirigido.

## B2 — Diff funcional exact5 (sin cambios laterales)
1. `v5/app/build.gradle.kts`: opt-in `faroW01AndroidTest=true` exclusivo, instrumentación sólo W01 Dev.
2. `v5/app/src/w01FinanceDev/AndroidManifest.xml`: service privado `:w01ReadProbe`, `android:exported=false`, mantiene provider privado existente sin internet.
3. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadNoticeProbeService.kt`: Messenger de Binder, responde PID y ack; dispara sólo `ContentResolver.notifyChange` de prueba, **NO** llama publisher post-COMMIT, no SQLite, cuentas ni persona.
4. `v5/app/src/w01AndroidTest/kotlin/ar/com/faro/v5/app/S4W01FinanceTwoPidNoticeAndroidTest.kt`: dos tests instrumentales, PID distinto, callback/desregistro, URI de provider no legible.
5. `.github/workflows/faro-v5-s4-w01-d1a-two-pid-notice.yml`: SDK37, 2 APK aislados, inspección manifest, emulador Android API35 y validación `OK (2 tests)`.
No se tocó `RealityStore`, ACL, ledger, Apply, UI/Compose, `ContentObserver` de producción, FBS2v4, main, V4.4, S2/S3 ni W2.

## B3 — CI y Android reales, exactos
- Workflow `44168268784`, job `134340076932`, commit `a6a15af2ff0cce1a9874336c66f3f8643ff27f35`: COMPLETED **SUCCESS**.
- Preflight individual `FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_D1A_ANDROID_TWO_PID_PRIVATE_NOTICE_TRANSPORT_DEV 43819d566a1e351ebc3c063a74a226dc80973fdb`.
- SDK 37 instalado; `:app:assembleW01FinanceDev` y `:app:assembleW01FinanceDevAndroidTest`: `BUILD SUCCESSFUL in 6m 29s`.
- Manifest empaquetado verificado W01: provider privado `exported=false`, servicio remoto `exported=false`; V2 Core sin providers/services, no INTERNET, ni llamadas al publisher post-COMMIT.
- Emulador Android `API35` ejecutó `S4W01FinanceTwoPidNoticeAndroidTest`: `t01RemoteServicePidDiffersAndObserverStopsAfterUnregister` terminó `INSTRUMENTATION_STATUS_CODE: 0`; `t02PrivateProviderRefusesAnyFinanceRead` también `CODE: 0`; salida completa `OK (2 tests)`, `INSTRUMENTATION_CODE: -1` y `W01_D1A_ANDROID_API35_REAL_TWO_PID_SYNTHETIC_NOTICE_2_OF_2_PASS`.
- El test `t01` exige `remotePid != Process.myPid()`, notificación recibida y ausencia de nuevos callbacks luego de unregister. El test `t02` comprueba que URI anchor no lee filas. **Los dos tests pasan realmente en emulador OS, no solo fake JVM.**
- No se instaló ni probó APK en el Moto G15. No confundir `androidTest` con prueba física humana de pantalla.

## B4 — Límites explícitos
- Un `notifyChange` sintético sólo prueba entrega de señal Android a observador vivo; no demuestra COMMIT SQL ni entrega garantizada ante crash o cambios raw SQLite.
- El método `publishAfterVerifiedCommitForDevOnly` continúa restringido a operaciones SQL confirmadas; el test remoto no lo invoca.
- Las ACL y la reconciliación `ON_RESUME` deben seguir consultando SQLite. No se montó `S4W01FinanceReadForegroundReconciliation` ni `S4W01FinanceReadAuthHost` al host real. **Próxima unidad D1B** requiere B1–B5 y permiso individual por rutas, incluyendo la identidad del host Android, la transición foreground y políticas fail-closed.
- W1 sigue abierto; W2 STOP, V2 productiva OFF, Auto/Noche OFF, sin APIs pagas, sin egress ni pagos/transacciones reales.

## B5 — Independencia y no repetición
Verificado diff EXACT5 y separación de build variants. Tests anteriores D0, C y S3 no se repiten; esta unidad resuelve únicamente transporte real inter-PID. El STEP de la sonda no se considera prueba de saldos/biométricos ni certificación completa de W1.

## B6
IMPLEMENTED `a6a15af2ff0cce1a9874336c66f3f8643ff27f35`; Android API35 2/2 PASS y compiles dos APK. Writer de código epoch1148 RELEASED antes de CI.

## B7
**PASS_SCOPED_REAL_ANDROID_API35_TWO_PID_SYNTHETIC_PRIVATE_NOTICE_2_OF_2**.
Consumir permiso READY D1A a BLOCKED, publicar checkpoint y confirmar guard de docs y writer RELEASED remoto. Siguiente D1B sólo después de preauditoría propia. No declarar W1 DONE.
