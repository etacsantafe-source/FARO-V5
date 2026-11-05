# FARO V5 — S3.A2 G4 real Finance/Objective/Matter federation and typed navigation · B1–B5

Fecha 05/11/2026, rama `work/s3-night-20261102`, HEAD revisado `0fab63a04de055e3479b33c6d38e1072646f42e3`; unidad `S3_A2_G4_REAL_FEDERATION_TYPED_NAV`.

## B1 EVIDENCIA
- G1/G2/G3 formalmente CLOSED. `FaroV5WorkspaceSearchSurfaceAdapter` ya abre conversación/anchor con emisión de hits, invalidación y stamp real. No replicarlo ni modificarlo.
- `FaroV5FinanceSearchReadSource` B7 6/6 y `resolveAuthorizedEntryId` ya existen, pero son fuente no montada. Sus lecturas son Finance de `RealityStore.financePersistence`, nunca índice.
- `RealityStore.objectivePersistence` y `matterPersistence` ahora son propietarios reales de filas/revisión bajo transacción única (B7 8/8 +13 regresiones, SHA `0fab63a04de055e3479b33c6d38e1072646f42e3`). Dos API owner JVM poseen `id/title/detail/status/timestamp`. Nunca usar MatterAttentionReadProjection como dueño.
- `FederatedCanonicalSearchCoordinator` ya existe, round-robin acotado y comparación completa de stamps por cada fuente; NO escribir otro federador desde cero. En el variant V2CoreDev el pointer/replayFence y readAdmission son reales, pero Search actualmente conecta solo Workspace: hay que registrar Finance/Objective/Matter con permisos de lectura independientes y revocables.
- `FaroV5SearchScreen` solo muestra resultados y navega a Chat. Una fila de Finanzas/Objetivos/Asuntos no puede abrirse haciéndola pasar por conversación. `SearchCanonicalDocument.Kind` carece de OBJECTIVE.

## B2 COMPARACIÓN
- V4.4 global Search tenía varios dominios con identidad/tipo diferentes, pero no trasladar sus repositorios in-memory ni las ACL de V4.4. En V5, Finance/Objetivos/Asuntos son autoridad persistente Reality; los hits son locators tipados, nunca autorización de acceso.
- Reusar `FederatedCanonicalSearchCoordinator` para cobertura por dueño, `FaroV5WorkspaceSearchSurfaceAdapter` para navegación de Chat, y el host real V2CoreDev para emisión de grants de sesión exclusivos por dominio. No habilitar shipping ni compartir registros con V1/V4.4.
- La navegación de dominio sólo mostrará **detalle read-only autorizado dentro de Buscar**, y nunca ejecutará Apply, selección de Chat ni mutación. Mantener el único menú lateral existente.

## B3 INVESTIGACIÓN Y ELECCIÓN
- Nextcloud Unified Search https://docs.nextcloud.com/server/stable/developer_manual/digging_deeper/search.html : proveedores registrados explícitamente, resultados parciales por fuente y privacidad por defecto. **ADAPTAR** source namespaces, cobertura parcial e invalidez por owner; DESCARTAR API remota y sus ACL de servidor.
- Android AppSearch https://developer.android.com/develop/ui/views/search/appsearch : índice offline potente con español, no constituye el owner Reality ni controla revocación. **DESCARTAR** índice adicional en esta unidad; usar scan canónico acotado 500 y marcar PARTIAL cuando excede.
- Android Navigation https://developer.android.com/guide/navigation : destinos tipados; **ADAPTAR** destino de dominio con revalidación y pantalla de lectura dentro de Buscar, no agregar navegación paralela ni dependencia nueva.
- Alternativa A **ELEGIDA**: crear fuentes canónicas independientes para Objetivos y Asuntos, reusar Finance y federador existente, una barrera de publicación con issuanceId + stamp FULL y resolver fresco, binding sólo al DEV V2 con grants independientes. Alternativa B DESCARTADA: usar Workspace como fuente única para objetos financieros y asuntos. Alternativa C DESCARTADA: tratar un hit de índice como permiso o devolver COMPLETE ante owner caído. Alternativa D DESCARTADA: UI de edición/transacción desde resultado de búsqueda sin revisión.
- Código original FARO, no copiar fuentes externas, sin APIs pagas/servicios de red ni nuevas dependencias, sin afectar backup.

## B4 PLAN UNIFICADO / EXACT PATH SET
1. `v5/control/src/main/java/ar/com/faro/v5/control/search/SearchCanonicalDocument.java`: Kind.OBJECTIVE; conservar otros Kind.
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ObjectivesSearchReadSource.kt` y `FaroV5MattersSearchReadSource.kt`: owner reads desde puertos Reality; namespace único; ID opaco SHA-256, bounded scan 500; authoritative generation+ACL+revision, revocation/regrant fail closed, resolver de ID sólo tras `expectedAuthority`.
3. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5FederatedSearchSurfaceAdapter.kt`: reusar federador para cuatro fuentes y el adaptador Workspace existente para emisión/navegación de Chat; revalidar autoridad completa y emisión por fuente antes y después de abrir; fallback PARTIAL/UNAVAILABLE, sin filtrar datos de fuente fallida. Target de dominio sólo después de fresh canonical reread.
4. `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt`: conectar cuatro propietarios verificados en DEV (grants session-scoped separadas para Reality Finance/Objetivos/Asuntos) y cerrar/revocar en Dispose; app shipping permanece sin Search nueva. Sin segunda instancia de Reality.
5. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5SearchSurface.kt`: detalle tipado READ_ONLY en Buscar tras CONFIRMED, sin reusar callback de Chat para dominio, borrar estado al invalidar.
6. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ObjectivesSearchReadSourceTest.kt`, `FaroV5MattersSearchReadSourceTest.kt`, `FaroV5FederatedSearchSurfaceAdapterTest.kt`: casos lectura canon, límites, partial, ACL, ABA, owner cerrado, emisión falsa, query intercalada, destino por tipo y fallo aislado.
7. `.github/workflows/faro-v5-g4-federation-directed.yml`: XML JVM exacto y `:app:compileV2CoreDevKotlin` más manifest, sin activación ni físicos.
Total 10 rutas; no modificar código G1–G3, V2 shipping, FBS1, esquemas, catálogo, main, V4.4, S4 o Auto/Noche.

## B5 AUDITORÍA ADVERSARIAL
- Antes de mostrar cada título comprobar stamp generación/ACL/revisión, texto solo de owner real, no de índice; duplicados/namespace ajeno, 501 documentos y owner caído producen PARTIAL/UNAVAILABLE, nunca false COMPLETE.
- Si una fuente se revoca durante búsqueda, no presentar hits antiguos de esa fuente; conservar hits autorizados de otras, con cobertura por owner. Regrant nuevo epoch impide ABA, invalidación borra emisión de páginas anteriores.
- Un hit inventado/copiadо o de página anterior no puede abrirse. Resolver de dominio relee la entidad y bloquea ante cambio de título/revisión/ACL/puntero; la UI no puede aceptar datos ni IDs arbitrarios, sólo muestra target devuelto por puerto y no ejecuta Apply.
- Workspace debe seguir usando su adapter probado para `CONFIRMED`; un cambio de revision legítimo durante selección no debe forzar falso STALE en el federador; no reabrir G3. Pantalla de dominio mantiene NO_EDITION/NO_CROSS_AUTHORITY.
- No hay CI física. DEV V2 aislado puede leer datos de su propio Reality; no autoriza V2 productiva ni garantiza portable backup para filas nuevas. Pruebas de host multi-process no aprobadas.
- Dictamen **READY_BOUNDED_G4_FEDERATION_TYPED_READ_ONLY_DEV**. G4 global sólo podrá cerrarse con CI dirigida y B7 de comportamiento/permisos; NO declarar datos portables G4 ni prueba Android física.
