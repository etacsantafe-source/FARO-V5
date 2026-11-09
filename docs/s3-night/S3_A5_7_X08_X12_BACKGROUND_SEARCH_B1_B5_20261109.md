# FARO V5 — S3.A5.7 X08/X12 Search revocado al perder foreground — B1–B5
Fecha: 2026-11-09 AR. Unidad: `S3_A5_7_X08_X12_BACKGROUND_SEARCH`. Estado: PREAUDITADO; **ningún PASS funcional** hasta CI y B7.

## B1 — Evidencia real y delta falsable
- Baseline `2ea8033aefd6e3d49436988bebaef07ed93d1b62`, rama `work/s3-night-20261102`, writer global RELEASED epoch638 verificado y CAS HELD epoch639; push permitido, cola STAGED activation_authorized=false. Catálogo 172 sin cambio; A3, V2 productiva, S4, Auto/Noche OFF; Moto G15 reservado para campaña final.
- Certificación anterior: 47 tests dirigidos en suites/SHA distintos, incluyendo X08 truncación Workspace >4096 1/1, X12 native lifecycle 2/2, X12 Compose Chat 2/2, X01/X02 3/3, X06 2/2, X07/X11 4/4. No reejecutar ni reinterpretar.
- Brecha por inspección de `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt`: `onPause()` cierra permisos de Activity y Pendientes, pero no llama a cierre de los cuatro Search grants; `Session.close()` sí lo hace. `FaroV5SearchScreen` borra hits al recibir `invalidations`; el federador invalida publicación y destino. Hipótesis: tras STARTED→RESUMED se podría seguir leyendo Search con la concesión de la sesión anterior; no inferir explotación sin prueba.
- Criterio: al salir de RESUMED, las cuatro concesiones DEV de Search dejan de autorizar; la UI descarta el hit previo; una búsqueda nueva queda UNAVAILABLE (no COMPLETE vacío). Solo una NUEVA acción nativa explícita puede reabrir la autoridad.

## B2 — Comparación V4.4 / V5 y delta
- Comparación V4.4 read-only ya inventariada en `docs/s3-night/S3_A5_X01_X12_B1_B5_20261109.md`: navegación/búsqueda sin importar permisos ni datos antiguos. V4.4 no es autoridad para esta sesión V2 DEV ni su puntero Reality8; no copiar repositorio ni reabrir sus tests.
- V5 existente separa cuatro owners Search: Workspace, Finance, Objectives, Matters; `FaroV5V2CoreDevSearchReadAuthority.close()` revoca `epochOrNull()` y emite StateFlow, mientras `FaroV5FederatedSearchSurfaceAdapter` rechaza hits anteriores y `FaroV5SearchScreen` limpia UI. Reusar esas rutas exactas; no introducir ACL paralela, flags de test o segunda base.
- El cierre tras onPause es distinto de las 47 pruebas: las previas no ejercen Search con hit visible y pérdida real del foreground antes de abrirlo.

## B3 — Investigación externa, alternativas y decisión
- Android Activity lifecycle oficial: https://developer.android.com/guide/components/activities/activity-lifecycle — onPause implica perder estado RESUMED; pruebas deben usar transición real del host y evitar reutilizar autoridad tras pause. USAR.
- AndroidX ActivityScenario oficial: https://developer.android.com/reference/androidx/test/core/app/ActivityScenario — `moveToState(STARTED)` y `recreate` permiten pruebas Android reales, sin usar un View mock. USAR.
- Compose testing semantics, fuente primaria: https://developer.android.com/develop/ui/compose/testing/common-patterns — comprobar comportamiento observable por árbol semántico, no un getter privado. USAR.
- Proyecto real Square LeakCanary, Apache-2.0: https://github.com/square/leakcanary/blob/main/docs/ui-tests.md — plantea detectar retención de Activity en instrumentación, pero agregar dependencia de heap/perfilado no aborda el permiso Search; REFERENCIA/DESCARTAR para esta corrección mínima.
- Alternativa A (USAR): cerrar los cuatro grants en onPause con el close idempotente que ya existe, invalidación transportada por Flow, reautorización solo tras acción nativa del usuario. Alternativa B (DESCARTAR): `Activity.recreate()` automática durante onPause; dispara navegación/cierre indiscriminado y confunde lifecycle. C (DESCARTAR): solo limpiar los chips UI sin cerrar el owner; deja posible lectura residual. Sin nuevas librerías, red, costo ni permiso de sistema.

## B4 — Solución única, rutas y aceptación
- Cambios productivos exclusivamente DEV: `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt` (llamada onPause) y `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt` (método Session que cierra cuatro Search grants de forma idempotente).
- Nueva prueba real Android emulada: `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57SearchBackgroundRevokeUiTest.kt`, workflow nuevo `.github/workflows/faro-v5-s3-a5-7-search-background-revoke-android-directed.yml`. Ningún source shipping, S2, catalog, A3, main, V4.4, permisos de red ni datos reales.
- Aceptación binaria: (1) un mensaje USER canónico produce hit en Search, (2) STARTED→RESUMED elimina hit y búsqueda fresca devuelve UNAVAILABLE sin falso cero, (3) tras `recreate` y clic DEV explícito, búsqueda canónica vuelve a hit; nunca hay Apply ni respuesta IA simulada.
- Única clase de prueba nueva: `OK (1 test)` + `INSTRUMENTATION_CODE: -1` + marcador explícito 1/1, APK emulada aislada. Guard seven-block-evidence del mismo SHA también SUCCESS; B7 aparte.

## B5 — Adversarial, STOP y límites
- Fail-closed: cerrar todo el grupo incluso cuando una lectura esté en vuelo; revalidación de grants antes/después de consulta y apertura, mutación de token imposible desde UI, no reenviar hit anterior con string idéntico. Cierre idempotente y sin guardar token durable; una sesión no resucita por onResume.
- Riesgo: Compose `invalidations` puede perderse al no estar colectando; probar por UI que el hit desaparezca, no solo por estado JVM. Si falla, RED_FIX justificado dentro de la misma unidad, jamás PASS por compilar.
- No afirmar revocación ACL de un owner *independiente durante consulta*, restore ABA ni accesibilidad física; estos alcances de X08/X12 quedan abiertos. No desbloquear A3/X04-X05 ni certificar X03, X09 o X10 por este trabajo.
- Permiso B6 requiere este documento en commit previo, SHA blob exacto y aprobación `READY` por ruta en documento de control separado. Writer CAS único; liberar antes de CI, sin repetir 47 pruebas previas; G15 solo campaña física final.
