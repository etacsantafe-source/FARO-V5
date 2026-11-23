# FARO V5 — W04 F2C1B1B1A | DEV owner read-scope leases — B1–B5

Fecha local 2026-11-23. Baseline: `ea9943ac7a058b6f7db408202b602b717f647ed6`. Unidad `S4_W04_F2C1B1B1A_OWNER_READ_SCOPE_DEV`.
**Esta subunidad se limita a la lógica aislada de las dos políticas de READ, NO al acceso real de Android/F2C1B1B1 completo.** El B5 de la unidad global F2C1B1B1 sigue STOP hasta composición con fuentes LIVE.

## B1 — Evidencia y objetivo binario
F2C0 tiene lectura REAL SQLite Household10/Evidence2 desde una sola lease activa (6/6 JVM B7 previo). F2C1B1A tiene dos taps Android validados; F2C1B1B0 host Android DEV tiene 5/5 negativas CI `45181057697` y guarda `45182583818`; no tiene owner policy fuente real. No repetir. Hoy `HouseholdNeedPersistencePort.read()` define snapshot de owner Household, y `CommercialEvidenceStagedTypedPortabilityPort.snapshot()` lee Evidence2 tipada, ambos read-only; `FaroV5Household10Evidence2DevSelectionReadPort` coordina el lease, pero requiere dos callbacks todavía ajenos. Faltan modelos de permiso owner independiente, con objeto exacto, revocación y semántica de SOURCE CHANGED.
Objetivo acotado: **dos owner grant owners independientes** de alcance SESSION DEV, capaces de emitir un nonce efímero solo después de un callback confiable del host y snapshot de dominio elegible; deny-by-default y validación repetida de selección/scope/origen. La fuente será un puerto de lectura real **cuando F2C1B1B1B conecte generación y policy**; tests con fakes certifican solo invariantes, NO emisión real ni acceso Android.

## B2 — V4.4 y reutilización
V4.4 solo UX: selección por humano y reversibilidad; no importar permisos ni DB. Reusar exactos `HouseholdNeedPersistencePort`/`HouseholdNeedsSnapshot`, `CommercialEvidenceStagedTypedPortabilityPort`, `CommercialProductKey`, `HouseholdNeedId`, `ActiveGenerationPointer` en próxima composición, sin duplicar base ni modificar esquema. Tomar el patrón de `S4W01B2FinanceObjectGrantOwner` (lectura de epoch + nonce único + revocación) pero **no** heredar Finance ACL ni fingir autenticación. No revisar de nuevo Android B0.

## B3 — Alternativas reales y documentación primaria
- SQLite WAL snapshot isolation https://www.sqlite.org/isolation.html y https://www.sqlite.org/wal.html : mantener un único graph/lease al conectar host, revalidar fuente; una segunda apertura debilita consistencia.
- Android internal app storage https://developer.android.com/training/data-storage/app-specific : scope del root privado se verificará en Android host, NO aquí.
- OWASP MASVS-AUTH (proyecto libre) https://mas.owasp.org/MASVS/07-MASVS-AUTH/ y riesgo de componente sin control https://mas.owasp.org/MASWE/MASVS-AUTH/MASWE-0018/ : no convertir flags/consent de UI en permiso.
Alternativa A: `{true}`/UID/boolean directo — DESCARTAR, permite confused deputy.
B: nuevo SQLite ACL en dos esquemas existentes — DESCARTAR para esta subunidad: migración real y reempaquetado de H2 sin preauditoría del esquema; agranda superficie.
C: leases efímeros separados por owner, respaldados por read-port del owner, objeto+snapshot exactos, token/epoch y revocación — ADAPTAR. Sin egress, dependencias nuevas ni APIs.
D: saltar directamente a renderer Android con registros inventados — DESCARTAR, prueba positiva falsa.
Limitación: Kotlin no impone seguridad de proceso contra código malicioso en la misma app; la autoridad final necesita host Android y selección/ACL reales, no se declara production ready.

## B4 — Contrato exacto, CINCO rutas congeladas
1. NUEVO `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/spi/Household10DevReadScopeOwner.kt`: solo dev; `HouseholdNeedsSnapshot` de fuente inyectada, need OPEN exactamente uno, revision no negativa, raíz de identidad `principal,generationId,sequence,hostEpoch`, selección exacta, token nonce irreversible con revoke. Queda sin bindings Android.
2. NUEVO `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence2DevReadScopeOwner.kt`: fuente port `CommercialEvidenceStagedTypedPortabilityPort`, schema2+watermark+único producto con observaciones tipadas, token nonce independiente, exact epoch/puntero, revocación por cambio de cualquier watermark.
3. NUEVO `v5/owner/household/src/test/kotlin/ar/com/faro/v5/owner/household/spi/Household10DevReadScopeOwnerTest.kt`: 3 JUnit5 nuevos: positivo bajo host confiable + snapshot inmutable; ausencia/need COMPLETED y scope ajeno; revocación por epoch, generation ABA, row edit.
4. NUEVO `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialEvidence2DevReadScopeOwnerTest.kt`: 3 JUnit5 nuevos: positivo con typed evidence; sin términos/otro producto/permiso; watermark/epoch/revoke y token distinto.
5. NUEVO `.github/workflows/faro-v5-s4-w04-f2c1b1b1a-owner-read-scope.yml`: protocolo parent READY exact5, Java21, `:owner:household:test` y `:evidence:test` dirigidos por clase, verificar XML **6/6 distintos**, publicar XML. Sin Android ni alterar shipping.
No incluir edits de `F2C0`, `F2C1B1B0` ni D4/E en este commit. La siguiente unidad F2C1B1B1B deberá probar wiring real bajo el mismo graph y una prueba positiva Android API35.

## B5 — Auditoría adversarial y condiciones de READY de esta subunidad
PASS DE DISEÑO **solo del contrato owner efímero**: (a) OPEN y terms schema2 verificados desde la fuente owner; (b) snapshot sin ambigüedades y fuente doble chequeada antes/después; (c) scope exacto `principal+generationId+pointerSequence+epoch+need/product`, no wildcard ni Identity de Intent; (d) nonce nuevo por emisión; (e) cualquier pérdida de epoch/puntero/ready/row/watermark cierra irreversiblemente; (f) no mutación SQL/Apply/UI, no cache de datos comerciales, no login ni privilege escalation; (g) fallo por throw = DENY; (h) tests 6/6 y B7 independiente antes de promover.
**No equivale a owner grant real en Android:** el caller de las fuentes es explícitamente trusted; fakes en JUnit no son permiso de un owner del dispositivo. El B5 global F2C1B1B1 sigue STOP hasta conectarlas a las conexiones reales bajo la misma lease con pruebas Android.
Aprobar READY solo tras guardia documental SUCCESS, permiso exclusivo de cinco rutas, preflight del HEAD exacto. No V2 productiva, G15, pagos, red, Apply, main, V4.4, Auto/Noche.
