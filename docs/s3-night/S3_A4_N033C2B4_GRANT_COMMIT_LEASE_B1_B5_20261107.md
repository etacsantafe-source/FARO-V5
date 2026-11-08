# FARO V5 S3.A4 · N033C2B4 GRANT_COMMIT_LEASE · B1–B5
Fecha local 2026-11-07 AR. Base a96bae33f6f5cec831f90495352db411c95e5f0c. Alcance exclusivo: propietario interno de grants, NO ACL productiva ni composición Android. Preauditoría nueva S3_A4_N033C2B4_GRANT_COMMIT_LEASE. Autoridad de estado: puntero docs/v5/FARO_CONTINUITY_POINTER.json y último B7 kernel 13/13.

## B1 — Evidencia y brecha concreta
- S3.A4 transaccional ACL B1–B5 del commit a96bae33f6f5cec831f90495352db411c95e5f0c terminó STOP, identificando carrera entre la última lectura de ACL y COMMIT SQLite. El último código verificado es 66d581ff, kernel:apply 13/13, workflow #42427565628 y guard #42427565595; no reejecutar sin cambio.
- v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5Planning8ObjectGrantOwner.kt: FaroV5Planning8ObjectGrantOwner tiene monitor privado para grant/epoch/revoke/revokeAll/close. Hoy epochOrNull dura una llamada; no existe lease que mantenga el mismo monitor desde el check hasta el COMMIT del callback. Null policy => DENY; principal provisto por host sin autenticación humana.
- RealityStore.run(..) ya contiene BEGIN IMMEDIATE y COMMIT/ROLLBACK en el mismo callback, no coordina mutable policy fuera de Reality ni otro proceso. MaterialWriteGate usa read-lock compartido de escrituras; no equivale a revocation fence. No modificarlo.
- B6 éxito medible: nueva llamada opcional, de alcance exacto (scope + pointer + nonce), que retenga el monitor del grant durante callback completo, de modo que revoke/revokeAll/close de ESTE owner no se confirmen hasta el fin de callback. Retornar DENIED tipado antes del callback si falta ACL, puntero, nonce, policy o host. No persistir grants; no emitir permisos por sí sola.

## B2 — Comparación V4.4 y reutilización
- V4.4 distingue recordatorios y plan mensual por owner sin prueba de ACL multiusuario. No copiar ni migrar V4.4 ni datos de pruebas.
- En V5 reutilizar monitor sincronizado de grant, no crear lock global, nueva DB/tabla/epoch ni modo worker, conservar el nonce revocable y la policy externa obligatoria. La función queda en archivo existente y sus tests; no tocar kernel executor ya B7 PASS.
- No cambiar el contrato visible de Preview gate, ni publicarlo como autorización productiva.

## B3 — Investigación externa y decisiones
- Kotlin shared mutable state https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html : sincronización mutua para proteger grant + operación; ADAPTAR el monitor existente, sin biblioteca adicional.
- Java synchronized https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html : exclusión de monitor por llamada, reentrante y bloqueo de revoke; USAR como recurso *single-process* ya adoptado.
- OWASP Authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html : fail-closed si falta comprobación; ADAPTAR resultado DENIED tipado, sin decisión desde UI.
- SQLite transactions https://www.sqlite.org/lang_transaction.html : SQL COMMIT dentro de callback sigue siendo responsabilidad del host; REFERENCIA, no inferir atomicidad de ACL fuera del monitor.
- Alternativa lock global / tablas persistidas / login cloud DESCARTAR por duplicación y scope. CALLBACK genérico sin scope/nonce y boolean cache DESCARTAR, permite reutilización accidental. Cross-process sigue fuera de alcance: requerirá evidencia de uniproceso y control de todos los escritores.

## B4 — Implementación limitada
1. Agregar tipo interno sellado `Planning8GrantLeaseResult<T>` con `Admitted(value)` y `Denied`. Aceptar callback únicamente para un `scope`, puntero exacto y `expectedNonce`. El callback NO se invoca cuando el grant falta o está vencido.
2. `withCurrentGrantLease(scope, pointer, expectedNonce, action)` entra al mismo monitor ya usado por `revoke` y llama `epochOrNull` bajo lock. No se consulta ni crea grant por entrada; solo admitir uno previamente emitido con política real. Un `revoke` concurrente espera a que salga el callback, que debe incluir COMMIT/ROLLBACK. La excepción se propaga y desbloquea; no simular éxito.
3. Tests dirigidos: deny sin grant, nonce incorrecto y scope distinto; allow tiene valor correcto; revoke desde otro thread permanece bloqueado durante callback y se confirma al salir; excepción libera monitor para revocar. Tests capturan estado de owner; no afirmar prueba SQLite real ni cross-process.
4. Workflow dirigido con :app:testDebugUnitTest cuatro suites anteriores (14 owner +14 gate +10 search +2 flow), nueva suite owner 18/18 y total 44/44; valida XML exacto y guard independiente. No ejecutar G15 ni firmas.

## B5 — Auditoría adversarial y permiso de alcance
- Riesgo de deadlock: si callback adquiere Reality mutex bajo monitor de grants, ningún camino inverso Reality→grant debe hacerse desde otro hilo mientras sostiene Reality; ejecución host futura debe demostrar el orden, evitar callback que se autobloquee. Lease reentrante sólo con el mismo thread; no usar callback suspend/coroutine. No introducir bloquear UI.
- Riesgo mutable policy externa: `policyEpoch` puede mutar sin tomar monitor; ESTE lease linealiza sólo `revoke/revokeAll/close` del owner y NO constituye ACL completa ni protección contra cambio externo cross-process o pointer swap. No reclamar ventana TOCTOU cerrada globalmente.
- No autorizar conexión Shipping/Planning, no cambiar V2_ACTIVE_ENABLED, main/V4.4, S2, S4, Auto/Noche, catálogo 172, ni secretos. CI exacta y B7 scoped obligatorios.
- B5: READY solamente para S3_A4_N033C2B4_GRANT_COMMIT_LEASE con los tres paths explícitos; la unidad superior `S3_A4_N033C2B4_TRANSACTIONAL_PLANNING_ACL` permanece STOP hasta que Review/identidad/política legítima y orden de locks se integren y certifiquen.
