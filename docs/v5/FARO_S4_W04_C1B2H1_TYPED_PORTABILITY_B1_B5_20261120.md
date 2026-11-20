# FARO V5 — W04-C1b2H1 · Portabilidad tipada Evidence2 aislada, SIN exportar · B1–B5

Fecha local 2026-11-20. Código precedente G final `ada348d07baf5dd26bd136a59f9e1afc8b27e1ac`, guard `44826771844` SUCCESS, CI JVM `44826771657` SUCCESS new5/5 legacy5/5, regresión G4 `44826771655` SUCCESS. C1b2G queda B7 PASS scoped DEV. Nueva unidad de diseño `S4_W04_C1B2H1_STAGED_TYPED_PORTABILITY_DEV`. **B1–B5 documental, NO READY, NO B6, NO EXPORTACIÓN, NO egress**. C0A/C0B/C1a/C1b1/C1b2E/C1b2F/C1b2G ya PASS scoped: no repetir.

## B1
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidencePortabilityPort.kt` blob `104522e7605f3a2924fcf7ccbcf8f40a04a08399`: `PortableCommercialObservation` contiene producto/comercio/moneda/importe/sourceKind/sourceRef/observedAt/acquiredAt; **excluye** evidenceId, idempotencyKey y payloadFingerprint. `CommercialEvidencePortabilitySnapshot` schemaVersion+watermark+records máximo100000. `EvidencePortabilityProjection.commercial` ordena valores determinísticamente y emite hash `faro-portability-commercial-v1` sin terms.
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`: schema2 STAGED tiene `commercial_evidence_quote_terms`, `readTypedTerms` valida hash, `stagedTypedBackup.snapshot` captura records+terms opcionales; `readPortabilitySnapshot` tiene `check(readSchemaVersion()==1)` y **debe** seguir rechazando schema2 hasta que export JSON sea versionado. Backup semántico v2 opt-in y graph STAGED G no implican permiso para exportar datos personales.
- `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/EvidencePortabilityPortTest.kt` blob `99143a19cea9c1557573e7a2224efc5182d81297`: verifica port v1 sin identities y cambio de watermark; conservar.
- `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/PortableBundleJsonWriter.kt` blob `4fe914e0792325019fffa62a071e0a885a7a7062`: `writeCommercial` JSON contiene sólo campos v1, no terms ni schema comercial. `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/PortabilityCore.kt` blob `3d62c2f6f2bb849d642a176444436a2874db9ba7`: `PortabilityPreview.FORMAT_PROFILE=PORTABLE_BUNDLE_V1`, `EXPORT_SCHEMA_VERSION=1`, plan fingerprint/warnings requieren aprobación explícita y URI de salida no es backup. Exportar snapshot v2 por esta cadena hoy sería pérdida silenciosa. H1 **NO cambia** maintenance ni esa aprobación.
- C1b2E respalda rows físicas `terms=null` (legacy sin fila) vs `terms=UNKNOWN` (fila tipada explícita), C1b2F hash de terms v2, G stage graph FBS2. No existen tests de portabilidad typed v2 ejecutados.

## B2
V4.4 SmartCompra L11/L15 sólo referencia UX/ofertas contextualizadas, no autoridad de export. V5 Evidence≠Reality; nunca exponer claves de ejecución, receipts, credenciales, journal ni importación financiera; `sourceRef` y `promotionSourceRef` son texto literal no URL ejecutable. `observedAt` nullable distinto de `acquiredAt`. Localidad/vigencia UNKNOWN no implica oferta válida, monedas ARS/USD separadas sin FX. Mantener puerto v1 y watermark v1 byte-equivalentes. No tocar los cuatro stores restantes, Finance ni C1b2G.

## B3
Alternativas y mecanismos:
- **USAR** proyección pura tipada y CanonicalHashBuilder ya existente en Evidence, orden determinista con tiebreaker de fingerprint de terms y domain nuevo `faro-portability-commercial-v2`, sin escribir ni consultar la red.
- **REFERENCIA** RFC 8785 JSON Canonicalization Scheme `https://www.rfc-editor.org/rfc/rfc8785` para evitar cambios de representación/orden, y guías Android DataStore/Room migration `https://developer.android.com/training/data-storage/room/migrating-db-versions` para fixtures de evolución. No incorporar biblioteca nueva ni depender de API paga; preferir hash domain FARO y tipos existentes.
- **ELEGIDA** nueva proyección y puerto **STAGED-only** `CommercialEvidenceStagedTypedPortabilityPort` con DTO portable sin identities, sin enlazarlo a `PortabilitySourceReader` ni a `PortableBundleJsonWriter` legacy. Su futura conexión exigirá subunidad H2 de codec JSON/version2, preview/fingerprint y egress explícito.
- **DESCARTAR** extender `PortableCommercialObservation` y seguir exportando silenciosamente `PORTABLE_BUNDLE_V1`, copiar `CommercialEvidenceRecord` con idempotency/receipt, codificar terms como JSON mágico en sourceRef, habilitar `CommercialEvidenceStore.portability` schema2 antes del serializer, inferir promociones o FX, llamadas de red/paid APIs.

## B4
Contrato propuesto de H1 stage-only:
1. DTO `PortableCommercialTypedObservation(observation:PortableCommercialObservation, terms:CommercialEvidenceTypedTerms?)`, sin campos evidenceId/idempotencyKey/payloadFingerprint. `terms=null` representa ausencia física v1 y `terms=UNKNOWN` representa metadata v2 explícitamente registrada; ambos proyectan sin aseverar vigente/localidad.
2. `CommercialEvidenceStagedTypedPortabilitySnapshot(schemaVersion=2,sourceWatermark,records)` con count<=100000, orden estable por campos v1 más fingerprint terms para desempate, pertenencia v2 inequívoca. Hash domain `faro-portability-commercial-v2` incluye **todos** los campos base y presence/fingerprint de terms por índice; fuente hostil texto literal y moneda sin conversión.
3. `CommercialEvidenceStore.stagedTypedPortability.snapshot()` sólo lectura, exige `stagedTypedTermsV2` + PRAGMA schema2/health y construye de `stagedTypedBackup.snapshot` proyectando sin identidades. Relectura no crea observaciones, filas ni WAL nueva autoridad. El puerto legacy `portability.snapshot()` sigue fail-closed ante schema2 por defecto, aun con grafo STAGED G; no exponer el nuevo puerto a Android/export ni a otras generaciones.
4. H2 posterior actualizará JSON commercial terms, sección versionada, `PortabilityPreview`/watermark/approval local, redacción y egress; H1 NO es export de usuario, no declara portable bundle válido ni constituye backup.

**Rutas exactas candidatas H1 (CUATRO; NO AUTORIZADAS aún):**
1. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidencePortabilityPort.kt`
2. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`
3. `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceStagedTypedPortabilityTest.kt` (nueva)
4. `.github/workflows/faro-v5-s4-w04-c1b2h1-typed-portability.yml` (nueva)
No modificar `:maintenance`, FBS1/FBS2, generación, Finance, Android, Active, V4.4/main sin nueva unidad/permiso.

## B5
T01 v1 y schema2 aislados: v1 portable watermark/DTO intactos; H1 sobre schema1 FAIL-CLOSED y legacy `portability` sobre schema2 sigue FAIL-CLOSED.
T02 legacy MANUAL/TOOL y v2 terms known/UNKNOWN; preserva valores y diferencia terms physical-null vs explicit-UNKNOWN sin ids en DTO/toString.
T03 ordenar por valores semánticos sin depender de EvidenceId/orden de inserción; watermark v2 estable para mismo contenido y cambia ante sourceRef, locality, validity, presentation, payment, eligibility, promo/conditions.
T04 fuente `file://...`/texto hostil nunca ejecutada ni consultada; código currency ARS/USD sin FX ni valor estimado. Nunca mostrar UNKNOWN como oferta vigente.
T05 corrupción de fingerprint/fk, campos inválidos, records>100k, fecha negativa/pack cero: rechazo, sin export parcial ni mutación.
T06 no mostrar ni exportar Receipt, idempotencyKey, payloadFingerprint, token de autorización, ubicación inferida o secreto; salida sólo estructura en memoria STAGED, LOCAL_ONLY.
T07 tests JVM `:evidence:test` dirigidos + XML y regresión v1; guard exact4 por SHA, B7 independiente y writer CAS RELEASED. No UI/G15, no egress.
**Pruebas H1 NOT_RUN** en esta preauditoría.

## Gate
Estado **B1_B5_PREAUDIT_NO_READY_NO_CODE**. Tras guard documental SUCCESS, emitir READY independiente exact4 y preflight ANTES de B6. H2 requiere B1–B5 propios y JSON/profile version2; C1b global OPEN. No iniciar exportación ni activar V2 productiva.
