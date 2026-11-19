# FARO V5 · W02-E · Montaje Android aislado

Unidad `S4_W02_E_OBJECTIVES_ANDROID_DEV`. Base inspeccionada `1978c4dc5d4c344432b02a039916e6a6a3d36518`. Guardar B7 de D y validar guardia antes de emitir READY.

## B1
- El drawer de FaroV5ShellNavigation ya incluye OBJETIVOS y FaroV5AdaptiveShell admite contenido de ruta. FaroV5SurfaceHost instala Chat real y hoy usa el contenido no disponible por defecto. Los hosts W02-B a D deben quedar vinculados al mismo Session/Workspace/Reality que Chat.
- Aceptación. APK DEV separada, acceso a Objetivos desde el drawer, alta/edición, avance, historial, correcciones, estados y pasos/referencias con source grants. Review/Apply y recibos sobre Chat seleccionado. Recuperar datos y recibos al relanzar sin duplicar. Mostrar nuevos y legados sin inventar porcentajes ni importes.

## B2
- FaroV4ObjectivesSurface en snapshot V4.4 `bfa0ee71f5b9cd104baaadd71f1e77a61f203939` separa cartera, ficha, avance real, pasos y consulta a FARO. Conservar acceso simple desde Chat/drawer, ficha legible y acción explícita.
- Reusar FaroV5AdaptiveShell, drawer, workspace, Timeline, sesión y flujo Review. Usar patrón nativo scrollable del DEV de W01 y su evidencia de fuente 200% sin alterar su UI, paquete o binario publicado.

## B3
- [Android escala 200%](https://developer.android.com/about/versions/14/features#non-linear-font-scaling). USAR sp y layouts que crezcan y puedan hacer scroll. Descartar alturas fijas y etiquetas solo visuales. Probar fuente 200%, selección y lectura de roles/etiquetas en API35.
- [Android estados de Activity](https://developer.android.com/guide/components/activities/state-changes). ADAPTAR admisión visible en los clics, revocar grants efímeros y limpiar formularios/lecturas en pausa o cambio de generación. Descartar una Activity exportada para mutar, grant guardado, deep link a Apply y DB adicional.
- [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics). Reusar shell/drawer y proporcionar contenido Objectives mediante ruta existente. Alternativa de crear una navegación paralela de Chat, DESCARTADA por duplicación y riesgo de que seleccione otra sesión. Sin dependencias ni permisos Android nuevos.

## B4
- Crear build type W02 separado con applicationId exacto y manifest offline. Añadir opt-in exclusivo para instrumentación, sin cambiar variantes preexistentes. IA remota y egress ausentes. Abrir Chat real desde el launcher local y montar Objetivos en la ruta de drawer. El Session crea las acciones desde su graph actual con comprobación de package, sandbox, generación, epoch y foreground.
- Cuando el usuario crea una Review desde Objetivos, enlazar al Chat que la contiene y permitir únicamente la decisión humana explícita. La acción de Apply revoca al salir de primer plano. Leer y autorizar referencias Finance/Matter en el owner concreto y limpiar identificadores/transcripciones visibles al revocar. El intento aprobado puede conciliar un receipt real tras recuperación, sin ejecutar un segundo Apply.
- Usar listas scrollables, controles etiquetados y fecha civil sin zona; mostrar notas/progreso/estado y disponibilidad parcial. Sin saldo o acciones financieras. Ejecutar JVM relevante y API35 en workflow dedicado con XML de todos los casos, regresión de Chat y ensamblado de APK aislada.

- Rutas funcionales exactas:
  - `v5/app/build.gradle.kts`
  - `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt`
  - `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt`
  - `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/S4W02ObjectiveSessionActions.kt`
  - `v5/app/src/w02ObjectivesDev/AndroidManifest.xml`
  - `v5/app/src/w02ObjectivesDev/kotlin/ar/com/faro/v5/app/FaroV5W02ObjectivesDevActivity.kt`
  - `v5/app/src/w02ObjectivesDev/kotlin/ar/com/faro/v5/app/FaroV5W02ObjectivesPanel.kt`
  - `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5SurfaceHost.kt`
  - `v5/app/src/w02AndroidTest/kotlin/ar/com/faro/v5/app/S4W02ObjectivesAndroidTest.kt`
  - `.github/workflows/faro-v5-s4-w02-e-objectives-android.yml`

## B5
- Negativas de salida a Home, rotación/recreación, nueva generación/sequence, doble confirmación, descarte, referencia no autorizada/retirada y declaración inválida. No usar callbacks Android simulados para certificar interacción. Verificar cualitativo sin ratio y lectura distinta de medición cero. Probar alta, edición, progreso, historial, corrección, paso, retorno a Chat y receipts tras reapertura.
- Verificar merged manifest, launcher único y prohibición de egress/OS dispatch. Android API35 emulador, G15 físico fuera de alcance. V2 productiva, Auto y Noche OFF.
