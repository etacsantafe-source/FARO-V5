# FARO V5 — G5 Android emulador — B7 RED_FIX por memoria (05/11/2026)

## B7 AUDITORÍA FINAL INDEPENDIENTE
**Unidad:** `S3_A2_G5_ANDROID_EMULATOR_DIRECTED`. **SHA auditado:** `85e36b4c06e272e640467504a360b748fb9fb7f0`. **Dictamen:** `RED_FIX`, no G5 PASS.

## B1 EVIDENCIA
- Protocolo obligatorio [#42066290890](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42066290890) SUCCESS; V2 Core [#42066290864](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42066290864) SUCCESS; M5 APK [#42066290874](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42066290874) SUCCESS; Home notice [#42066290863](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42066290863) SUCCESS.
- Nuevo Android real OS [#42066291096](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42066291096) **FAILURE**, paso `Assemble isolated V2 DEV and G5 instrumentation APKs`; log del daemon: `java.lang.OutOfMemoryError: Java heap space` y `Gradle build daemon disappeared unexpectedly` al procesar/dexar variante. No llegó a ejecutar test en emulador.
- No declarar error de Finance/Objective/Matter ni Android PASS; el test todavía es `NOT_RUN`.

## B2 COMPARACIÓN
- Workflow de ensamblado dirigido M5 `.github/workflows/faro-v5-v2-core-apk-directed.yml` **SUCCESS** con un worker, compilador Kotlin en proceso y límites explícitos Gradle heap 3072 MiB / metaspace 768 MiB. Reutilizar esas opciones verificadas sin cambiar Kotlin/app ni V4.4.

## B3 INVESTIGACIÓN
- Gradle: documentación oficial sobre memoria de JVM [https://docs.gradle.org/current/userguide/config_gradle.html#sec:configuring_jvm_memory](https://docs.gradle.org/current/userguide/config_gradle.html#sec:configuring_jvm_memory). Hipótesis: configuración de JVM por defecto insuficiente y competencia entre procesos; aumentar heap dentro de runner y reducir workers evita el OOME.
- Alternativa A **ADAPTAR**: mismas opciones que M5 aprobado. Alternativa B DESCARTAR: aumentar máquinas/usar API pagas; innecesario sin prueba de A. Alternativa C DESCARTAR: saltar `assembleAndroidTest` o tomar compile como PASS; invalida G5.
- La investigación original de AndroidX instrumentation, runner reactivo y V4.4 sigue en `S3_A2_G5_ANDROID_EMULATOR_B1_B5_20261105.md`. No se reabre.

## B4 UNIFICACIÓN
- Cambiar **sólo** `.github/workflows/faro-v5-g5-android-directed.yml`: usar `--max-workers=1`, `-Pkotlin.compiler.execution.strategy=in-process`, `-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m` y `timeout` acotado como workflow M5 verificado; conservar compile de ambas APK y Android OS 1/1 obligatorio.

## B5 AUDITORÍA ADVERSARIAL
- Sin archivos shipping, datos reales, privilegios, Internet, teléfonos ni roots afectados. Timeout evita writer de CI indefinido (writer Git ya pertenece al trabajo manual, no al job). Cambiar memoria **no** relaja requisito de prueba exacta, PackageManager, revoke ACL, aislamiento ni el PASS del emulador.
- Si vuelve a fallar la misma hipótesis, volver a B2/B3 en vez de reintentar a ciegas. El Moto G15 sigue pendiente.

**B7 = RED_FIX acotado.** G5 OPEN; S3.A2 permanece 4/5. Sólo editar el workflow del permiso previamente auditado y verificar nuevo CI completo.