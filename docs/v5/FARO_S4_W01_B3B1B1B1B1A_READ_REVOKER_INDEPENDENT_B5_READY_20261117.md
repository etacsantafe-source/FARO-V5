# FARO V5 · S4 W01 B3B1B1B1B1A — B5 independiente y alcance READY del revocador READ SQLite9
Fecha: 2026-11-17 (Argentina). Baseline `4766c6c250cc948b125353f446a7d6eeffa0bf0c`; guard B1–B5 `44120244115` COMPLETED SUCCESS exact SHA. Auditoría independiente de la preauditoría `FARO_S4_W01_B3B1B1B1B1_CROSS_PROCESS_REVOKE_LIFECYCLE_B1_B5_20261117.md`. Modo documental; este archivo no incorpora cambios de código, permisos productivos ni pruebas ejecutadas. El permiso anterior B3B1B1B1B0 está BLOCKED/consumido.

## B1 — Evidencia revalidada y límites observables
- `RealityFinance9Acl.kt` blob `0b7effb6d5d75c9cc511f957c34cd6ecd8ecf467`: `revoke(key)` serializa `BEGIN IMMEDIATE`, escribe `revoked=1`, incrementa policy epoch y global revision, COMMIT o ROLLBACK. Ausente retorna `null`; revocación repetida retorna **el mismo epoch anterior** sin incrementos. Esa respuesta no prueba «cambio nuevo».
- `RealityStore.kt` blob `bbdeeaf3432614faeff74d1314e208bd5d676910`: el emisor READ usa `withMaterialAdmission`, `synchronized(lock)`, exclusión `activeTransactionId` y schema9 explícito; debe aplicarse la misma cerca al nuevo revocador, sin tocar el emisor ni el grant Apply.
- `RealityFinance9TrustedReadPolicyIssuerPort.kt` blob `5e960ba6d17cd715f261b45e7f4e5d1f941a0cc5` fija estructura de principal SHA256 lowercase hex64, generación saneada, pointerSequence>0 y TTL máximo 30 s. No hay prueba biométrica real en el issuer SQL.
- `RealityFinance9AclCrossProcessTest.kt` blob `4bb4c362bf0681962d4c3f91470921e34007b3e1` y `RealityFinance9AclProcessWorker.kt` blob `9daf54909c9ef6a601c6101a8d35032760349bcd` prueban dos OS PIDs con SQLite; **su scope histórico es Apply y no se atribuye como certificación del nuevo READ revoker**.
- `RealityFinance9ObservedSourceReadPort` devuelve snapshot WAL y no una lease permanente de render. `FaroV5FinanceReadSurface` y callbacks de puntero existentes no cubren revocación Android remota; ningún wiring shipping por esta unidad.

## B2 — Reutilización y exclusiones
USAR `RealityFinance9Acl.revoke` intacto; SQL staged9 owner y contador durable; fence de pointer y epochs de READ ya B7 previos; worker dos PIDs como infraestructura dirigida. No modificar `RealityFinance9Acl.kt`, DDL/migraciones, Finance ledger, readers, issuer, FBS2v4, V4.4, main, S2, S3, W2, Apply/receipts, Compose, manifest, ContentProvider, Activity ni V2 shipping. No repetir 8/8 JVM y 5/5 CI de B3B1B1B1B0.

## B3 — Investigación independiente y decisión
A — **ADAPTAR** revocación SQL existente a un puerto fuertemente tipado y con scope constante `finance.snapshot.read/finance:all`: controla serialización interproceso por `BEGIN IMMEDIATE`, no pide permiso a UI y no publica notificaciones. SQLite documenta que `BEGIN IMMEDIATE` puede dar SQLITE_BUSY si otro escritor tiene la reserva; debe ser fallo cerrado (https://www.sqlite.org/lang_transaction.html).
B — **DESCARTAR** status de «nuevo revoke» deducido por un par de lecturas de revision antes/después: un segundo PID puede alterar esos contadores entre ambas; `revoke` hoy retorna epoch histórico en repetidos. Ningún evento post-COMMIT ni «changed=true» sin evidencia atómica.
C — **DIFERIR** `ContentResolver.notifyChange`/ContentObserver hasta B3B1B1B1B1B, una vez cerrados TODOS los escritores autorizados; Android O requiere ContentProvider válido para el URI notificado (https://developer.android.com/reference/android/content/ContentResolver). Una escritura directa Python/SQLite no emite esa señal.
D — **DESCARTAR** asumir que un SELECT WAL cubre una revocación posterior: SQLite WAL conserva snapshots previos aunque otro proceso confirme un cambio; revalidar al mostrar (https://www.sqlite.org/isolation.html).
No dependencia extra, sin API paga, offline.

## B4 — Unidad EXACT4 propuesta para el permiso
Unidad `S4_W01_B3B1B1B1B1A_SQL_READ_REVOCATION_OWNER_DEV`, SOLO estas rutas:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyRevocationPort.kt` (NUEVO): request tipada `principalHash`, `generationId`, `pointerSequence` exclusivamente. Contrato revocation after trusted-stage admission, **no recibe** operationId/objectScope, no credencial humana, cuentas, epoch arbitrario ni Apply.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: factory `finance9TrustedReadPolicyRevokerForDevOnly` solo `STAGED9` con store abierto, `withMaterialAdmission`, lock, sin Apply activa. Construye scope READ fijo, llama la ACL `revoke` sin duplicar SQL, retorna `null` para ausencia/fallo. Un retorno no-null sólo significa **fila observada revocada tras COMMIT**, incluso en idempotencia: no prometer transición nueva, no emitirse evento ni contador inferido. No toca saldo.
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyRevocationTest.kt`: pruebas R01–R08 exactas en SQLite9 real, incluido worker SQL segundo PID y los bordes siguientes.
4. `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1a-read-revoke-directed.yml`: JUnit dirigido, XML con nombres y cifras exactas, regresiones READ afectadas y comprobación de ausencia de wiring shipping.
Emisión de permiso en commit documental separado, B6 funcional únicamente con preflight de cuatro rutas y writer CAS. No mezclar permiso y código.

## B5 — Auditoría adversarial independiente, negativos obligatorios
R01 schema7/schema8/closed/active Apply rechazan sin mutación ni escape de respuesta; R02 scope READ fijo y cero grants/receipts/ledger; R03 ausencia -> null, repetida -> mismo epoch **sin avance** de contador; R04 foreign principal/generation/pointer no invalida otro; R05 revoke-reissue-ABA: old epoch inválido; R06 expiración de READ no reautoriza lectura ni un revoke; R07 dos PIDs reales y `BEGIN IMMEDIATE`: revocación externa antes/después de lectura, persistencia y bloqueo; R08 error SQLite/rollback/fallo de lock y request inválida no provocan falso éxito ni cambios parciales. Usar infraestructura previa, pero pruebas **nuevas** para scope `finance.snapshot.read`; no repetir suite histórica Apply.
- **Hallazgo nuevo:** la función ACL retorna el epoch ya revocado en reintentos. El nuevo puerto no debe nombrar su resultado como `newRevocation`, `changed` o `notify`: exigir semántica `observedRevokedPolicyEpoch`, sin promesa de evento. En B3B1B1B1B1B, el publisher precisará una decisión separada que sólo anuncie transiciones realmente committeadas.
- Una revocación entre el último WAL SELECT y Compose render sigue siendo posible; **esta unidad no hace invisible una cuenta ya mostrada**. Android publisher, lifecycle STOP/RESUME, TTL y G15 son gates posteriores, no PASS actual.
- Cualquier request DEV puede ser falsificada por código del UID; sin prueba de identidad humana ni autorización shipping. No exponerla a Chat o Surface. Si el test no puede demostrar dos PIDs nuevos con ACL READ, B7 debe ser RED_FIX, no PASS.
**DICTAMEN B5:** `PASS_SCOPED_PRECODE_ONLY` para **revocador SQLite READ DEV EXACT4** con resultado observacional sin bandera changed, sujeto a preflight/guard y B6/B7 exactos. **RECHAZADO** para Android notifyChange, lifecycle host, sesión UI, biometría física o V2 productiva.

## B6
`READY_TO_IMPLEMENT` solo después del permiso individual exact4 aprobado documentalmente y preflight PASS. En este commit: NOT_RUN, cero código y cero nuevos tests.

## B7
`PENDING_FINAL_AUDIT` hasta diff 4/4, pruebas R01–R08 en XML real, dos PIDs confirmados, workflow y guard SUCCESS del SHA de código, revisión de no product wiring, checkpoint y writer RELEASED. W1 OPEN, W2 STOP, V2 productiva OFF; G15 NOT_RUN.
