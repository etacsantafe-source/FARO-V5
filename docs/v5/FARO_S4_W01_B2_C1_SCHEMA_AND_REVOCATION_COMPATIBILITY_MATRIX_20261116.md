# FARO V5 — W01-B2-C1 · Matriz de esquema y fencing Finance cross-process
Fecha local 2026-11-16; READ_ONLY / DOC_ONLY. No READY, sin cambios de código.
Base: docs/v5/FARO_S4_W01_B2_C1_CROSS_PROCESS_REVOCATION_B1_B5_20261116.md;
W00 D3 GO arquitectura / NO-GO ejecución productiva;
W01-B2C0 SQLite DEV 7/7 y W01-B2C2 receipt-only 5/5,
ninguno autoriza cambios productivos ni ACL entre procesos.

## B1 — Matriz basada en fuente real
Fuente primaria: v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt,
constantes líneas ~2842–2844, initializeSchema ~1024,
initializePlanningStage8 ~1098, G4 ~1858, backup legacy ~2660.

| Superficie | Estado actual en HEAD | Riesgo de añadir Finance ACL |
| --- | --- | --- |
| Reality normal | SCHEMA_VERSION=7; admite versión 0..7 y actualiza a 7 | Rechaza abrir schema8 u otro superior sin nuevo opener |
| Planning8 aislado | PLANNING_SCHEMA_VERSION=8; migración desde 7 explícita | No montar su app/stage como Reality productiva |
| G4 restore de dominio | backup.schemaVersion debe ser 7 | Nueva versión invalida restore hasta migración/contrato versionado |
| Backup legacy | LEGACY_BACKUP_SCHEMA_VERSION=5 | Mantener lectura legacy y migración acotada de recibos |
| Finance owner | cuentas y entries con revisiones, sin ACL durable SQL | No convertir Revision de finanzas en permissionEpoch |
| MaterialWriteGate | ReentrantReadWriteLock in-process | No bloquea revocaciones desde segundo proceso |
| FinanceObjectGrantOwner DEV | nonce/epoch dentro de instancia JVM | No garantiza fence entre último check y COMMIT |
| RealityStore.run | BEGIN IMMEDIATE, COMMIT/ROLLBACK, receipt/journal en una Reality | Diseñar política para el mismo dominio serializable |
| Catálogo | 180 operaciones con metadata generada por 3 fuentes de spec | C/D/E requieren adicionar contratos sin editar TSV manualmente |
| Generaciones | puntero con sequence; restore/replay fences | Rollback/ABA no debe revivir grants ni epochs |

## B2 — Reutilización, V4.4 y ownership
Preservar una única Reality para Finance, recibos PREPARED/COMMITTED
y Workspace Review como decisión humana. Un grant no se obtiene de
Workspace authorityToken, ni de IA, ni de balances ni del catálogo.
Reusar las 180 operaciones originales intactas y los lectores
schema7/Planning8 certificados sin repetir sus campañas.
No vincular acceso Household/MonthlyPlan a Finance.

## B3 — Alternativas y decisión de factibilidad
A: especificar política Finance durable de principal real, epoch
no reutilizable, y revalidación dentro del mismo ámbito SQLite
BEGIN IMMEDIATE–COMMIT junto con su revocación; CANDIDATA,
NO IMPLEMENTADA, sin elegir aún esquema/versión.
B: usar synchronized in-memory + precheck como sustituto: RECHAZADA.
C: habilitar V2 shipping para conseguir credencial: RECHAZADA.
D: usar ATTACH/WAL con dos bases como transacción atómica: RECHAZADA.
E: elevar schema9 sin auditar restore/Planning8: RECHAZADA.
Fuentes SQLite: https://www.sqlite.org/lang_transaction.html,
https://www.sqlite.org/isolation.html y https://www.sqlite.org/wal.html.

## B4 — Orden futuro con gates distintos
C1-00 ampliar esta matriz a TODOS consumidores de backup/restore,
opener, generación, APK aisladas y schema7/8; finalizar compatibilidad
antes de cualquier nueva tabla/índice. Sin READY actual.
C1-01 diseñar política SQL versionada y migración de prueba,
con CRASH rollback y dos conexiones/procesos mid-COMMIT.
C1-02 acreditar fuente de identidad humana, consentimiento real
Android y owner grant Finance separado; CI + aceptación G15.
C1-03 integrar Apply Finance productivo solo si C1-01/C1-02
y W01-B2-C2 PASS, y B2-B4 UI/integración están certificados.
C/D/E (transferencias, obligaciones/cuotas y presupuestos)
tienen aprobación humana de alcance pero requieren catálogo generado,
schema versionado propio y permisos B1–B7 por unidad. El orden preciso
y el versionado futuro deben decidirse al resolver C1-00;
no generar ahora migraciones ni datos.

## B5 — Pruebas y riesgos que no están resueltos
M01 legacy5 restore, M02 apertura schema7, M03 stage8,
M04 migración de ambos con rollback/crash, M05 G4 backup+restore,
M06 integridad/FK+hash, M07 dos conexiones con revocación
entre binding y COMMIT, M08 generación ABA/restore/restart.
Todos NOT_RUN. Una prueba en un solo proceso NO sustituye M07.
El próximo READY de C1 requiere evidencia de diseño y paths exactos
y NO debe heredar READY consumido de C2.
V2 productiva/Auto/Noche OFF, S2/S3/main/V4.4 intactos,
sin transferencias externas ni APIs pagas.

## B6
Solo auditoría documental; ni migrations ni Kotlin ni CI nuevo.

## B7
DOC_ONLY, NO PASS funcional para cross-process.
Conservar M01–M08 NOT_RUN hasta la subunidad propia.
