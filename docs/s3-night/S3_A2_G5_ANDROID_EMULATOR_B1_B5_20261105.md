# FARO V5 — S3.A2 G5 Android instrumentado aislado — B1–B5 (05/11/2026)

**Unidad:** S3_A2_G5_ANDROID_EMULATOR_DIRECTED. **Ámbito:** prueba `androidTest` + flujo Actions; **NO** cambia runtime de producción ni núcleo V2 DEV ni datos físicos. Usuario solicitó finalizar G5 sin repetir cadena manual ADB.

## B1 — Evidencia y brecha
- HEAD base: `5920f57d6184b6fdd1a3ab00bfccf21fc4714b3b`. Writer RELEASED epoch 265, adquirido exclusivamente epoch 266.
- G1–G4 cerrados, CI host JVM real `1/1`, núcleo V2 `10/10`, T4 `7/7`, aislamiento `4/4`; NO repetir como nueva certificación.
- En Moto G15 Chat y Workspace pasan; las tres tablas Reality para finanzas/objetivos/asuntos están vacías. Falta evidencia Android positiva con tres owners y garantías de aislamiento, navegación read-only y revocación.
- Aceptación exacta de esta unidad: `androidTest` inicia host real DEV (journal, generación y cinco stores) sobre una raíz creada dentro del directorio de cache del **paquete aislado**; finaliza sesión; siembra una transacción Reality con APIs de Finance/Objective/Matter; reabre; federación tres namespaces, detalle tipado one-shot, sin importes/IDs; negativo sin hits; hit viejo inválido tras cierre/reapertura; inspección de permisos/IPC de paquete Android; borra fixture en finally. Cero datos de usuario.
- Resultado esperado: Android emulador PASS XML/log, **no** G5 PHONE PASS ni S3.A2 CLOSED por sí solo.

## B2 — Comparación y reutilización
- V4.4 no exponía federación real V2 DEV G4; no trasplantar stores. G4 `FaroV5FederatedSearchSurfaceAdapterTest` y G5 JVM `FaroV5G5ActiveFourOwnerSearchTest` ya certifican contratos; reutilizar la misma secuencia, sin crear nuevo owner ni duplicar SQL.
- A diferencia de JVM, `androidTest` usa APIs Android reales, SQLite real del dispositivo y PackageManager Android. Emulador elimina los pasos físicos de preparación y reproducción manual, pero no acredita Moto G15.

## B3 — Alternativas externas y evaluación
- Elegida/ADAPTAR: AndroidX Test Instrumentation + `reactivecircus/android-emulator-runner@v2` ([proyecto](https://github.com/ReactiveCircus/android-emulator-runner)); ejecución localizada en runner GitHub sin credenciales, servicios ni APIs pagas. Pertinente por historial CI dirigido y `androidTest` ya configurado.
- REFERENCIA: [Android developer instrumentation](https://developer.android.com/training/testing/instrumented-tests), API Java/Kotlin/AndroidJUnit4; ejecutar dentro de app aislada con UID propio.
- DESCARTADA: editar SQLite real en Moto con SQL, cambiar root/puntero o agregar botón de semilla a UX DEV/productiva; viola autoridad y genera datos no autorizados.
- REFERENCIA/NO ELEGIDA: Robolectric JVM (https://robolectric.org/) no valida OS, permisos ni SQLite/manifest reales; la suite JVM ya cubre esos contratos con limitaciones declaradas.

## B4 — Plan delta
- Crear `v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5G5AndroidHostIntegrationTest.kt` (sólo test).
- Añadir una propiedad Gradle **opt-in solo CI** en `v5/app/build.gradle.kts`: con `-PfaroG5AndroidTestV2CoreDev=true` seleccionar `testBuildType = "v2CoreDev"`; sin la propiedad, conserva el `debug` predeterminado y no afecta el shipping.
- Crear workflow GitHub dirigido `.github/workflows/faro-v5-g5-android-directed.yml`, con APK v2CoreDev + APK instrumentación, emulador Android API 35, ADB install, instrumentación **sólo** de la clase nueva, comprobaciones de resultado observadas. Sin tocar otros workflows o CI históricos.
- No modificar código shipping ni `v2CoreDev` shipping. La CI se activa sólo al cambiar el test/flujo; la tercera ruta `app/build.gradle.kts` sólo cambia el tipo de test bajo la propiedad opt-in, no la compilación shipping.

## B5 — Auditoría adversarial antes de implementación
- Ruta `Files.createTempDirectory(context.cacheDir.toPath(), "faro-g5-android-")` separada de `noBackupFilesDir/faro-v2-core-dev-root` real; jamás leer/escribir en la instalación del usuario; limpiar en finally.
- Cerrar completamente host y Reality antes de abrir el otro escritor; sólo transacción `RealityStore.run`, no SQL manual, ni prefabricar hits.
- Test real `PackageManager`: paquete DEV exacto, cero permisos solicitados; componentes de app esperado: único launcher exportado, cero providers/services/receivers. Instrumentation runner puede existir en **APK de test**, no app.
- El tipo Android instrumentado es `v2CoreDev` sólo por property de CI; ningún cambio de build de publicación, ninguna test APK ajena a DEV.
- Sin V2 productiva, no S4, no nuevas APIs, egress sin permisos de red; sin datos financieros verdaderos ni IDs reales. Las coincidencias visibles son títulos de ensayo inventados no sensibles.
- Rechazar falsos positivos: CI no es PASS por gradle compile ni APK installed. Exigir resultado de la clase instrumentada `OK (1 test)` + `INSTRUMENTATION_CODE: -1`; fallas y SKIP nunca equivalen a evidencia.
- Límite explícito: emulador Android NO suple Moto G15. Quedará solo certificación de los positivos y adversariales no ejecutados en teléfono, sin secuencias largas de comandos para el usuario.

**B5:** READY_BOUNDED_ANDROID_TEST_ONLY. Si CI falla: RED_FIX, documentar causa exacta; sin falso cierre.