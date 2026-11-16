# FARO V5 — S4 W01-A · B7 PASS Scoped (Finance Draft Preview)
Fecha AR: 2026-11-15. Unidad S4_W01_A_FINANCE_MANUAL_DRAFT_PREVIEW.
Inicio código 30c8e5e8079d4c2a5d81fd848b253cc26243e67c. RED_FIX solo test en
3d62a39489b8007e651b5470991d7d2992eb6d6d. El B1–B5 aprobado está en
docs/v5/FARO_S4_W01_FINANCE_PREAUDIT_B1_B5_20261115.md, permiso individual
docs/v5/FARO_S4_W01_A_IMPLEMENTATION_AUTHORIZATION_B1_B5_20261115.md;
RED_FIX documentado en docs/v5/FARO_S4_W01_A_RED_FIX_B7_20261115.md.

## B1
Differences verificadas: commit original solo 3 rutas W01-A (Kotlin puro, tests, workflow);
commit RED_FIX solo FinanceManualDraftPreviewTest.kt (ID del positivo).
No modificaciones al catálogo180, Reality, Compose, V2, S2/S3, main ni device.
## B2
Reutiliza FinanceAccountCreate, FinanceEntryRecord, FinanceSnapshot y sus pruebas históricas;
solo 10 nuevos test de validación previa. Sin repetir W00/S3.
## B3
Preview es lectura pura del snapshot y aritmética exacta. No concede grant,
no crea ApplyPlan, no guarda Reality, no produce receipt ni mueve dinero externo.
## B4
CI original 43780897721 FAIL 9/10 por fixture positivo con cuenta duplicada.
B7 RED_FIX previamente registrado antes del segundo código.
CI reejecutada 43781556486 COMPLETED/SUCCESS exact SHA 3d62a39489b8007e651b5470991d7d2992eb6d6d;
job 133019673386: Gradle BUILD SUCCESSFUL; comprobador XML exacto
S4_W01_A_FINANCE_MANUAL_PREVIEW_XML_PASS 10/10; 10/0/0/0.
Guardia protocol 43781556504 SUCCESS exact SHA.
El guard del HEAD documental ac5dd80f6f02ae575ba2a804555a5b9e7fe4f703,
run 43781902062, falló porque el preflight READY veía B7 anterior
(no por compilación ni tests). Este cierre consume READY con BLOCKED.
## B5
Opening ausente DENY, cero solo explícito, duplicados DENY, moneda inválida DENY,
monto no positivo DENY, cuenta no encontrada DENY, fecha incoherente DENY,
ledger overflow DENY, preview no muta. Host Finance real, UI, transferencias,
cuotas, presupuesto, APK y físico G15 NOT_RUN.
## B6
Publicación únicamente este B7, permiso, pointer, handoff; writer epoch940.
## B7
Resultado observado de prueba: PASS_SCOPED_JVM_PREVIEW_ONLY 10/10.
La certificación documental final de este commit está condicionada a
guard siete-bloques SUCCESS del nuevo SHA, writer RELEASED epoch940,
last_checkpoint HEAD exacto, diff solo 4 docs y permiso BLOCKED.
W01-A cierra en scope JVM preview; W01 global continúa OPEN.
