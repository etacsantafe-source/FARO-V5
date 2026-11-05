# FARO V5 — S3.A2 G2 · B1–B5 conexión controlada del puerto Buscar al host (04/11/2026 AR)

**Unidad:** `S3_A2_G2_AUTHORIZED_HOST_UI_WIRING`. **HEAD revisado:** `87347037e79a713330c0254250fafe0d189ef2ce`. **Alcance:** cableado preparado y comprobable de `FaroV5SearchReadPort` del *DeliveryGraph* activo hasta la superficie Buscar bajo admisión real. **NO activar V2 productiva**, no abrir S4/Auto/Noche, no mutar S2, V4.4, main, catálogo 172 ni instalar datos físicos.

## B1 EVIDENCIA

1. **G1 cerrado, no repetir:** `docs/s3-night/S3_A2_G1_FORMAL_CLOSED_SINGLE_PROCESS_B7_20261104.md`; HEAD `87347037`; código final `f5f57a04`; CI Search dirigida `#41957095077` SUCCESS 42/42, manifest single-process 2/2, guard `#41957095087` SUCCESS. Los 10 tests del owner prueban grants efímeros y recovery. Sólo scope usuario único/un proceso.
2. `FaroV5CompositionRoot.java` crea `FaroV5ActiveWorkspaceSearchGrantOwner`, `FaroV5WorkspaceSearchReadSource` y `FaroV5WorkspaceSearchSurfaceAdapter` exclusivamente con Workspace real + `V2_ACTIVE_ENABLED`; retiene `DeliveryGraph.searchReadPort` y cierra owner al reemplazar graph. `readActiveWorkspacePort` ya expone Workspace activo, pero **no existe** `readActiveSearchPort`.
3. `FaroV5Application.java` delega puertos al root; `FaroV5BootstrapActivity.showShellSurface()` tiene el argumento de Buscar fijo a **`null`**. `FaroV5SurfaceHost.install` recibe el puerto existente, `FaroV5SearchScreen` invalida y oculta al recibir evento, y `openAuthorized` revalida autoridad/procedencia antes de seleccionar.
4. La variante aislada `FaroV5V2CoreDevActivity.kt` ya pasa `active.searchReadPort` a la superficie; NO es el host productivo. `FaroV5HostCertification.V2_ACTIVE_ENABLED=false`; V1 no tiene Workspace V2. `FaroV5StartupReadinessGate` distingue LOCAL_READY; callbacks del owner G1 revocan cuando deja de estar ready. Preflight actual **BLOCKED**: no programar antes de permiso nuevo.
5. Writer global observado RELEASED epoch 212 antes de adquisición CAS G2 epoch 213; cola CLOUD STAGED y activación false. Último guard G1 docs `#41957915923` SUCCESS. No hay prueba física Android G2.

## B2 COMPARACIÓN V4.4 / V5

- Informe original de V4.4 en `docs/s3-night/S3_BACKLOG_20261102.md`: su `FaroV4GlobalSearch.kt` permitía mezcla de owners FINANCE/OBJECTIVES/FOLLOW_UP/SMARTCOMPRA, degradación y advertencias en texto, y `Shell` podía perder encabezado de cobertura. **No copiar esa ruta ni tomar UI como autoridad**.
- En V5 ya están los contratos tipados `COMPLETE/PARTIAL/UNAVAILABLE`, el adapter que exige stamp del host y selección real, el SearchScreen con listener, y la ruta DEV aislada. **Reusar todo**: falta únicamente el último enlace en el host normal, manteniendo V2 OFF.
- G3 = contenido/idioma y navegación (parcial); G4 = owners federados (abierto); G5 = campaña Android/integración (abierto). G2 NO certifica G3–G5 ni autoriza otros owners.

## B3 INVESTIGACIÓN EXTERNA Y ALTERNATIVAS

- **USAR principio, no copiar código** — Guía Android StateFlow/repeatOnLifecycle https://developer.android.com/kotlin/flow/stateflow-and-sharedflow ; separación de estado y eventos https://developer.android.com/topic/architecture/ui-layer y navegación desde UI https://developer.android.com/topic/architecture/views/ui-layer/events-views . Consecuencia: la notificación de Flow no otorga permisos; comprobar el owner de la fuente en cada lectura y al abrir, y no conservar resultados revocados al remontar.
- **REFERENCIA** — Mozilla Firefox Android `BaseBrowserFragment` utiliza `store.flowScoped(viewLifecycleOwner)` y desmonta Find-in-page al navegar: https://github.com/mozilla-mobile/firefox-android/blob/main/fenix/app/src/main/java/org/mozilla/fenix/browser/BaseBrowserFragment.kt . Patrón: estado visible vinculado al lifecycle. Licencia Mozilla MPL 2.0 del proyecto; no trasplantar código ni motor browser.
- **REFERENCIA, NO COPIAR** — Signal Android https://github.com/signalapp/Signal-Android , aplicación de chat local sensible, AGPL-3.0; su arquitectura tiene restricciones/stack propios y no justifica integrar código ni dependencias en FARO.
- **USAR PRINCIPIO** — Android app security https://developer.android.com/privacy-and-security/security-best-practices : minimizar exposición de datos/componentes, no abrir provider externo. No agregar permisos ni servicio.
- **Alternativa A (ADAPTAR, elegida):** getter acotado sobre `DeliveryGraph` y una política pura de admisión `recovery AND LOCAL_READY AND V2 certified AND genuine host grant`. Ventaja: cero motor nuevo; reutiliza stamp, invalidaciones y la ruta que DEV ya usa. Coste: clase mínima + test.
- **Alternativa B (DESCARTAR):** duplicar la búsqueda en Activity o conceder acceso al instalar SearchScreen / usar `ready` como ACL; falla revocación y cruza privilegios. **Alternativa C (DESCARTAR):** crear otra base/índice/IPC para Search; añade owner, complejidad y backup/restore sin solucionar G2 y sin licencia/necesidad material.
- **Fallo externo convertido en prueba negativa:** flujo no equivale a autoridad; probar UI gate denegado para V2 OFF, ACL revocada, recovery incompleto, LOCAL_READY falso, null owner/port y host cerrado. Fallback no afirma cero resultados.

## B4 UNIFICACIÓN / DELTA ONLY

Sólo estas **6 rutas**:
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5SearchHostPortAdmission.kt` — política pequeña, pura de admisión que recibe puerto ya creado y owner **real**, nunca crea/renueva grants.
2. `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java` — getter `readActiveSearchPort`, no abrir `DeliveryGraph` si V2 no está certificada o readiness no listo; delegar al gate y no conceder permisos.
3. `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5Application.java` — passthrough interno del puerto.
4. `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapActivity.java` — sustituir **solo** argumento `null` de Buscar por `readActiveSearchPort()`. V1 permanece null con V2 OFF.
5. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5SearchHostPortAdmissionTest.kt` — nueve negativas/positivas sobre owner auténtico, sin fakes de ACL en producción.
6. `.github/workflows/faro-v5-s3-home-notice-directed.yml` — dirigir la clase G2 y verificar XML exacto, además de 42/42 previos. La CI V2 Core existente cubre compilation aislada si se dispara.

## B5 AUDITORÍA PREVIA ADVERSARIAL

- **Negar (retorno null sin abrir datos)** para V2 OFF, recovery incompleto, not-ready, owner ausente, puerto ausente, owner sin grant, owner revocado o cerrado. La política nunca invoca `grantAfterVerifiedHostOpen` ni `renewAfterVerifiedRecovery`; sólo el host gestiona identidad.
- **ABA y restauración:** G1 ya revalida puntero durable completo y no rehidrata grants. En UI el `readPort` sólo se instala para un host ya autorizado. Old hits siempre pasan por `openAuthorized`; el stale adapter los bloquea tras revocación.
- **Sin escalar:** nada de snippets persistentes en índices, nuevo schema FBS2, Android provider/exported, cambio de build flag, API, red, token de escritura, Apply ni modelo. Sin compartir datos entre app normal y paquete DEV.
- **Limitación explícita:** una prueba JVM del gate + compilación Android + CI no es campaña física. Un flujo de invalidaciones no sustituye la validación por operación. Si un port queda revocado durante una emisión visual, el adapter G1 deniega el open y emite invalidación; G5 conserva campaña de UX/rotación y lifecycle.
- **Acceptance B6:** compilación Debug, tests G2 exactos y 42/42 regresiones G1, guard protocol PASS en HEAD correspondiente, y variante aislada verificada o marcar su resultado explícito. **B7:** revisar diff, XML exacto, writer RELEASED y no marcar G2 CLOSED si falta evidencia. Si CI roja => RED_FIX, no avanzar a G3.
- **Dictamen B5:** `READY_FOR_BOUNDED_IMPLEMENTATION_ONLY` en las seis rutas anteriores, condicionado a permiso separado READY, preflight verificado y escritor CAS. No autoriza V2 productiva, ninguna fase adicional ni repetición global.
