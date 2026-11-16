# FARO V5 · W01-C/D/E delta preciso: contratos oficiales y Reality 7/8
Fecha AR 2026-11-15. READ_ONLY documental; no B6 funcional.
Autorización humana específica registrada en
docs/v5/FARO_S4_W01_CDE_AUTHORIZED_SCHEMA_CATALOG_SCOPE_B1_B5_20261115.md.

## B1 — Hallazgos primarios del HEAD
- v5/spec-build-logic/src/main/kotlin/faro/buildlogic/FaroSpecBridge.kt
lee docs/v5/V5_CAPABILITY_TAXONOMY.md, V5_OPERATION_CONTRACT_INVENTORY.md,
V5_RG06C_07_GLOBAL_REFREEZE.md; valida duplicados, identidades, perfiles
y recuentos, después genera v5/spec/generated/contract-baseline.tsv.
- v5/spec-build-logic/src/main/kotlin/faro.spec-bridge.gradle.kts
exige verifySpecBaselineFresh ANTES de generar catálogo runtime.
- BuiltInContractCatalogTest comprueba 54 capabilities, 180 operations,
preserva SHA de los 172 originales e identidad de 8 Guardados.
- RealityStore.kt exige schema7 para apertura ordinaria; schema8 solo
Planning stage aislado. LEGACY_BACKUP_SCHEMA_VERSION=5; G4 Restore
exige schemaVersion==SCHEMA_VERSION (7). Las tablas Finance actuales
son state/account/account_opening/entry; MonthlyPlan usa tablas nuevas en 8.
- createFinanceAccount omite fila opening cuando cero explícito;
readFinance usa COALESCE(opening.amount_minor,0). Preservar procedencia:
en import/restauración no inferir "cero confirmado" de ausencia de fila.
- recordFinanceEntry escribe dentro de RealityTransactionScope y
actualiza finance_state.revision; FinanceEntry solo INCOME/EXPENSE,
sin vínculo de transferencia, obligación o presupuesto.

## B2 — Reuso obligatorio y mapa de impacto
Preservar intactos 180 descriptors existentes. Para NUEVAS operaciones:
editar autoridad/taxonomía si corresponde, inventario, refreeze/counts,
regenerar TSV mediante build-logic, actualizar tests de catálogo sin
aflojar protección original 172+8, y B7 de cada ID exacto.
Para schema futuro: migración desde 7 y 8 bajo nuevo opener aislado,
actualizar validaciones schema/backup/restore/generation y pruebas
de checksum, crash, FK/integrity y política de corte.

## B3 — Alternativas y fuentes
SQLite ALTER y 12-step migration:
https://www.sqlite.org/lang_altertable.html;
SQLite BEGIN IMMEDIATE/rollback:
https://www.sqlite.org/lang_transaction.html;
user_version/FK:
https://www.sqlite.org/pragma.html.
Elegir evolución staged y transaccional que mantenga legibles generaciones
antiguas; rechazar sobreescritura de scheme7/8 o actualización TSV manual
que contradiga generador. No apps ni APIs pagas.

## B4 — Contratos propuestos para auditoría por unidad
C transferencia Finance ATÓMICA en un solo owner y un receipt, 2 efectos
de ledger más identidad de transferencia; nunca dos Apply secuenciales.
D obligación/cuota COMPROMETIDO separada de pago REAL, versionado y
calendario; verificar dueño de MonthlyPlan.
E presupuesto mensual/categoría/moneda read-model y escritura de límite
sin mutación de ledger; no sumar ARS+USD sin FX.
No fijar todavía OperationIds ni schema9: primero enumerar consumidores
del estricto 7/8 y decidir rollback, backup compatible y paths exactos.

## B5 — Gates diseñados NO_EJECUTADOS
C1 cuenta origen=destino, C2 FX mixto, C3 débito/crédito con fallo inyectado,
C4 rollback/receipt/UNKNOWN, C5 doble toque y ABA, C6 ledger overflow;
D1 vencimiento sin pago, D2 cuotas M+36, D3 generación restaurada,
D4 permisos de MonthlyPlan; E1 presupuesto no egreso real,
E2 fuente denegada NULL, E3 ARS/USD separados.
M1 7→nuevo, M2 8→nuevo, M3 crash/mid migration rollback, M4 FK/integrity,
M5 restore G4+legacy, M6 guard de 180 originales.
Todas NOT_RUN. Sin READY, no Kotlin, SQL ni catálogo modificado en este
checkpoint. S3/S2/main/V4.4 congelados; V2 productiva/Auto/Noche OFF.
