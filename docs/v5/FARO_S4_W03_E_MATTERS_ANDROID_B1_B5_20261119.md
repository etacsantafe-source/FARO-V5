# FARO V5 — W03-E · Android DEV Asuntos, Recordatorios y Avisos

Unidad S4_W03_E_MATTERS_ANDROID_DEV. Base funcional 79928d698592fcbc7823d666cfb731b2c15e26d9. Session D, mismo graph, sin V2 productiva.

## B1 — Evidencia
W03-D usa owners reales, Review/Apply/receipt y ACL de vínculo. D2B2B aportó restauración Auxiliary4, switch DEV y permisos por click de Avisos. Shell actual inicia en Chat y ofrece destino ASUNTOS en menú; el SurfaceHost solo inyecta panel W02. La pantalla Avisos actual proyecta read-only sin revisión. Faltan el panel Android de Asuntos/Recordatorios y acciones de atención para el mismo Notice leído.

## B2 — Comparación y reutilización
FARO V4.4 FARO@bfa0ee71f5b9cd104baaadd71f1e77a61f203939 FaroV4FollowUpSurface.kt muestra fecha, prioridad, estado, filtros y recordatorios. Por aprobación humana usar Chat por defecto, menú e iconos actuales y panel de Asuntos con acceso a Recordatorios. Adaptar el worker y restauración foreground de W02, View nativo dentro del shell, Review Disclosure explícito y port Avisos ya existentes. No copiar DB, rutas, ni servicios V4.4.

## B3 — Investigación
ADAPTAR guías Android de accesibilidad (https://developer.android.com/guide/topics/ui/accessibility/apps y https://developer.android.com/training/testing/espresso/accessibility-checking). Usar textos en sp, botones nativos accesibles, listas que desplazan y controles con textos en español. API de plataforma/AndroidX ya incorporada, sin dependencia nueva. Elegir widget nativo probado para formularios y conservar Compose para Avisos. DESCARTAR UI simulada, ajustes globales Android y mezclas de shipping con DEV. Pruebas instrumentadas completas quedan en G.

## B4 — Unificación y rutas
Variant aislado w03MattersDev con manifest offline y package exacto. Abrir Chat en foreground inicial y reutilizar menú. Preparación DEV autorizada por usuario cuando la sesión sigue en esquema anterior (Planning8, Finance9, Auxiliary4); siempre cerrar sesión entre pasos. Asuntos lista/detalle, alta/edición, estado/fecha/prioridad, historial, compensación del último cambio de estado compatible, Review y Apply real. Recordatorios con alta/edición/recurrencia/estado/vínculo autorizado y atención revisada de próxima ocurrencia. Avisos usa mismo read port más opt-in de revisión esperada, actions explícitas Visto/Descartar y recibo durable. Una única sesión; revoke al perder foreground. E construye APK y chequea manifest, G ejecuta regresión e integración en API35 con recuperación y 200%. Los dos jobs W01 con preflight hardcoded por build.gradle solo se disparan para su unidad, preservando manual dispatch del job que ya lo tenía.
- v5/app/build.gradle.kts
- v5/app/src/w03MattersDev/AndroidManifest.xml
- v5/app/src/w03MattersDev/kotlin/ar/com/faro/v5/app/FaroV5W03MattersDevActivity.kt
- v5/app/src/w03MattersDev/kotlin/ar/com/faro/v5/app/FaroV5W03MattersPanel.kt
- v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt
- v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt
- v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveAvisosBindingFactory.kt
- v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5SurfaceHost.kt
- v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5AvisosSurface.kt
- .github/workflows/faro-v5-s4-w03-e-matters-android.yml
- .github/workflows/faro-v5-s4-w01-d1a-two-pid-notice.yml
- .github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1b-private-invalidation.yml

## B5 — Auditoría previa
Sin activación de V2 productiva, OS, permisos extra, servicios, egress ni G15. UI no ejecuta sin Review y confirmación. Confirmar dos veces (approve y apply), recheck de permisos y de key/revisión en host. No cachear datos en pausa; limpiar pantalla/grants y reverificar. Fuente de fecha y zona civil explícita, manejo de entradas inválidas y snapshots obsoletos. UI no ofrece deshacer un estado que el contrato no permite. Isolated manifest: único launcher W03, no INTERNET/POST_NOTIFICATIONS/alarma/servicios/proveedores/receptores de app. CI compila APK y guarda hashes; no atribuir G PASS hasta emulador y evidencia B7 final.
