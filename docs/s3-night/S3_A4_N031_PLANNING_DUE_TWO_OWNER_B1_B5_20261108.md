# FARO V5 — S3.A4 N031: dos owner reales, vencimientos tipados — B1–B5
Fecha AR: 2026-11-08. Unidad: `S3_A4_N031_PLANNING_DUE_TWO_OWNER_SOURCE`. Baseline: `14a7c066a71e364a9de68a97b713c795a0a23855`.
Writer reservado epoch 557; V2 productiva/S4/Auto/Noche OFF. No reabrir N033C3 Undo B7 (56/56), N015 federación B7 ni backup/restauración schema8 B7.

## B1 — Evidencia, brecha y aceptación
- RealityStore schema8 staging ya aloja `ReminderPersistencePort` y `MonthlyPlanPersistencePort` reales; `ReminderSnapshotReadApi`, `MonthlyPlanSnapshotReadApi`, `ReminderOccurrence` y `MonthlyPlanItem` poseen datos tipados. No inventar nuevos tables, catálogo, recurrence scheduler, ni duplicar owner.
- `FaroV5RealityMatterDueReadSource` B7 lee SOLO Matter y deja `globalAttentionComplete=false`. N031 carece de fuente de lectura para vencimientos almacenados Reminder/MonthlyPlan. El host Android aún carece de emisión/verificación de ACL real y montaje de esta nueva lectura.
- Una fuente aislada recibirá puertos tipados desde futura composición host y autorización independiente para cada owner (admisión, epoch, generación, puntero durable). Devuelve null ante permisos ausentes/revocados, nunca un falso cero; no confiere permiso ni se conecta a Home/UI.
- Aceptación de esta unidad acotada: 6 pruebas JUnit dirigidas de lectura combinada de ocurrencias PENDING con regla ENABLED y MonthlyPlan PLANNED sin pago confirmado, zonas locales +7 días, orden estable, truncación sin cobertura global, negativas ACL/ABA/excepción/cancelación. No repetir SQLite Undo ni campaña física.
- Si la regla existe pero NO tiene ocurrencias persistidas, NO fabricar instancias DAILY/WEEKLY/MONTHLY ni afirmar que los avisos futuros están completos.

## B2 — Comparación V4.4 / V5 existente / alternativas
- Auditoría previa del `FaroV4FollowUpGateway` indica composición de recordatorios y vencimientos por dominio con reglas y ocurrencias. SOLO referencia semántica: el origen V4.4 no se importa, ni se reutilizan datos legacy.
- En V5 se reutilizan modelos/puertos oficiales de `:owner:reminders`, `:owner:monthlyplan`, y el precedente de `FaroV5RealityMatterDueReadSource` que exige host, generación, puntero/ACL antes y después de cada read. No usar Backup FBS2 como API de UI ni marcar como pagado lo que solo está planeado.
- La forma canónica del vencimiento de recordatorio es un instante persistido; la del plan mensual es una fecha civil: preservar ambas, ordenar por día local y claves estables, sin fabricar hora mensual.

## B3 — Investigación externa y selección
- Android Developers, Offline-first: https://developer.android.com/topic/architecture/data-layer/offline-first : fuente de verdad local persistida, reutilizar owner REALITY y no crear cache derivada/otro write owner (**USAR principio**, sin dependencia/API).
- Android Developers, Guide to app architecture: https://developer.android.com/topic/architecture : datos inmutables de una sola fuente oficial (**ADAPTAR** con DTO mínimo de solo lectura, sin títulos/importes).
- OWASP Authorization Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html : DENY por defecto y verificación en cada acceso; **USAR** checks separados, revocación entre lecturas y final.
- SQLite isolation: https://www.sqlite.org/isolation.html : lecturas atómicas dentro de un owner y WAL, pero dos lecturas independientes no constituyen snapshot transowner; **REFERENCIA**, prohibir etiqueta de cobertura completa/global.
- Alternativa A (**ADAPTAR**): leer solo ocurrencias ya persistidas + cuotas PLANNED, mantener cobertura parcial verificable. Alternativa B (**DESCARTAR en esta unidad**): expandir recurrencias en Surface/Chat, inventar eventos sin scheduler DST ni owner. Alternativa C (**DESCARTAR**): usar snapshots FBS2 como nuevo Home owner, duplicaría autoridad y expondría datos privados de backup.
- Cero código externo copiado, cero licencia nueva, cero servicio/API pago. Hipótesis falsable: una revocación entre snapshots anula TODA la página y jamás se infiere un contador global.

## B4 — Delta único y ruta de prueba
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5RealityPlanningDueReadSource.kt`: DTO mínimo (fuente, identidad opaca, due local/date o instante), dos read-port tipados inyectables, emisión de fuente PARCIAL, D-021 no copia de V4.4.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5RealityPlanningDueReadSourceTest.kt`: 6 casos (sin ACL; due/status; límites del día local; truncación y orden; ABA/revocación en medio; excepciones/Cancel).
3. `.github/workflows/faro-v5-s3-planning-due-directed.yml`: `:app:compileDebugKotlin :app:testDebugUnitTest --tests ...`; XML exacto 6/6, zero failed/error/skipped. Guard seven-block obligatorio mismo SHA.
- No tocar `RealityStore`, migration schema8, Apply/Undo, Android Activity, Home, catálogos o permisos de producción; esto es proyección JVM aislada no conectada.

## B5 — Auditoría adversarial y límites
- Prohibir lectura si cualquiera de ambas grants/readAdmitted/ACL epoch no existe, cambió tras read o se arroja excepción; pointer durable A→B→A, generación y epochs se revalidan por lectura y después de proyección. CancellationException debe propagarse.
- Filtrar reglas disabled, occ ACKNOWLEDGED/CANCELLED, ítems PAID_CONFIRMED/CANCELLED; ninguna inferencia por notificación delivery. El rango incluye pasado y +7 por FECHA CIVIL host-supplied; no cambiar default time zone.
- No renderizar títulos, importes, pagos, detalle ni conocimiento de permisos a través de null vs empty; null = UNKNOWN_DENIED, lista vacía = empty SOLO dentro de owner fuente tipada; completitud global permanece false incluso con limit suficiente.
- No hay snapshot coherente de los tres dominios Matter+Reminder+MonthlyPlan, ni host ACL real, ni pantalla: A4 OPEN y S3.A5 física NOT_RUN. No declarar N031 ni Home como completos por esta unidad. Si el host no provee fuentes auténticas y una ACL legítima, **STOP shipping**.
- Permiso B6 únicamente por los tres paths anteriores y por SHA de esta evidencia exacta. B7 contrastará CI/XML, diff y límites antes de liberar.
