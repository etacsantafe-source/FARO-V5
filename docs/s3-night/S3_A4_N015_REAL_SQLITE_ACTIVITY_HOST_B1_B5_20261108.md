# FARO V5 — S3.A4 N015 Activity real Reality+Workspace host to Surface · B1–B5
Fecha Argentina 2026-11-08. Unidad `S3_A4_N015_REAL_SQLITE_ACTIVITY_HOST_BRIDGE`, baseline `fd07886bd7efd5f6e636ef57da2159e26bb96a9c`, writer epoch564. V2 shipping/S4/Noche/Auto OFF, S3.A4 OPEN.

## B1 — Brecha y acceptance
- Ya hay `RealityStore.timedReceiptHistory`, `ConversationWorkspaceStore.readHistoryPage(query)`, fuente federada `FaroV5FederatedHistoryReadSource` con 6/6 B7 y `FaroV5ActivityScreen` 20/20+Surface68/68 B7. **No hay composición real** Reality+Workspace→fuente→`FaroV5ActivityReadPort`; la pantalla por ahora ofrece null y "No disponible".
- Esta unidad entrega (a) composición interna JVM que usa los dos **canónicos SQLite** y exige policy read grant independiente por source, pointer ID+sequence, host lifecycle epoch y principal técnico; (b) traducción tipada exacta al DTO neutro Activity, sin payload/texto; (c) forwarding opcional del puerto Activity por SurfaceHost→AdaptiveShell→ShellScaffold. NO habilita CompositionRoot ni instala policy productiva.
- `Planning8TrustedDueReadPolicyPort` y `FaroV5ActiveWorkspaceSearchGrantOwner` son capacidades DIFERENTES; no aprovechar sus nonces. Definir policy abstracta Activity source-scoped y `readPolicy=null` => DENY sin consultar SQL.
- Verificar seis nuevos tests JVM app con SQLite Workspace y Reality reales; source6 y Surface model5 regresiones solo si el workflow las incluye; guard seven-block exact SHA. Error/cancel/revoke/ABA => null, nunca fake zero. El puerto Activity es de lectura, no Apply ni UI egress.

## B2 — Reuso y comparación V4.4
- `FaroV4FollowUpGateway` referencia contrato UI de Seguimiento sin prueba de ACL heredable; no copiar código V4 ni importar datos.
- `FaroV5FederatedHistoryReadSource` ya ordena cronología, aplica cursor+epoch y verifica dos owners. Reusar INTACTO; adaptador a `FaroV5ActivityPage` solo mapea source enum, 3 event kinds y time provenance exacto, fail closed en cualquier evento desconocido.
- `RealityStore` y `ConversationWorkspaceStore` ya poseen ports de lectura públicos, no tocar SQL/schema/FBS2, no replicar caché; fuente de verdad permanece en cinco stores.
- `FaroV5SurfaceHost.install` y `FaroV5AdaptiveShell` ofrecen opcionales Home/Search/Avisos; extender **solo** puerto Activity opcional por defecto null y en key(...), sin cambiar navegación ni UI previo.

## B3 — Comparación externa, criterio seleccionado
- OWASP authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html: explicit per-source deny-by-default, ACL no derivada de pantalla. USAR `epochOrNull` distinto para Reality y Workspace, comprobado antes/después de cada read.
- SQLite isolation https://www.sqlite.org/isolation.html: dos store SQLite no garantizan snapshot causal global. MANTENER `globallyComplete=false`; no reconstruir total de FARO de la federación parcial.
- Android UI layer https://developer.android.com/topic/architecture/ui-layer: UI state observa Flow invalidations y datos autorizados; PASAR port y eventos tipados, no consultar la DB desde Compose.
- Kotlin cancellation https://kotlinlang.org/docs/cancellation-and-timeouts.html: conservar `CancellationException`; no capturar cancel como empty.
- Alternativa A USAR host dormant con fuentes reales + ACL separadas + DTO tipado; B DESCARTAR permitir global History por Workspace Search grant; C DESCARTAR activar V2 o fabricar persona con UID; D DESCARTAR consultar backup/auxiliary para fabricar Activity, E DESCARTAR copiar la lógica de cursor ya certificada. Sin API ni licencias adicionales.

## B4 — Delta máximo, CI
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5RealHistoryActivityHostBinding.kt`: internal host `FaroV5ActivityReadPort`, admite `RealityStore`, `ConversationWorkspaceStore` auténticos; pin+host epoch+source ACL independiente y `invalidations` provista por host. Mapea 3 event kinds y provenance exacta; null default DENY. No activa producción.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5RealHistoryActivityHostBindingT2Test.kt`: seis JUnit real Workspace SQLite y Reality receipt SQL (no mocks de store); revocación en query, falta policy, pointer ABA, close, datos provistos y no writes.
3. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5AdaptiveShell.kt`: param opcional Activity, incluye en `key` y pasa a `FaroV5ShellScaffold`, preserva "dispose old host" regla.
4. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5SurfaceHost.kt`: `install(activityReadPort: FaroV5ActivityReadPort?=null)` al final, forward; Java @JvmOverloads conserva llamada existente.
5. `.github/workflows/faro-v5-s3-a4-n015-real-host-directed.yml`: compilar app/Surface y ejecutar los 6 nuevos, Activity Surface 5 y federated source 6, exigir XML 17/17 + regression shell, guard obligatorio exact SHA.
- NO tocar `FaroV5CompositionRoot`, launcher, V2CoreDev, `FaroV5HostCertification`, Realm schema, source B7, Avisos, S2, V4.4, main, catálogo 172. Nuevo B7 propio.

## B5 — Auditoría adversarial/STOP
- Un actor técnico `principalId` inyectado NO autentica al humano: política de ownership real aún inexistente. Sin `readPolicy` DENY. Fixture policy en pruebas NO implica autorización productiva; no reclamar shipping. Fuerza rechazo de cualquier source/eventKind/provenance incompatible, evita texto y UUID en pantalla, no mapear FARO_PARTIAL como COMPLETED.
- `invalidations: Flow<Unit>` provisto por host debe emitir ante revoke, recovery, pointer switch, owner read access; no existe implementación productiva, por lo que no emitir resúmenes reales en app. La pantalla igual muestra "No disponible" en shipping.
- Versiones V1 sin Workspace no satisfacen federación real, jamás inferir vacío Workspace. Si Workspace/Reality cerrado, policy epoch revocado, puntero A→B→A, host epoch roto: fail closed null y sin nueva lectura autorizada. No usar `Receipt` como autorización.
- UI solo primeros 50 eventos y `completeWithinSources` por source; *no* inventar navegación/offset a eventos no presentes, ni decir "history.query global". No mutaciones, no auto-read, no OS.
- No cierres A4 ni habilitación V2 hasta auténtica autoridad de lectura, Compose montaje real y pruebas integradas. G15 físico A5 NOT_RUN. B7 exige CI exact SHA y conservar B1–B5 previos; writer RELEASED.
