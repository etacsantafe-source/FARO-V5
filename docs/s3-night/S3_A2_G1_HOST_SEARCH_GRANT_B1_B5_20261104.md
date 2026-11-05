# FARO V5 — S3.A2 G1 · host-owned Search grant, unidad acotada B1–B5 (04/11/2026 AR)

**Unidad:** `S3_A2_G1_HOST_SCOPED_SEARCH_GRANT`. **Base:** `0bb28d10fa43fd52069db80cfdf267ef9a12152e` de `work/s3-night-20261102`. **Objetivo acotado:** construir el owner de sesión de lectura Search *en shipping code*, conectarlo al graph de Workspace activo pero **SIN** conectar aún Buscar a la UI ni activar V2 productiva. **G1 formal sigue OPEN** hasta probar revocación entre procesos, restore y la frontera de grants definitiva. **S2 CLOSED, S4/Auto/Noche/V2 productiva OFF, catálogo 172 intacto.**

## B1 EVIDENCIA
- `FaroV5CompositionRoot.java` abre un Workspace sólo desde el graph activo cuando `V2_ACTIVE_ENABLED`, actualmente `false`; `DeliveryGraph` ya tiene ciclo de vida `close()` y bloqueo del puntero durable `ActiveGenerationPointer(sequence,generation)` reusado por las sesiones de Chat.
- `FaroV5BootstrapActivity.java` pasa `null` para Search expresamente por falta de ACL shipping. Mantener `null` esta unidad. No instalar una superficie de Search en V1.
- `FaroV5WorkspaceSearchReadSource.kt` valida `aclEpoch()`, generación, admisión, revisión y relectura; `FaroV5WorkspaceSearchSurfaceAdapter.kt` invalida locators y exige publicación fresca y selección real. La integración DEV `FaroV5V2CoreDevSearchReadAuthority.kt` es host local y **no** autoriza el grafo productivo.
- `FileActiveGenerationPointer.kt` registra listener por raíz para instancias del mismo proceso, no entrega eventos cross-process; el `SingleProcessMaterialWriteGate` tampoco es un lock multiproceso. Los 32/32 tests dirigidos y las cuatro CI del `29a295ad` tienen B7 PASS en `0bb28d10` y NO se reabren.
- Falta un owner explícito para grant/revoke separado del token de escritura Workspace y del estado visual. La transición de G1 a G2 necesita esa pieza; no implica que haya un fallo expuesto en shipping hoy.

## B2 COMPARACIÓN V4.4
- V4.4 `FaroV4GlobalSearch.kt` tenía lectura canónica diversa por FINANCE/OBJECTIVES/FOLLOW_UP/SMARTCOMPRA con degradación, **pero no es** autoridad de lectura del Workspace V2. Reusar la separación de propietarios y no copiar tablas/código antiguo.
- V5 ya tiene `SearchAuthorityStamp`, Source/Adapter, generación durable, Workspace canónico, material gate y grants DEV demostrados. **Reusar** estas piezas; no introducir sexto owner persistente, nuevo esquema FBS2, segundo writer, permisos Android ni proveedor de terceros.
- El owner propuesto vive en el **host de sesión activa**, no en Compose ni en un index/caché de resultados. El constructor aislado de pruebas no es un permiso para que shipping monte la UI.

## B3 INVESTIGACIÓN EXTERNA Y ELECCIÓN
- **USAR:** OWASP Authorization Cheat Sheet (https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html), deny-by-default y validar autorización en cada lectura/selección. El grant no puede equivaler a `ready=true` o a un token de escritura.
- **ADAPTAR:** Kotlin `StateFlow` (https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/): reenvía el estado actual al suscriptor tardío y coalesce eventos; el flujo sólo notifica, jamás confiere permiso. Releer owner tras suscribirse y antes/después de cada acceso.
- **ERROR REAL:** Element X Android PR #7877 (https://github.com/element-hq/element-x-android/pull/7877) revocaba por error la cuenta equivocada. Probar grant A/B distintos e independencia de revocación.
- **DESCARTAR:** epoch fijo, revision de Workspace como ACL, usar el pointer ID como permiso, UI bool, `Room` invalidation como ACL y trasladar literalmente la clase DEV a shipping. La alternativa durable con tablas/fichero ACL adicional puede permitir multiproceso pero exige una política de identidad/restore y una auditoría de migración FBS2 que no está autorizada para esta subunidad.
- **ELECCIÓN:** autoridad *efímera, separada y revocable* por sesión host con identidad de concesión aleatoria + contador creciente, scope de generación y secuencia durable y señal StateFlow. Reinicio sin reusar grants: DENY por defecto hasta apertura/grant nuevos. El token de epoch no es un secreto ni una autorización persistente.

## B4 UNIFICACIÓN — DELTA ONLY
**Rutas funcionales máximas (4):**
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveWorkspaceSearchGrantOwner.kt` nueva clase con grant explícito sólo si host admitido; `epochOrNull` fail-closed; revoke + regrant con epoch distinto; close definitivo; `Flow<Unit>` de invalidaciones como pista de refetch. Cada instancia es scope de una sesión, no un ACL universal.
2. `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java`: integrar sólo si existe Workspace **activo y certificado**, ligar admisión a puntero durable completo + recuperación; registrar listener de pointer -> revoke, liberar owner/listener antes de cerrar graph, crear source+adapter canónicos de Search como puerto interno **no montado en la UI**. Ningún permiso del Workspace staged. Dejar `BootstrapActivity` Search `null`.
3. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveWorkspaceSearchGrantOwnerTest.kt`: pruebas reales de grant/revoke, regrant ABA, owner A no afecta B, cierre/reinicio y stale hit canónico, pointer cambio A→B→A sin observador.
4. `.github/workflows/faro-v5-s3-home-notice-directed.yml`: ejecutar clase nueva con XML exacto y conservar suites históricas.
**G2 no se monta:** el cambio sólo deja la autoridad y el puerto preparado en graph y no expone Buscar en shipping. Esta subunidad no cambia AndroidManifest, firma, catálogos ni backup.

## B5 AUDITORÍA ADVERSARIAL PREVIA
- **Bloqueos estrictos:** V2 flag false o Workspace null -> no grant, no Search port, V1 idéntica; recovery no completada -> deny; puntero durable sequence cambiado -> deny sin necesitar notificación; owner cerrado -> deny permanente; acto de regrant genera nueva identidad; hit previo jamás abre después de revoke/regrant.
- El host, NO la UI, administra concesión y revocación; no utilizar token de escritura Workspace ni generar grants por efectuar una búsqueda. Cada lectura comprueba owner y puntero; Search adapter verifica sello antes de publicar/abrir y se mantiene aislado hasta G2.
- Reentrancia/carrera: antes y después de read; callback de revocación puede llegar tarde pero el estado del owner es fuente de verdad. El `StateFlow` coalesce: no asumir que observa cada transición, emitir replay + revalidación.
- Distintos procesos: NO atribuir señal de revocación de uno al otro, ni fiar el enforcement a un listener local; los grants efímeros no se comparten. Transacciones que crucen proceso no certificadas. Restore de Workspace con nueva generación tiene nuevo pointer; no copiar grant en FBS2, ni rehidratarlo tras app restart.
- Los tests no implican que el ACL persistente multiusuario esté resuelto. Un diseño futuro de cuentas o acceso interproceso requerirá owner de política real y su registro durable separado. Cualquier nueva capacidad de acceso a usuarios debe denegarse hasta que sea explícitamente admitida.
- **Resultado B5:** `READY_FOR_BOUNDED_IMPLEMENTATION_ONLY` sujeto a permiso en otro commit y a `FARO_PROTOCOL_PREFLIGHT_PASS`. No acreditar G1 completo por los tests JVM ni activar V2. B7 debe auditar diff, XML y riesgos residuales.

**Próxima unidad dependiente tras B7:** comprobar cierre/restore/observabilidad cross-process y posible owner de política persistente. Si no existe frontera real multiproceso/multiusuario, documentar por qué la sesión local cumple el caso de uso antes de declarar G1 formal PASS. Mantener S3 ≈40% estimado, 0/5 etapas formalmente cerradas, S3.A2 0/5 gates.
