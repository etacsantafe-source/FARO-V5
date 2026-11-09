# FARO V5 — S3.A5.7 X12 Android native launcher — B7 PASS_SCOPED 2/2
Fecha 2026-11-09 AR. Unit S3_A5_7_ANDROID_NATIVE_LIFECYCLE. Android emulado API 35 **PASS_SCOPED**; Moto G15 **NOT_RUN**. S3.A5 global **OPEN**.

## B7 — Evidencia independiente de aceptación exacta
- B1–B5 anteriores al cambio en docs/s3-night/S3_A5_7_ANDROID_NATIVE_LIFECYCLE_B1_B5_20261109.md, blob 9c25b3e24cf98e74d41b540cbe7f234df967c2a0; permiso READY previo al código en padre 95e838ea23b1c4396bc4edec5c7fa21af266a495.
- Código https://github.com/etacsantafe-source/FARO-V5/commit/d64881dffb01576186f9cd0119a736c977629276, SHA exacto d64881dffb01576186f9cd0119a736c977629276, diff SOLO 2 archivos NUEVOS (v5/app/src/g5AndroidTest/.../FaroV5S3A57AndroidNativeLifecycleTest.kt y .github/workflows/faro-v5-s3-a5-7-android-native-lifecycle-directed.yml). Sin modificaciones productivas, Gradle principal, manifest, gateways, permisos ni catálogo.
- CI dirigida https://github.com/etacsantafe-source/FARO-V5/actions/runs/42559425316, job 128972350298, COMPLETED SUCCESS en el mismo SHA. Compilación :app:assembleV2CoreDev + :app:assembleV2CoreDevAndroidTest `BUILD SUCCESSFUL in 4m 57s`, marcador `S3_A5_7_ANDROID_NATIVE_APKS_COMPILED`. GitHub Android API35 real OS emulado instaló SOLO dos APKs aisladas.
- Salida Android instrumentation comprobada literalmente (no inferida por build verde): `OK (2 tests)`, `INSTRUMENTATION_CODE: -1` y `S3_A5_7_ANDROID_NATIVE_LIFECYCLE_A11Y_PASS 2/2`. Ejecutó exclusivamente clase FaroV5S3A57AndroidNativeLifecycleTest; NO re-ejecutó la certificación histórica G5 Android 1/1 ni seis suites A5 33/33.
- Guard obligatorio https://github.com/etacsantafe-source/FARO-V5/actions/runs/42559425132 COMPLETED SUCCESS mismo SHA. A5.7 X07/X11 4/4 previo permanece PASS_SCOPED sin rerun.

## Qué cubren las dos pruebas
1. App aislada ar.com.faro.alpha.v2coredev: solo cuatro acciones nativas esperadas, labels no vacíos explícitos, habilitados/clickables/focusables, objetivos táctiles >=48dp y estado inicial "V2 habitual permanece apagada"; sin botón Apply clandestino.
2. ActivityScenario mueve Activity a STARTED (paused), invoca botón Reality8: su guard requiere RESUMED, por lo que estado de almacenamiento local no cambia; vuelve a RESUMED, recrea Activity y no repite ni concede el click privilegiado.
En ambas, la APK y el proceso Android son reales en emulador efímero. No accedió a datos ni hardware del Moto G15.

## Límites y matriz global obligatoria
- X12 **parcial nativo Android emulado**; NO prueba Compose después de abrir Chat, conexiones reales, control de recursos del G15, rotación/small screen, lector de pantalla ni interacción física del Moto G15. Queda alcance X12 no certificado global.
- X07/X11 owner-revision JVM 4/4 certificados; no confundir con todo X06–X08 ni con UI real.
- X01–X02: Activity/Avisos/pendientes respaldados por A4 en su alcance histórico, pero falta X global de pantalla/flujo con receipts/cobertura.
- X03: "Consultar con FARO" real no cableado a V2 Core DEV. F1/quarantine tests previos no equivalen a activación autorizada ni a interfaz real.
- X04–X05: contrato H02/R2 Guardados exige A3 y ocho operaciones candidatas NO APROBADAS. No desbloquear ni modificar catálogo de 172: **BLOCKED_PERMISSION**.
- X09–X10: Review y Apply/receipts deben comprobarse en flujo autorizado completo; no derivar PASS del aislado Chat sin Apply ni activar V2 productiva.
- G15 físico: **una campaña final** solo al completar todo lo automatizable autorizado. V2 productiva OFF; S4 OFF; Auto OFF; Noche OFF. No usar APIs pagas, no tocar main/V4.4/S2.

**Dictamen**: S3_A5_7_ANDROID_NATIVE_LIFECYCLE = DONE_VERIFIED_PASS_SCOPED_2_OF_2. S3.A5 global = OPEN_X01_X12. Writer liberar epoch610; siguiente unidad autónoma compatible = investigación y prueba Compose Chat real en emulador, sin A3, sin egress ni Apply, previa B1–B5 y nuevo permiso.
