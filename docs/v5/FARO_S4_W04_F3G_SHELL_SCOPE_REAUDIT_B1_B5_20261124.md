# FARO V5 — W04 F3G · RED_FIX shell de emulador · B1–B5 delta exact1

Fecha 2026-11-24. Unidad S4_W04_F3G_GLOBAL_LOCAL_DEV_ACCESSIBILITY_ACCEPTANCE; código de t14 2ec27043ad55ccc584dc8ecb59248044c4b07162. Alcance único: el workflow Android API35.

## B1 — Evidencia SHA/log
CI 45370318211 ERROR antes de Gradle y t14. El emulador arrancó; el runner ejecuta cada línea script con /usr/bin/sh -c propio, por lo cual test "$actual" falla al perder el valor creado en otra línea. También cd v5 se ejecutaría en proceso distinto de Gradle. No hay artefacto ni XML. Guardia del código 45370318033 SUCCESS. No repetir tests t01–t13. Baseline de B1–B5 original doc F3G blob 4a8453640d0642afd352547bd1f755c97599075f.

## B2 — Reutilización
No tocar el test Kotlin ya creado ni host, datos, permisos, generación, Activity o schema. Reutilizar misma lógica de CI W04-F3A/F3B donde un solo comando cd v5 && ./gradlew funciona, y mismo setup Android/manifest offline.

## B3 — Alternativas de shell
A) Exportar la variable entre shells: no confiable, porque el runner invoca shells independientes. B) Eliminar la comprobación de font 200: rechazado; test Kotlin ya verifica fontScale >= 1.9. C) Ejecutar cada comando adb como una línea completa y unificar cd + Gradle en **una** línea: ELEGIDO. Evitar nuevas dependencias, APIs o sudo.

## B4 — Ruta exacta y cambio mínimamente verificable
Única ruta funcional autorizable: .github/workflows/faro-v5-s4-w04-f3g-global-local-dev-android.yml.
Sustituir secuencia:
  adb shell settings put system font_scale 2.0
  actual=...
  test "$actual" = "2.0"
  cd v5
  ./gradlew ...
por exactamente:
  adb shell settings put system font_scale 2.0
  adb shell settings get system font_scale
  cd v5 && ./gradlew ... :app:connectedW04CommercialDevAndroidTest
Una orden por sh -c, sin variable cross-shell. El test t14 es autoridad final del requisito de escala 200%. Mantener runner exacto t14, conteo XML 1/1, manifest offline y artifact ZIP. No duplicar test ni modificar 2a ruta.
Mantener tag FARO-UNIT:S4_W04_F3G_GLOBAL_LOCAL_DEV_ACCESSIBILITY_ACCEPTANCE y preflight head parent exact1; el script CI de control cuenta una ruta modificada, por lo tanto cambiar count de 2 a 1.

## B5 — Adversariales, prueba y STOP
Negar PASS si fontScale de Activity <1.9, Kotlin falla, XML missing, APK absent o guard rojo. El shell debe conservar cwd al llamar Gradle. Confirmar solo un diff de workflow, no source/test. No revalidar suites t01–t13 ni otros owners; precio histórico sigue no vigente. El red-fix debe pasar guard, CI original XML/APK, luego B7 final separado. Writer RELEASED durante CI.
