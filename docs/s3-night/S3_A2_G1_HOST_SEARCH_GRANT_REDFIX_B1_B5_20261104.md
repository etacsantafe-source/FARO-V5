# FARO V5 — S3.A2 G1 Search grant: B7 RED_FIX + B1–B5 de la misma unidad (04/11/2026 AR)

**Unidad sin cambiar:** `S3_A2_G1_HOST_SCOPED_SEARCH_GRANT`.
**Código original:** `e72b6471a50d9d9482dcfcf0040781d94efed3ee`. **Permiso original:** `4ac4dc1e196acba70141196c4f5a56db06af2453`.
**V2 productiva OFF, S2 8/8 cerrado, S4/Auto/Noche OFF.**

## B7 AUDITORÍA FINAL DEL PRIMER CÓDIGO — RED_FIX, NO PASS
**CI dirigida verde pero B7 RED_FIX por defecto de revocación de sesión tras recuperación A→B→A.**
- Protocol guard `#41950439595` SUCCESS.
- S3 Home/Chat/Search `#41950439570` SUCCESS, XML 36/36: Home 9, Workspace Search 7, Bridge 5, Avisos 4, Chat pointer ABA 6, active host ABA 1, Search host grant 4.
- V2 core `#41950439621` SUCCESS, DEV Search 10/10, T4 7/7 y aislamiento del manifest.
- APK DEV `#41950439584` SUCCESS, paquete aislado `ar.com.faro.alpha.v2coredev`, firma v2 verificada, sin permisos OS prohibidos; SHA256 `d82eb6a397def8e4a752a10bbcedaf8fe17d9a25505bbc53848f34570cc75da8`. No instalación física ni V2 shipping.
- **Hallazgo estático, no afirmar ejecución fallida:** `FaroV5ActiveWorkspaceSearchGrantOwner.epochOrNull()` devuelve null si `hostAdmitted=false` pero conserva el grant anterior. Si el host vuelve a `true` (maintenanceRecoveryComplete falso→verdadero con generación y secuencia sin cambios), `epochOrNull()` podría devolver el MISMO `aclEpoch`; un hit publicado sin subscriber de invalidaciones podría recuperar autoridad. `FaroV5CompositionRoot` solo suscribe cambio de puntero, no transición a readiness no-lista. Un CI verde no cubre este ABA de readiness. El riesgo hoy está en una rama de código con V2 productiva OFF.
- **Decisión:** NO certificar B7 del SHA `e72b6471`; hacer un `RED_FIX` de esta MISMA unidad, sin abrir G2 ni cambiar esquema.

## B1 EVIDENCIA
- B1–B5 originales en `docs/s3-night/S3_A2_G1_HOST_SEARCH_GRANT_B1_B5_20261104.md` y permiso independiente sobre cuatro rutas; reutilizar su comparación V4.4, OWASP, Element X y Kotlin StateFlow, no repetir.
- La condición de admisión actual del root verifica `maintenanceRecoveryComplete` y `pinnedChatPointerStillCurrent`, pero no `startupReadinessGate.snapshot().isLocalReady()`. `FaroV5StartupReadinessGate` es el owner auténtico de transiciones: `beginRecovery()` y `maintenanceOnly()` publican un snapshot no-listo y notifican a listeners; `localReady()` lo reabre. `DeliveryGraph.close()` ya libera servicios y stores.
- En `FaroV5WorkspaceSearchSurfaceAdapter.openAuthorized()` sello `SearchAuthorityStamp` incluye grant epoch; un old epoch que reaparezca tras recuperación podría restaurar un locator si no hubo cambio de publicación/observer. No es autorización de escritura, pero no exponer títulos ni selección stale.

## B2 COMPARACIÓN Y REUTILIZACIÓN
- V4.4 mantiene owners canónicos independientes; no copiar código/DB ni sustituir guardia actual de Workspace. Esta subunidad reutiliza `FaroV5StartupReadinessGate`, su `addListener/removeListener`, `FaroV5ActiveWorkspaceSearchGrantOwner` y `FileActiveGenerationPointer`.
- Patrón ya demostrado en V5 para Home: suscribir listeners reales, reconciliar snapshot tras suscripción y releer autoridad. NO crear otra fuente de readiness, no inventar epoch por reloj ni guardar token en FBS2.

## B3 INVESTIGACIÓN EXTERNA Y DECISIÓN
- OWASP Authorization Cheat Sheet (https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): deny-by-default y reautorizar en cada acceso. **USAR**: una pérdida de admisión debe cancelar, no suspender, el grant.
- Kotlin StateFlow (https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/): actualización observable y coalescing; un Flow de avisos no equivale a ACL. **ADAPTAR**: listener del owner + snapshot actual, con comprobación fuente de verdad en `epochOrNull()`.
- Element X Android PR #7877 (https://github.com/element-hq/element-x-android/pull/7877) es fallo real de sesión equivocada al revocar otra cuenta; **REFERENCIA** para pruebas A/B de scopes separados, no copiar implementación.
- **DESCARTAR**: cambiar oráculo a 4/4 sin nueva prueba, reiniciar V2 productiva para reproducir, "esperar al próximo hit" para revocar, un boolean UI o ACL persistente sin contrato de restore.

## B4 UNIFICACIÓN DELTA MÍNIMO
**Solo cuatro rutas que ya estaban en el permiso original:**
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveWorkspaceSearchGrantOwner.kt`: cuando admisión deviene false durante `epochOrNull`, revocar grant + emitir cambio de epoch; nuevo `grantAfterVerifiedHostOpen()` explícito requerido. Validar también el segundo check dentro del lock.
2. `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java`: añadir `startupReadinessGate.snapshot().isLocalReady()` al predicado y un listener real que revoca en cualquier transición no-lista, suscrito ANTES de grant y limpiado en `DeliveryGraph.close()`. Reconciliar tras suscripción para cerrar carrera.
3. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveWorkspaceSearchGrantOwnerTest.kt`: dos tests negativos adicionales: pérdida de admisión observada no revive grant; readiness false→true sin acceso ni subscriber a Search no revive grant anterior, usando `FaroV5StartupReadinessGate` real. No borrar 4 anteriores.
4. `.github/workflows/faro-v5-s3-home-notice-directed.yml`: oráculo XML nuevo **6/6** exactos, conservar todos los demás.
- **No tocar** `main`, V4.4, FBS2, esquema, AndroidManifest, UI/Bootstrap Search `null`, gates productivos ni APIs.

## B5 AUDITORÍA ADVERSARIAL PREVIA
- No aceitar permisos antiguos tras readiness A→B→A; el listener debe funcionar aun cuando UI no recolecta invalidaciones. Fallo al observar `hostAdmitted` en cualquier read suspende definitivamente el grant hasta nueva concesión explícita con nueva identidad.
- Listeners: registrar primero, emitir reconcile snapshot tras registrar, y remover en cierre/excepción. `grant.revoke()` seguro aunque no haya grant activo; no hay supuesta promesa cross-process.
- Verificar XML 6/6 para nueva suite más 32 tests históricos = **38/38**, protocolo y builds V2 aislados. Si RED, STOP y corregir misma unidad.
- En producción `FaroV5HostCertification.V2_ACTIVE_ENABLED=false` se mantiene; ningún grant entregado a SurfaceHost.
- **B5:** READY_FOR_SAME_UNIT_REDFIX_ONLY, sujeto a permiso independiente `READY` y preflight, writer CAS, B6, CI, B7 final separado. G1 formal **OPEN** hasta cerrar cross-process/restore y política de acceso shipping.

**Métricas:** S3 ≈40% desarrollo estimado sin inventar progreso, 0/5 subetapas formal CLOSED; S3.A2 0/5 gates formal. Tiempo efectivo NO_MEDIDO.
