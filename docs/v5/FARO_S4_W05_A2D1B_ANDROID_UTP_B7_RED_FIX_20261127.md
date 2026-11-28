# FARO V5 · W05-A2D1B · B7 RED_FIX de UTP

## B7 · Evidencia y delta

- Código `d5bd6aac3594b6d0ebdbbac91eb796ca96b2a30a`, guardia 46096599344 SUCCESS. Job 140829252718 de CI 46096599323 compiló las APK y aprobó el contrato JVM. Verificación independiente del artefacto 15244205429: XML JVM 3/3 sin fallos, errores u omisiones, APK SHA-256 `f1b57f81f9eb82847d94d86e43c9ddc5a1e1f7e5333c65f4b0f41347cbbfd25f`.
- El emulador API35 arrancó y recibió fuente al 200%. Gradle no llegó a la instrumentación: el modo offline impidió resolver `com.android.tools.utp:android-test-plugin-result-listener-gradle:32.3.1`, usado únicamente por el runner de pruebas.
- Autorizar delta de workflow exact1: quitar `--offline` de Gradle en GitHub Actions para resolver UTP, igual que otros workflows Android API35 del repo. La APK permanece sin permiso de Internet y el test lo comprueba. Sin cambio Android funcional ni más rutas.
- El avance global es 3/5. A2D1B espera CI Android y XML del mismo SHA; A2D2 espera A2D1B B7 PASS.
