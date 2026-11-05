# FARO V5 — G5 Android emulador · B7 RED_FIX de fuentes de test (05/11/2026)

## B7 AUDITORÍA FINAL INDEPENDIENTE
**Unidad:** `S3_A2_G5_ANDROID_EMULATOR_DIRECTED`; SHA `6a6076585191a2eba2173a4763b30e5f13d21fe1`. **Resultado RED_FIX:** GitHub Actions [#42067731793](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42067731793) FAILURE en `:app:compileV2CoreDevAndroidTestKotlin` después de que `:app:assembleV2CoreDev` completó. Error concreto: `Unresolved reference 'FaroV5A5TestContentProvider'` en test legado `FaroV5A5TextReaderInstrumentationTest.kt:22`. El emulador **NO EJECUTÓ** el caso G5. Corrección de memoria previa exitosa en esta ejecución; no confundir con otro OOME.

### B1 EVIDENCIA
La variante V2 aislada debe tener test runner y fuentes de AndroidTest propias. El AndroidTest compartido histórico depende de clases del debug habitual y no es fuente de V2. No es un fallo de los tres owners, solo de compilación de pruebas históricas incompatibles.

### B2 COMPARACIÓN
La suite AndroidTest clásica conserva sus fuentes en `src/androidTest` para debug; variante G5 requiere aislamiento opt-in. Protocolo exige no eliminar ni alterar los tests A5 históricos.

### B3 INVESTIGACIÓN EXTERNA
Android Developers [Gradle tips, sourceSets](https://developer.android.com/build/gradle-tips) y [build variants](https://developer.android.com/build/build-variants): se puede elegir la raíz `androidTest.setRoot("src/tests")` para que Gradle compile exclusivamente esas fuentes cuando se active una propiedad. Android Developers [Advanced test setup](https://developer.android.com/studio/test/advanced-test-setup) confirma que `src/androidTest` común se fusiona en variantes: agregar `src/androidTestV2CoreDev` **no** alcanza para aislar el legado común. 
- **ADAPTAR** `android.sourceSets.getByName("androidTest").setRoot("src/g5AndroidTest")` SOLO bajo `-PfaroG5AndroidTestV2CoreDev=true`, y mover el test G5 allí.
- **DESCARTAR** modificar el test A5 o crear una clase fingida que tape una dependencia de debug: contaminaría la evidencia y haría falsos PASS.
- **DESCARTAR** excluir pruebas sólo al ejecutar `-e class`: no evita el error de compilación de Kotlin.

### B4 UNIFICACIÓN
Cambiar solo `v5/app/build.gradle.kts` (source root condicionado), mover test de `v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5G5AndroidHostIntegrationTest.kt` a `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5G5AndroidHostIntegrationTest.kt`, y actualizar workflow para referenciar ruta correcta. Datos y prueba G5 no cambian; los tests de debug conservan fuentes originales sin el nuevo test.

### B5 AUDITORÍA ADVERSARIAL
Reemplazo de raíz de fuentes **solo con bandera CI opt-in**. Sin manipular root V2, Finance, Objective, Matter, ACL, permisos, shipping ni launcher. El test del emulador y restricciones de seguridad permanecen obligatorios; no declarar G5 Android PASS hasta tener `OK (1 test)` y `INSTRUMENTATION_CODE: -1` en la misma run. Sin nuevas credenciales/APIs pagas.

**Dictamen:** B7 RED_FIX de la unidad, con B1–B5 complementarios y permiso específico antes del siguiente commit. Writer exclusivo, S3.A2 4/5 y G5 OPEN.
