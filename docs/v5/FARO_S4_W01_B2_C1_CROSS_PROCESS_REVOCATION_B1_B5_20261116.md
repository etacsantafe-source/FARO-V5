# FARO V5 · W01-B2-C1 — autoridad Finance durable hasta COMMIT: B1–B5
2026-11-16 AR. PREAUDIT DOC_ONLY sin READY ni código.
Requiere W01-B2-C0 B7 guard documental verificado.
No confundir con W06 Household+Finance ni con migración C/D/E.

## B1 — Evidencia en HEAD y condición estructural
MaterialWriteGate.kt: ReentrantReadWriteLock con exclusión solo IN-PROCESS.
S4W01B2FinanceObjectGrantOwner.kt: synchronized monitor protege
revoke/regrant emitidos a través de esa instancia, no otro proceso.
ApplyTransactionExecutor.kt: revalida autorización antes/después
de bindings dentro de Reality.run, pero advierte carrera final
check→SQL COMMIT cuando fuente de política vive fuera de lock.
RealityStore.kt BEGIN IMMEDIATE y receipt/journal bajo una sola
SQLite Reality; no hay política durable Finance en misma transacción.
FARO_S4_W00_D3_FINAL_FEASIBILITY_B1_B5_20261115.md:
GO arquitectura; NO-GO host productivo sin ACL/grants cross-process
serializables con COMMIT. W00 30/30 no prueba ese fencing.
C0 DEV 7/7 es prueba temporal, NO soluciona C1.

## B2 — Reutilización y fronteras
Conservar Workspace APPROVED como evidencia de decisión humana
pero no como grant. Finance owner independiente de Household,
MonthlyPlan y Profile; mismo director Reality y journal/receipt.
No introducir un segundo SQLite y usar ATTACH+WAL como prueba de
atomicidad. No acceder Workspace/policy remota dentro mutex Reality.
Catálogo180, schema7/8, generation pointer/backup y V4.4 congelados
hasta auditoría de migraciones exactas.

## B3 — Alternativas
A: policyEpoch/ACL versionado y revalidado como autoridad serializable
durante Reality BEGIN IMMEDIATE→COMMIT; revocaciones deben tomar el
MISMO dominio transaccional, con tests en dos conexiones/procesos.
Candidato de diseño: tabla Finance ACL transaccional, identificador
de principal real verificado por host, nonces y generación/version
no reutilizables. NO APROBADO para implementar sin proof/migrations.
B: monitor Kotlin o callback foreground como barrera global: RECHAZADO.
C: leer policy antes de BEGIN pero no en COMMIT: RECHAZADO.
D: revocar después del SQL y afirmar rollback: RECHAZADO.
No se crean permisos reales, cuentas externas ni operaciones bancarias.

## B4 — Siguiente partición PREVIA a READY
C1-00 auditar todos schema7/8, backup/restore G4, lifecycle,
WriteCoordinator y consumidores; diseñar esquema/versionado y
rollback de migración. C1-01 prueba adversarial dos conexiones SQLite
con política Finance en el MISMO ámbito serializable: 2 actores
intentan revocar/commit; no incluir credenciales humanas de prueba
como certificación final. C1-02 host de identidad/consentimiento humano
verificable, policy epoch y grant hasta COMMIT; requiere Android,
y pruebas físicas finales G15. Requieren B1–B5+READY separados.
Ninguna ruta de código READY ni tabla/esquema escogido en C1 actual.

## B5 — Gates NOT_RUN/STOP
A revocar desde proceso 2 entre binding/COMMIT; nunca COMMIT
con epoch invalidado, según orden serializable.
B dos contextos/objetos/cuentas, ninguna herencia de permisos.
C pointer ABA, backup antigua, policies antiguas y host restart.
D crash/power-cut de WAL, PREPARED/COMMITTED y receipt único.
E migraciones 7/8 hacia versión nueva con checksum, FK,
rollback, readers y backup/restauración.
F identidad humana/consentimiento local real, no IA/callback sintético.
TODOS NOT_RUN. Hasta C1+C2 y aceptación G15,
V2 productiva/Auto/Noche OFF y Finance Apply producto STOP.
