# FARO V5 · W01-C/D/E: autorización humana específica, catálogo y migración B1–B5 preliminar
Fecha local 2026-11-15. Baseline 3090416219ba7ff8b551e239a11cdbeadfd5291c. Decisión del usuario: "Autorizo" a
incorporar NUEVAS operaciones al catálogo 180 y migraciones versionadas de Reality para
transferencias internas, compromisos/cuotas y presupuestos. FARO no mueve dinero real
externo. Esta autorización de ALCANCE no es permiso de código B6 ni READY.

## B1
FinanceOperations tiene solo snapshot.read, account.create y entry.record. RealityStore
exige SCHEMA_VERSION=7, PLANNING_SCHEMA_VERSION=8 y LEGACY_BACKUP_SCHEMA_VERSION=5;
contiene finance_state/account/account_opening/entry y tablas de MonthlyPlan en 8.
v5/spec/generated/contract-baseline.tsv es fuente de contratos generados.
Antes de migrar: identificar todos los lectores de esquema 7/8, backup/restauración,
generation pointer, compatibilidad física y aplicaciones aisladas. W00 solo GO de
infraestructura transaccional; W06 host Household+Finance aún STOP.

## B2
Preservar inalterados los 180 descriptores y su identidad; nuevos contratos
exclusivamente de Finance autorizados por unidad, sin acceso Household al dinero.
Reusar transacciones una sola Reality, receipts, WAL FULL, preflight, idempotencia,
Undo/UNKNOWN y fuente real Finance. Un compromiso futuro no es gasto REAL.
MonthlyPlan no concede permisos Finance.

## B3
Fuente técnica oficial: SQLite transacciones https://www.sqlite.org/lang_transaction.html,
alteraciones https://www.sqlite.org/lang_altertable.html, PRAGMA user_version y
foreign_key_check https://www.sqlite.org/pragma.html, Android offline-first
https://developer.android.com/topic/architecture/data-layer/offline-first.
A candidata: migración incremental y atómica compatibilizada 7/8 y backups,
con nuevos contratos independientes por subunidad. Rechazadas: elevar esquema
a ciegas, ATTACH+WAL para atomicidad global, dos Apply secuenciales, importar
datos ajenos o declarar saldo $0 ante falta de autorización.

## B4
W01-C transferencia: una mutación Finance de origen+destino en misma moneda,
monto minor positivo, ID propio, una transacción SQL y un receipt. Fallo después
del débito => rollback integral; nunca dos confirmaciones separadas. Política de
sobregiro pendiente de decisión técnica, no inventarla.
W01-D compromisos/cuotas: calendario y pasivos previstos separados del ledger
REAL; fechas M+7/M+12/M+36 preservadas, PAID_CONFIRMED solo tras evento real
revisado, no por vencimiento. Verificar frontera con MonthlyPlan.
W01-E presupuestos: topes por mes, categoría y moneda sin debitarlos de Reality;
lecturas parciales/denegadas NULL, sin sumar USD/ARS sin FX. Migración versionada
solo después de auditar caminos de backup/generation y plan de rollback.
Rutas exactas, nuevas OperationIds, tablas e índices NO definidos ni
aprobados con READY en este documento. No presumir que schema 9 sea correcto.

## B5
C: dos cuentas, diferencia moneda, error después primer efecto, crash,
receipt UNKNOWN, revoke, ABA, doble tap. D: cuotas duplicadas, cambio
calendario, vencimiento sin pago, restauración generaciones. E: mutación
de presupuesto no genera flujo efectivo, divisas no sumadas, fuente revocada
no equivale a cero. Migraciones: abrir 7/8, reabrir, checks FK/integrity,
recovery/rollback/backup, regresión G15 al final.
Cada C, D y E exige B1–B5 específicos y nuevo READY exact-path, code B6,
XML/CI B7. V2 productiva/Auto/Noche, S2/S3, main/V4.4 OFF/inmutables.
