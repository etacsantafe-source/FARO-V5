# FARO V5 — W01 D1A auditoría B5 independiente del test Android inter-PID
17/11/2026. Unidad candidata: `S4_W01_D1A_ANDROID_TWO_PID_PRIVATE_NOTICE_TRANSPORT_DEV`.
Estado: **GO B1–B5 CORREGIDOS; NO READY SIN GUARD DOCUMENTAL**. Sin permiso de código ni B6/B7.
Revisión de preauditoría `docs/v5/FARO_S4_W01_D1A_TWO_PID_INVALIDATION_B1_B5_20261117.md` y código existente:
`S4W01FinanceReadPrivateInvalidation.kt` blob `ff022304228e77a9c0ed741f4163456b6a933d85`,
`S4W01FinanceReadPostCommitPublisher.kt` blob `1e4dce3a37ed62455189fda5c9843beb4669e47b`,
`v5/app/build.gradle.kts` blob `06efa77d0b8bb09911b0a74fb200e8d883fe1464`,
manifest W01 blob `ef4b2e16d82cd9535fb4c1645fa9599341f739f1`,
D0 B7 blob `aa4c6f35247dea93ef10b7a42731d2ad9e12676f`.

## B5 — Hallazgos adversariales
1. **FIX BEFORE READY — falso COMMIT**: la primera versión D1A pretendía llamar al método `publishAfterVerifiedCommitForDevOnly` desde sonda de transporte sin SQL. **RECHAZADO**: ese método contractual sólo corresponde a transición confirmada por SQL. La sonda usa `ContentResolver.notifyChange(uri, null, 0)` directamente en servicio remoto aislado y etiqueta en la evidencia `SYNTHETIC_IPC_ONLY`. Ninguna prueba interpreta esto como policy epoch, ACL, receipt o commit.
2. **Proceso real**: declarar `S4W01FinanceReadNoticeProbeService` con `android:process=":w01ReadProbe"` y `exported=false` sólo en `w01FinanceDev`; binder Messenger entrega PID remoto y ACK. Rechazar PASS si `remotePid == Process.myPid()`. No se permite AIDL/componente exportado ni launcher nuevo.
3. **Seguridad de entrada**: `onBind` del servicio exige `packageName == ar.com.faro.alpha.w01financedev` y `FLAG_DEBUGGABLE`; negar otras variantes aunque se intentase declarar el servicio. No secretos, cuentas, Identity, Internet, origen por consent.
4. **Prueba Android real**: registrar `ContentObserver` en proceso del test, enviar comando por Binder a PID remoto, esperar callback acotado; luego desregistrar y enviar nueva señal, confirmar ACK y que el observador cerrado no se reabra. El ACK no prueba por sí mismo el callback. Sin `MockContentResolver`, `Thread.sleep`, asserts de UI o saldo.
5. **Alcance**: se mantiene exactamente cinco rutas preauditadas: `v5/app/build.gradle.kts`, `v5/app/src/w01FinanceDev/AndroidManifest.xml`, `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadNoticeProbeService.kt`, `v5/app/src/w01AndroidTest/kotlin/ar/com/faro/v5/app/S4W01FinanceTwoPidNoticeAndroidTest.kt`, `.github/workflows/faro-v5-s4-w01-d1a-two-pid-notice.yml`. Ningún cambio a Reader/Reality/FBS2v4/V4.4/main/S3/W2.
6. **Variantes y emulador**: selector opt-in único `faroW01AndroidTest` mutuamente excluyente con `faroG5AndroidTestV2CoreDev`, `faroX03AndroidTest`, `faroS3IntegratedAndroidTest`; G5 anterior es el patrón CI de SDK37 + emulador API35. Manifest W01 service no exportado y provider privado; v2CoreDev mantiene cero providers/services. No reutilizar tests G5 como si fueran D1A.
7. **Límites de prueba**: ContentResolver notifica asíncronamente, no garantiza entrega durante muerte de proceso ni cambios raw SQLite. Android API primaria https://developer.android.com/reference/android/content/ContentResolver y documentación https://developer.android.com/develop/background-work/services/bound-services. D1B conectará owner real; la sonda no autentica humano ni concede render.
8. **Gating**: D0 B7 documentado, guard 44167039799 SUCCESS. Verificar guard de ESTE commit B5 antes de emitir READY. Inspeccionar 5 blobs/paths y SHA exacto; if fail => STOP. No prometer runtime success antes de emulador.

## Dictamen
**GO SÓLO a permiso READY individual EXACT5**, tras guard exitosa de documentación. Este documento no concede permiso ni escritura funcional; B6, CI y B7 siguen NOT_RUN. W1 abierto, W2 STOP, V2 productiva OFF.
