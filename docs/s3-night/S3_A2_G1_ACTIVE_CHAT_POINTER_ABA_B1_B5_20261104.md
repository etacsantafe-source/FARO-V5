# FARO V5 — S3.A2-G1 · ACTIVE_CHAT_READ_POINTER_ABA · B1–B5 (04/11/2026)

**Unidad acotada:** `S3_A2_G1_ACTIVE_CHAT_READ_POINTER_ABA`; **HEAD inspeccionado:** `ca741880922cec59e7295b0aa03e35b3e173524b`. **Objetivo:** cerrar solo la brecha de lectura activa con puntero A→B→A, sin atribuir una ACL de producción al permiso de DEV. V2 shipping OFF, S2 8/8 congelado, S4/Auto/Noche OFF.

## B1 EVIDENCIA
- `FaroV5ActiveChatReadSession.kt` revalida `startupReady && activeGenerationId==generationId` antes/después de leer Workspace y timeline; NO fija la secuencia durable del puntero.
- `FaroV5CompositionRoot.java` construye ese read session dentro de `deliveryGraph()` y reutiliza el grafo si `current.generationId.equals(activeGenerationId)`. Un CAS A→B→A entre llamadas puede devolver el mismo ID y mantener el grafo y sus puertos, aunque la secuencia durable sea distinta. `V2_ACTIVE_ENABLED=false` impide abrir el Workspace de producción ahora; NO cambiar ese veto.
- `FileActiveGenerationPointer.kt` ofrece `ActiveGenerationPointer(sequence,generationId)`, secuencia monotónica durable, CAS y eventos **solo mismo proceso**. Usar `read()` para comprobar identidad del puntero aunque no haya listeners de UI.
- `FaroV5ActiveChatReadSessionT2Test.kt` cubre cambio A→B, recovery false y cambio durante una lectura, pero NO A→B→A sin observadores. Este es un hueco específico de la sesión activa, no motivo para repetir los tests de Search DEV.
- **Evidencia reutilizada:** S3 DEV Search B7 PASS `c3f5e5d1`, CI core `#41933426446` 10/10 y S3 Home `#41933426482` 25/25; su propia corrección de ABA está en `docs/s3-night/S3_A2_DEV_SEARCH_SESSION_B7_PASS_20261104.md`. No inferir que protegió el host shipping.

## B2 COMPARACIÓN
- V4.4 `app-v4/src/main/kotlin/ar/com/faro/v4/conversation/FaroV4GlobalSearch.kt` @ `44f1e6eb...`: proveedores separados, degradación si owner indisponible, normalización y resultados accionables; no tiene puntero V5 ni secuencia de generación, por lo que NO copiar soluciones inexistentes.
- V5 ya tiene `ActiveGenerationPointer` durable y lectores antes/después. **Reutilizar mecanismo y pruebas negativas del DEV**, adaptar al composición de producción; no crear nuevo almacén, read ACL ni cambios de SQL/FBS2/backup.

## B3 INVESTIGACIÓN EXTERNA, ALTERNATIVAS Y EVIDENCIA
- **OWASP Authorization Cheat Sheet** (https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): denegar por defecto y comprobar acceso en cada operación; USAR comprobación estricta antes/después de Workspace/timeline.
- **Element X Android PR #7877** (https://github.com/element-hq/element-x-android/pull/7877), **MERGED**, descripción y diff verificados: un `RustClientSessionDelegate` compartido entre cuentas revocaba la cuenta equivocada; el cambio hace delegate **por cliente** y usa NoOp en clientes temporales. ADAPTAR el principio: identidad inmutable del owner por sesión, no derivarla de un ID actual reutilizable; no copiar código AGPL ni introducir dependencias.
- **Room 3 Builder** (https://developer.android.com/reference/androidx/room3/RoomDatabase.Builder): `enableMultiInstanceInvalidation()` requiere misma DB, ambas instancias opt-in y no es fuente ACL. DESCARTAR para identidad del puntero (no son cambios de tabla); costes extra sin beneficio.
- Alternativa A: solo `activeGenerationId` actual o callback UI; **DESCARTAR** por ABA y falta de listener.
- Alternativa B: agregar nueva tabla ACL/epoch persistido o reescribir FBS2; **DESCARTAR para este defecto acotado**, no evita reutilización del puntero sin añadir muchas piezas; licencias/dependencias/costo innecesarios.
- Alternativa C: **ADAPTAR/USAR** `ActiveGenerationPointer(sequence,generationId)` preexistente, fijar sello una vez por `DeliveryGraph` y revalidar con `FileActiveGenerationPointer.read()` en cada Chat read. Sin código externo ni nuevas librerías.

## B4 DISEÑO UNIFICADO
1. `FaroV5ActiveChatReadSession.kt`: extender el contrato con `BooleanSupplier hostReadAuthorityStillCurrent` (opcional de compatibilidad `@JvmOverloads` para hosts DEV/quarantine cuyo `ready` ya es distinto); verificarla ANTES y DESPUÉS junto con readiness + generation. No desplazar el owner del Workspace.
2. `FaroV5CompositionRoot.java`: cuando `stores.workspace!=null` (únicamente V2 activa **autorizada previamente**, que shipping aún prohíbe), capturar `ActiveGenerationPointer` real del root con su secuencia y generation. Suministrar supplier que solo acepte el MISMO puntero; atrapar fallos de lectura fail-closed. No activar V2 ni crear Workspace para V1.
3. `FaroV5ActiveChatReadSessionT2Test.kt`: dos pruebas sobre `FileActiveGenerationPointer` REAL con `@TempDir`: A→B→A sin subscription debe negar ambos ports; A→B→A durante una lectura debe fallar en segunda comprobación. Conservar 4 tests actuales.
4. `.github/workflows/faro-v5-s3-home-notice-directed.yml`: incluir clase nueva en oráculo JVM dirigido y exigir XML 6/6 además de Home 9/9, Workspace 7/7, Bridge 5/5, Avisos 4/4. El workflow se ejecuta por touch de `v5/app/**`.
5. No habilitar búsqueda shipping ni modificar `FaroV5HostCertification`, paquete/manifest/identidad de APK, fuentes de otros dominios, firma, root ni esquema. Un próximo owner ACL deberá construir sobre esta barrera.

## B5 AUDITORÍA ADVERSARIAL PREVIA
- Negativas obligatorias: A→B→A puede ocurrir SIN listener de Compose o entre `readSnapshot()` y su segunda verificación; puertos anteriores deben fallar permanentemente si su secuencia cambia. Si `pointer.read()` falla, retorno false, nunca conceder acceso por excepción ignorada.
- Riesgos: no romper llamadas Java al ctor Kotlin (usar `@JvmOverloads`), no crear ni abrir tablas/Workspace V2 al estar OFF, no imponer I/O en el camino V1 (punto fijado solo si `workspace!=null`). Preservar compatibilidad F1 quarantine, DEV y S2 (default sigue su propia admission).
- **No resolver por accidente** otros gates: el dueño ACL de grant/revoke independiente, invalidación observable entre procesos y selección de Search shipping siguen pendientes. No afirmar G1 PASS por este parche.
- B6 requiere READY documental separado con rutas concretas, preflight GitHub Actions y writer CAS. B7 requiere SHA + CI XML exacta; si rojo, RED_FIX mismo alcance, sin replantear toda S3.
- **B5 DISPOSITION: READY_BOUNDED_POINTER_SAFETY_ONLY**. Nuevo permiso puede habilitar únicamente esta unidad; cierre S3.A2 G1 = OPEN (0/5). No trabajo de fondo, tiempo NO_MEDIDO.
