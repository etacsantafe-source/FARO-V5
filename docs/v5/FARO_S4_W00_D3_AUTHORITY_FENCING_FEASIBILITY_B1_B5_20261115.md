# FARO V5 — S4 W00-D3 · B1–B5 factibilidad de autoridad y fencing multipropietario
Fecha 2026-11-15 Argentina. DOC_ONLY, baseline `8c329744fce9013d779ea6113dad8e3f0a6b84c8`. **NO READY, NO Kotlin, NO owner nuevo**. No representa permiso para W06 ni W07.

## B1 — Fuentes y problema
W00-D2 B7 PASS scoped 7/7 exact SHA `8c329744fce9013d779ea6113dad8e3f0a6b84c8`, y S4W00D2... blob `210d2c7b7d92ed7bea14b6f251ad1a008455381c` descubren requisitos estructurales para Reminder/MonthlyPlan a partir de Review canónico, NO grants. W00-D1 shape 11/11 acepta observaciones entregadas por caller, no autentica source de permisos. W00-C host DENY dos owners 2/2. El host actual `FaroV5Planning8ReviewedApplyHost.kt` blob `b7655b4cf63266050100dcb08a69ee450271d948` sigue single-node y dormant. `FaroV5Planning8ObjectGrantOwner.kt` blob `5301094c14dabdca7a024dc5bd6b505fe146f746` y `FaroV5Planning8ReviewGrantLeaseBridge.kt` blob `5fa2f461455bbf312a7240d0d1346be95530b340`: locks in-memory de un scope, no fencing externo. `FaroV5Planning8LocalUserRequestPolicy.kt` blob `a86e67370782cfeb0f6308a5a350a7237d8cae65` no autentica evento humano por sí mismo.

## B2 — Reutilización y V4.4
Conservar separaciones Workspace Review vs Reality, kernel AtomicApply/receipts/Undo, dos authorities reales independientes y una sola Reality SQL transacción si se demuestra. Conservar ACL por owner/frescura, generation/host epoch y single-node DENY existente, sin copiar runtime V4.4, cuya lección G0D3 fue evitar dos commits económicos no atómicos. Compras administra precios/promos/estado comercial, Finanzas registra datos monetarios, FARO NO mueve dinero real.

## B3 — Alternativas externas y decisión
A candidata a investigar: usar una única transacción SQLite Reality y autorización propietaria verificable/fence transaccional común bajo orden de locks canónico. El lector D2 no es suficiente por sí solo.
B descartada: dos SQLite WAL diferentes con ATTACH para afirmar atomicidad global, porque SQLite WAL no da atomicidad de conjunto para múltiples archivos (https://www.sqlite.org/wal.html y https://www.sqlite.org/lang_attach.html).
C descartada: crear dos receipts/Apply separados y compensarlos silenciosamente.
D salida obligatoria: DENY hasta que ambos owner grants y su revocación cross-process estén realmente certificados. Referencias adicionales: https://www.sqlite.org/lang_transaction.html y https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase. Sin dependencia nueva, API paga ni importación de código externo.

## B4 — Puertas para próxima unidad
Antes de definir una unidad funcional W00-D3 exigir: (1) procedencia realmente confiable de policyEpoch/principal para CADA owner, no capabilityId de D2; (2) lease/fencing hasta COMMIT o denegación inequívoca; (3) orden de adquisición sin deadlock; (4) una transacción SQL y un recibo idempotente; (5) compatibilidad con recuperación/generación y Review mutada; (6) sin crear owner Household productivo hasta su autorización específica. Pruebas futuras de diseño D3-T01..T08: falta owner, revocación A/B, swap scope, ABA/generación, deadlock inverso, fallo de segundo binding con rollback, fallo cross-process entre check/COMMIT y UNKNOWN receipt-first. Todas NOT_RUN.

## B5 — Auditoría adversarial
La confianza de un host no puede inferirse de un token de Chat/Workspace ni del catálogo. `S4W00D1ScopeRequirement.authorityId` de D2 es solo un identificador estructural; no aceptar como principal real ni grant. No usar Workspace bajo Reality mutex. Faltan owner real Household, política transversal y prueba en proceso distinto. W00 GLOBAL OPEN; W06 requiere Household+Finance REAL; W07 BLOCKED. Este documento NO aprueba código ni emite permiso READY. Próximo objetivo: delimitar solución verificable o STOP, sin tocar V2 productiva/Auto/Noche.
