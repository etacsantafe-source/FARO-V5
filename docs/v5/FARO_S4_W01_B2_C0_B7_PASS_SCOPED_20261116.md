# FARO V5 · W01-B2-C0 B7 PASS scoped: Finance SQLite DEV reviewed Apply
Fecha AR 2026-11-16. Unidad S4_W01_B2_C0_DEV_FINANCE_REVIEWED_APPLY_TEST_HOST.
B1–B5: docs/v5/FARO_S4_W01_B2_C0_DEV_APPLY_B1_B5_20261116.md;
READY: docs/v5/FARO_S4_W01_B2_C0_READY_20261116.md.

## B1 — Evidencia de los dos commits de código
Inicial 9d3a0abba7ba4190225e547e2c2327e0512fbd1c,
guard 43881678616 SUCCESS, CI 43881678355 FAILURE
en Kotlin compile RETURN_TYPE_MISMATCH línea91 (7 JUnit NOT_RUN).
RED_FIX doc docs/v5/FARO_S4_W01_B2_C0_RETURN_RED_FIX_B7_20261116.md,
checkpoint 9b709245044ab054857bfba3e8561797078caf4e,
guard documental 43882549939 SUCCESS.
Corrección SOLO de cinco retornos etiquetados en
v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2C0FinanceDevApplyHost.kt,
código d441961bfd8dc5f1da961ee7c5f915be3548ce8b; tests y workflow sin cambio.

## B2 — Reutilización y evidencia de alcance
Usa BuiltInContractCatalog oficial metadata-v1, FinanceAccountCreate/
FinanceEntryRecord bindings, RealityStore schema7 y receipt durable,
Workspace Review y BeginApply CAS, MaterialWriteGate y grant owner DEV
de un solo proceso con epochs. No modifica metadata180 ni schema7/8,
ni instala APK o habilita Activity de V2 productiva.

## B3 — Alternativas rechazadas
Host legacy hardcoded, fingir principal autenticado, inferir permiso desde
APPROVED, pagos externos, duplicar recibos, exportar datos y correr
Apply en una base real: RECHAZADOS. C1 cross-process y C2 recovery
requieren unidades específicas.

## B4 — Pruebas dirigidas exactas, datos temporales
GitHub Actions 43882851971 COMPLETED SUCCESS mismo SHA d441961bfd8dc5f1da961ee7c5f915be3548ce8b,
job 133353032899, Gradle BUILD SUCCESSFUL in 3m35s,
S4_W01_B2C0_FINANCE_DEV_REVIEWED_APPLY_XML_PASS 7/7.
0 failures, 0 errors, 0 skipped. Guard 43882851982
COMPLETED SUCCESS exact mismo SHA.
7 casos: CREATE con receipt, ENTRY con receipt/saldo, PENDING/DISCARD
DENY, sin policy/host DENY, metadata falsa DENY, ABA/hostEpoch DENY,
repetición sin segundo Apply/receipt. Solo SQLite efímera JUnit.

## B5 — Limitación de seguridad no negociable
NO PRUEBA identidad humana Android, ACL Finance duradera cross-process,
revocación después de último check y antes COMMIT, caída después de
Reality COMMIT antes de ObserveCommitted, UI/Avisos, G15 físico.
MaterialWriteGate y FinanceObjectGrantOwner son single-process.
Esto es B7 PASS técnico aislado, NO permiso de Apply productivo.

## B6
Scope exacto tres rutas iniciales y una ruta host corregida,
sin cambios a tests en RED_FIX. Consumir READY de C0.

## B7
PASS_SCOPED_DEV_FINANCE_SQLITE_REVIEW_APPLY_7_OF_7.
Cierre DOCUMENTAL condicionado a guard nuevo HEAD, writer RELEASED
epoch966 last_checkpoint=HEAD, permiso BLOCKED y diff solo docs.
W01-B2-C1/C2 y W01 global siguen OPEN; V2 productiva OFF.
