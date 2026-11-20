# FARO V5 — W04-C1a · Observación manual v1, no cotización completa · B1–B5
Fecha Argentina: 2026-11-20. Baseline exacto `55cd674af79b0ee0f6d287311ded0fe5da9fe12f`.
Unidad `S4_W04_C1A_MANUAL_PRICE_OBSERVATION_DEV`, sólo DEV. B6 TODAVÍA NO AUTORIZADO al redactar.

## B1
- `CommercialEvidence.kt` @blob `cf9d53089cafd93538e6b03f9873c98264d20b65`: schema1, `CommercialPriceObservation(productKey,merchant,currencyCode,amountMinor>0,sourceKind,sourceRef,observedAtEpochMillis?)`; `CommercialEvidenceStore.record` transaction SQLite IMMEDIATE, commit, idempotency unique, same key+fingerprint replay, mismatched fingerprint conflict, clock adquisición separada. `CommercialEvidenceRecordExecutor` exige `commercial.evidence.record` N-034 `OBSERVATIONAL_WRITE` BINDABLE, compara descriptor congelado y descarta dependencia no vacía. No se debe tratar `commercial.quote` READ_ONLY como permiso de escritura.
- `EvidenceBackupPort.kt` @blob `23859d874c6203edf8cebdf078a5771315bdc5b0` representa records v1 y schemaVersion; `EvidencePortabilityPort.kt` @blob `104522e7605f3a2924fcf7ccbcf8f40a04a08399` proyecta observación v1. Migrar para persistir localidad, vigencia, presentación o elegibilidad requiere cambios atómicos de schema, backup/restore, migración semántica, portability e import/export: **otro gate C1b**, no aceptable en C1a.
- `FaroV5GenerationStoreGraph.kt` @blob `f62740a3b6fda64231c4bfc8e1a275dc2ee9db0a` contiene Evidence+Reality en única generación, gate y lease de Household10, C0B lectura autorizada; NO puerto de escritura comercial al host. `S4W04HouseholdNeedApplyHost.kt` @blob `df82f08a234bbe679632d030f06edefd2402eb89` ofrece C0A selección y C0B read con foreground/pointer/epoch y Household N-033 READ_ONLY.
- C0A B7 PASS 12/12 y C0B B7 PASS 9/9, guardia `44791117298` SUCCESS sobre baseline; no reprogramar ni repetir.

## B2
Contraste P02 V4.4 L11/L15 y W04-C general: la cotización humana tiene precio/comercio/contexto/localidad/vigencia; el registro v1 sólo representa una **observación** sin prueba de validez actual. V5 Reality necesidades, Evidence observaciones, Finance efectivo fuera de este owner. UX V4.4 como referencia, NO copiar persistencia ni asociar por nombres. Reusar C0A NeedId/productKey y C0B grants/fences.

## B3
- USAR: `CommercialEvidencePayload.payload`, `PreparedOperation` con `DependencyStampSet.empty()` y key generada por host, `CommercialEvidenceRecordExecutor` y `CommercialEvidenceStore.record`. Reapertura/backup v1 sin tocar schema.
- ADAPTAR: método estrecho en la sesión actual que toma `PreparedOperation` y callback grants Household+Commercial+confirmación, relee gate antes de ejecutar, nunca expone store mutable; nuevo host interno `S4W04ManualPriceObservationHost` para preparar/confirmar. C0A candidata vinculada a snapshot/generation/pointer/principal, N-033 Household read y N-034 Commercial observational write independientes.
- REFERENCIA: cotización V4.4 y P04 Grocy/KitchenOwl para flujo de usuario, no dependencias. DESCARTAR: guardar metadatos como sourceRef mágico, abrir segundo root/owner, convertir moneda, modificar Finance/stock/needs, presumir localidad/validity, ejecutar sourceRef como URI, scrape/API pagas.
- División fundada en límites reales de codecs/backups: C1a v1 simple, C1b `S4_W04_C1B_TYPED_COMMERCIAL_TERMS_MIGRATION_DEV` VERSIONADO posteriormente con permisos/rutas propios. C1 completo NO puede cerrarse por PASS sólo C1a.

## B4
- Entrada DEV de C1a: candidato C0A de necesidad OPEN y productKey expresamente elegido; `ManualPriceObservationInput` (merchant canónico, currency de 3 mayúsculas, amountMinor >0 sin escalas inventadas, fuente literal no vacía y limitada, observedAt nullable). `sourceKind=MANUAL` obligatorio, `acquiredAt` lo determina Evidence al persistir.
- Campos que esquema NO PUEDE preservar (`locality`, `validUntil`, `presentation`, `paymentMethod`, `eligibility`) son explícitos como opcionales en DTO, pero si el usuario aporta cualquiera, C1a devuelve `REQUIRES_VERSIONED_TERMS` y no guarda una cotización parcialmente silenciosa. No inferir que nulo es verdadero; cobertura siempre `localityKnown=false`, `quoteValidityKnown=false`, `presentationKnown=false`.
- Flujo de **dos fases**: `prepare` tipa payload/descriptor/idempotency sin escribir; `confirmAndRecord` recibe una señal/callback de confirmación humana desde host de confianza. Revalidar need+snapshot+fence+foreground+contracts+ambos grants ANTES de escritura. La idempotency derivada (dominio, principal, generación, secuencia, needId, requestId) preserva reenvíos; misma identidad con distinto payload produce `RejectedIdentityConflict` durable, sin nueva observación.
- Dentro de mismo lease y shared material gate, `CommercialEvidenceRecordExecutor.execute` es el único camino de registro. Validar descriptor OBSERVATIONAL_WRITE y rechazar dependencia ajena. Revalidación posterior: ante pérdida de autoridad **después** de commit, devolver `RECOVERY_REQUIRED` sin declarar rollback; replay idempotente tras recuperar grants. Si falta confirmado, grant, need abierto, fuente, contrato o hay cambio de generación, no escribir.
- No receipts de Reality, no transferencia monetaria, no compra/stock. No evidencia de UI/G15 en C1a.

**Rutas exactas individuales (cinco):**
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` (modificar sólo sesión Household10)
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04HouseholdNeedApplyHost.kt` (método estrecho)
3. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ManualPriceObservationHost.kt` (NUEVO)
4. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04ManualPriceObservationHostTest.kt` (NUEVO)
5. `.github/workflows/faro-v5-s4-w04-c1a-manual-observation.yml` (NUEVO)
NO tocar `:evidence`, schema, backup/portability, catalog, Gradle, C0A/C0B, main/V4.4 ni app productiva. Requiere guardia del documento y READY exact5 antes de B6.

## B5
Plan adversarial pre-código (todos NOT_RUN hasta B6):
T01 prepare sin persistir; confirmación explícita -> registro v1 real, mismo root, read back exacta y Need sin mutar.
T02 falta confirmación, owner/foreground/Household grant/Commercial grant -> sin registro.
T03 términos conocidos no representables -> REQUIRES_VERSIONED_TERMS, sin escritura.
T04 amount <=0, currency inválida, sourceRef vacía/controlada, timestamp negativo, SKU no elegido -> rechazo.
T05 misma idempotency key+payload replay, key+payload distinto conflicto y no duplica.
T06 revocación antes del commit -> cero escrituras. Revocación luego de commit -> RECOVERY_REQUIRED, reintento idempotente, NO segundo registro.
T07 cambio need revision/completed/remove, pointer ABA/generation, epoch, principal, cerrar host -> rechazo.
T08 sourceRef hostil se guarda como texto; nunca URL ejecutable ni instrucción.
T09 Evidence backup/reopen snapshot v1 preserva registro, no migration accidental, sin materializar nueva raíz mientras host abierto.
T10 cobertura UNKNOWN para localidad, vigencia y presentación, moneda no convertida, no pagos o stock.
T11 preflight por exact 5 rutas, Kotlin/JVM XML, guard por SHA y B7 independiente.
## Estado de permiso
B1–B5 de C1a documentados para verificación por SHA/blob; B6 y B7 siguen pendientes. C1b NO AUTORIZADO.
