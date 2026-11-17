# FARO V5 — W01 B3B1B1 · Adaptador de lectura Finance9: preauditoría B1–B5
Fecha Argentina: 2026-11-17. Unidad candidata `S4_W01_B3B1B1_FINANCE9_TRUSTED_APP_READ_ADAPTER_DEV`.
Modo DOC_ONLY / READ_ONLY de componentes existentes. NO READY para código en este checkpoint. W2 STOP.

## B1 — Evidencia exacta, sin repetir certificaciones
- HEAD base `de39726466f2fe5a590671b815ba4366a4349869`; writer RELEASED epoch 1074; permiso previo BLOCKED/consumido. Documento B7 previo: `docs/v5/FARO_S4_W01_B3B1B0_ATOMIC_SQL_SOURCE_B7_PASS_SCOPED_20261117.md`; code `ba6b83c39fdbfe2274fad64e103fe5d93a871bd2`; 8/8 pruebas nuevas + 16/16 regresiones afectadas, workflow `44087261313`, guard `44087261177` SUCCESS, 22/22 workflows previos. No repetir CI B3B0, B3B1A ni B3B1B0.
- `RealityFinance9ObservedSourceReadPort.observe(request)` expone UN resultado desde un único snapshot SQLite WAL de `finance.snapshot.read/finance:all` y provenance `RealityFinance9OpeningProvenanceSnapshot`. Sus argumentos (`principalHash`, `generationId`, `pointerSequence`, `expectedPolicyEpoch`, `observedAtMs`) son datos provistos por caller; NO autentican Android.
- `FaroV5FinanceReadPort` en `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5FinanceReadSurface.kt` requiere `suspend readFinance()` e `invalidations: Flow<Unit>`, borra snapshot al recibir evento, pero NO demuestra por sí mismo procedencia ni invalida por procesos remotos. B3A quedó dormant (B7 `f9c524cfc3b0ddda93d3def16371efbbcda9f933`).
- `S4W01FinanceAuthHost.kt` + `S4W01M1C1B1CTrustedGrantAdmission.kt` ofrecen presencia local real vía CryptoObject para **Apply** DEV; NO autorizan lectura implícita de `finance.snapshot.read`. G15 biometría de este nuevo recorrido NOT_RUN. `FaroV5FinanceSearchReadSource.kt` usa callbacks inyectables, nunca fuente de autoridad productiva.
- Falta una admisión de lectura host de confianza con principal técnico local/UID, puntero REAL y host epoch, y una invalidación cross-process verificable antes de exponer balances en Compose. NO puede asumirse de la fila `CONFIRMED_IN_SQL`.

## B2 — Comparación y reutilización V4.4/V5
Conservar de V4.4 sólo invariantes AtomicApply, PREPARED/COMMITTED, receipts, recuperación y Undo; ninguna credencial V4.4 se migra a permiso de lectura. Reutilizar B3A Surface, B3B0 provenance, B3B1A ACL y B3B1B0 lectura compuesta, `ActiveGenerationPointerPort` y el modelo de lifecycle de Home. No duplicar tablas, escritor, guard, permiso Apply, owner Finanzas ni consultas SQL. FBS2v4, schema7/8, catálogo180, main, V4.4, S2/S3 permanecen intactos.

## B3 — Investigación y alternativas, decisión específica
A. ADOPTAR DEV: módulo app que consume la lectura compuesta SOLO en host de desarrollo, con generación+secuencia+epoch fijados por owner confiable y doble comprobación de pointer+host antes y después del snapshot. Un callback o DTO fabricable es insuficiente. Los datos no se exportan a Chat, Intent ni Search.
B. REFERENCIA: SQLite WAL garantiza coherencia al primer SELECT dentro de una transacción, no vigencia eterna después de ROLLBACK: https://www.sqlite.org/isolation.html y https://www.sqlite.org/lang_transaction.html. No crear dos lecturas transaccionales independientes como si fueran una.
C. REFERENCIA/ADAPTAR: `android.os.FileObserver` puede observar escritura por otros procesos https://developer.android.com/reference/android/os/FileObserver; no es una garantía durable de entrega, por tanto jamás único fence. `Activity` ON_STOP/ON_RESUME https://developer.android.com/guide/components/activities/activity-lifecycle obliga a limpiar/revalidar datos al volver a foreground.
D. DESCARTAR: `admitted() = true`, `Flow<Unit>` inyectado desde UI como autoridad, tratar un grant de Apply como permiso read, mostrar saldo $0 en apertura desconocida, polling visual que no limpia datos y cache anterior a revocación. Sin librería nueva, red, API paga, ISO/IRAM ni envíos de dinero.

## B4 — Delta mínimo y contrato verificable
**B3B1B1A: mapper inerte, puro y testable, antes de host**. Candidato de código (requiere permiso individual posterior):
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B3B1Finance9UiMapper.kt` (NEW). Convertir *únicamente* `RealityFinance9ObservedSourceRead` ya obtenido por trusted owner a `FaroV5FinanceUiSnapshot`, con cobertura READY/EMPTY_CONFIRMED/PARTIAL, `OPENING_UNVERIFIED` sin importes y ARS/USD separados. Su resultado NO prueba autorización; no será montado en shipping, Activity, CompositionRoot ni Chat. Una entrada null devuelve `UNAVAILABLE` sin cuentas.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B3B1Finance9UiMapperTest.kt` (NEW). JUnit nominal 8 negativos/positivos, sin fingir biometría.
3. `.github/workflows/faro-v5-s4-w01-b3b1b1a-mapper-directed.yml` (NEW). Test dirigido `:app:testDebugUnitTest`, compilación Android DEV y XML, guard obligatorio; sin montaje UI.
**B3B1B1B: host/adapter real**, FUTURO y SEPARADO: wrapper `FaroV5FinanceReadPort`, fuente de principal local validada sin argumentos de UI, `pointer.read()`+UID/host epoch verificados, re-observación de ACL en un nuevo snapshot al reanudar, limpieza sin retención en revocación. Invalidation cross-process no puede descansar en `addChangeListener` intraproceso, `FileObserver` sin recuperación o falsos cambios. Requerirá nueva B1–B5, B5 independiente, paths exactos y autorización READY. B3B2 luego integra Android real y G15; V2 productiva OFF.

## B5 — Auditoría adversarial y decisión
T01: fuente null, schema incompatible, policy read ausente/revocada/expirada => sin saldos/IDs; T02: `CONFIRMED_IN_SQL` sintético no equivale a autorización humana; T03: apertura LEGACY_UNVERIFIED/MISSING conserva `null`, nunca 0; T04: cuentas ARS/USD verificadas y negativo se preservan sin FX; T05: moneda no admitida no usa escala ARS/USD; T06: duplicados, overflow, revision o payload contradictorio fail-closed; T07: stale pointer, host epoch o revocación entre procesos no entregan READY productivo; T08: cierre, cancelación/rotación/ON_STOP limpian cache (futuro host). Ningún test está ejecutado aquí.
El mapper A puede recibir DTOs falsos en tests y por eso NO puede declarar autoridad, UID/Keystore, lease fresco o invalidación cross-process. B3B1B1B no está listo para código hasta que el contrato trusted host e invalidación tras revocación sean demostrables. Si el B5 independiente no permite el mapper como pieza inertemente aislada, STOP.
**Veredicto B5: DOC_ONLY; candidato A para auditoría independiente y permiso individual, B pendiente de investigación. NO READY / no código en este checkpoint.**

## B6
NOT_RUN. Permit sigue BLOCKED. Sin commits funcionales.
## B7
PENDING para unidad nueva. No declarar PASS por esta preauditoría ni repetir certificaciones históricas.
