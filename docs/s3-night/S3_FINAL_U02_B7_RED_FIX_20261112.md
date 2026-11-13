# S3 final U02 — B7 RED_FIX: AGP9 Kotlin source registration
Fecha AR 2026-11-12. Unidad S3_FINAL_U02_DISTINCT_IA_APP. Código rojo eb6c61e9cabd1cb20317b5ce96386004c73c9a20; run43342015516 job131612340988 FAILURE. Guard43342015370 SUCCESS. No instalar artefacto13998974636: APK incompleto sin Activity, no certificación.
## B1
Log exacto: assembleX03AiDev termina, compileX03AiDevAndroidTestKotlin falla UNRESOLVED_REFERENCE FaroV5X03U02IsolatedAiActivity en test línea45. El root src/debug/kotlin se registró en AndroidSourceSet.java. No se ejecutó Android ni XML; no afirmar 0/2 PASS. Error de configuración propio de unidad, no defecto de S2 ni de fuente host histórica.
## B2
AGP9 built-in Kotlin es obligatorio (build V5 baseline), no añadir plugin legacy. KotlinSourceSet del runtime existente usa .kotlin.srcDir; java.srcDir de tutoriales antiguos no es equivalente. Reutilizar host debug sin copiarlo, manifest X03 intacto.
## B3
Consulta primaria actual: https://developer.android.com/build/migrate-to-built-in-kotlin dice explícitamente que agregar directorios Kotlin a AndroidSourceSet.java no está soportado; solución AndroidSourceSet.kotlin.srcDir. https://github.com/android/skills/blob/main/build-system/agp/agp-9-upgrade/references/android/build/migrate-to-built-in-kotlin.md confirma en código/documentación Android real. Alternativas: ADAPTAR kotlin.srcDir (elegida); DESCARTAR aplicar kotlin-android/opt-out AGP9 o copiar clases a otro root (contamina baseline y duplica). Sin dependencias/coste remoto.
## B4
Una sola ruta v5/app/build.gradle.kts, sustituir `.java.srcDir("src/debug/kotlin")` por `.kotlin.srcDir("src/debug/kotlin")` para x03AiDev. El test que referencia clase y verifica launcher/Application real se conserva; no rebajar comprobación ni quitar test para ocultar rojo. CI nueva de misma unidad solo dos tests nuevos, no suite X01–X12 histórica.
## B5
La fuente debug ahora debe compilarse realmente; verificar referencias y binario manifest/Activity runtime en la suite ya autorizada. Ningún cambio de permisos, identidad o host; no tocar S2/main/V4.4 ni activar V2. Tras un fallo de esta hipótesis se corrige causa, no rerun idéntico. Segundo fallo causal exige reinvestigación B2/B3/B5. Writer liberado durante CI. READY separado después de guard documental.
## B7
RED_FIX, no PASS. Diff original limitado a cuatro rutas previas, guard histórico local197 units PASS y CI guard SUCCESS. Tres verificaciones históricas auto por build.gradle corren por triggers existentes, no dispatch manual para recertificar: M5 y Home SUCCESS observados, core pendiente en esa observación. Bloquear permiso original; esta disposición permite solo corrección de registro Kotlin tras nuevo READY. U01/U03/U04 y S3 global siguen OPEN/BLOCKED respectivos.
