# FARO V5 · S3.A2 DEV-only canonical Search — B6 WAIT_CI / B7 PENDING

**Fecha:** 04/11/2026 AR. **Unidad:** `S3_A2_DEV_SEARCH_SESSION_OWNER`.
**Código exacto:** `8c01f4c4028a4b2f311e31031924fa194cb3303c` (protocolo FARO-UNIT en mensaje).
**Permiso B1–B5 en padre:** `be845434ce6b8c848ded554833e5f21859e7e6f8`; gate preflight `#41931706901` SUCCESS y log `FARO_PROTOCOL_PREFLIGHT_PASS S3_A2_DEV_SEARCH_SESSION_OWNER be845434...`.

## Alcance de código verificado por diff remoto (5 rutas)
- Nuevo `FaroV5V2CoreDevSearchReadAuthority.kt` (**solo DEV**): grant aleatorio por sesión validada, revocación al cerrar, invalidación observable StateFlow, epoch fail-closed.
- `FaroV5V2CoreDevAuthority.kt`: vincula owner, puntero y fuente canónica Workspace existente, misma selección y graph.
- `FaroV5V2CoreDevActivity.kt`: monta `searchReadPort` en shell aislado; sin permisos de Android adicionales.
- `FaroV5V2CoreDevAuthorityTest.kt`: 3 pruebas dirigidas nuevas (mensaje aceptado, cierre/reapertura, puntero/invalidation) sobre 6 previas; esperado 9/9.
- `.github/workflows/faro-v5-v2-core-directed.yml`: exige XML 9/9, no pasa por omisión de tests.

## CI en curso, NO PASS aún
- Mandatory seven-block protocol guard `#41932138079` **SUCCESS** @ exact code SHA.
- V2 DEV core compile/JVM XML `#41932138084` **RUNNING** cuando se abrió este checkpoint.
- V2 DEV signed/isolated APK gate `#41932138014` **RUNNING**.
- S3 Home + Workspace regression `#41932137991` **RUNNING**.
- Nunca reportar 9/9, APK binario ni G1 DONE hasta observar resultado y XML/log exactos. Sin prueba física. No publicar/instalar APK nueva en teléfono hasta la verificación humana de identidad/firma; ningún cambio al paquete habitual.

## B7 auditoría final pendiente
1. Comprobar `status=completed`, `conclusion=success`, `head_sha=8c01f4c4...` en 3 Actions dirigidas y guard.
2. Leer logs exactos: DEV core JVM 9/9, aislamiento manifest sin permisos; S3 Home 9/9, Workspace Search 7/7, bridge 5/5, Avisos 4/4; APK checksum, package y firma sin permiso.
3. Revalidar diff cinco rutas, V2 shipping OFF, no schema/FBS2, no nuevo egress, no side effects, writer liberado.
4. Si rojo, abrir B7=RED_FIX, identificar causa y preparar nueva unidad del MISMO scope bajo protocolo; no declarar PASS por el éxito del guard.
5. Si verde, registrar `B7 PASS` documental con SHA/XML, actualizar `last_code_review` y siguiente permit BLOCKED. Esto valida **Search DEV aislado**, NO ACL shipping ni S3.A2-G1 (formal aún 0/5).

**Estado:** B6=WAIT_CI, B7=NOT_RUN, V2 productiva OFF, S2 8/8 CLOSED, S3 ~40% estimado / 0/5 cierre formal; S3.A2 0/5 gates; Auto/Noche OFF.
