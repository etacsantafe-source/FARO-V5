# FARO V5 — W04-C1B2H2A · JSON v2 comercial tipado, LOCAL_ONLY — B1–B5

Fecha: 2026-11-20. Baseline confirmado `de1c4a32fb048afc5202e409f10f9779b1e643f7`; C1B2H1 B7 PASS 8/8 CI 44846516892 y guard 44846516826. Unidad nueva `S4_W04_C1B2H2A_LOCAL_TYPED_JSON_CODEC_DEV`. Esta publicación es exclusivamente documental; NO READY/NO B6/NO egress.

## B1
Lectura real: `EvidencePortabilityPort.kt` H1 proyecta `CommercialEvidenceStagedTypedPortabilitySnapshot(schemaVersion=2,sourceWatermark,records)`, tuplas `PortableCommercialObservation`+ `CommercialEvidenceTypedTerms?` con catorce campos tipados; null físico y fila UNKNOWN explícita son estados distintos. `CommercialEvidenceTypedTerms.kt` impone dominios y valida rangos. `PortableBundleJsonWriter.kt` usa sink UTF8 y PORTABLE_BUNDLE_V1 sin terms; `PortabilityCore.kt` fija exportSchemaVersion=1, valida aprobaciones v1, y `PortabilityArtifact.kt` fija formato V1. `:maintenance` depende de `:evidence`, tiene `kotlinx.serialization.json` SÓLO testImplementation; no añadir runtime parser. H1 no exporta ni concede permiso. El nuevo codec queda aislado de todos los consumidores de portabilidad V1.

## B2
V4.4 se usa sólo como referencia UX: V5 mantiene Evidence≠Reality y exportación distinta de backup. Preservar byte-equivalencia y puertas de V1; no reutilizar writer V1, ni anexar terms a sourceRef, ni serializar ID/idempotencia/fingerprint de ejecución. UNKNOWN no significa vigencia comprobada; moneda ARS y USD intacta sin FX. Sources promotionSourceRef y sourceRef sólo texto literal sin dereferenciar.

## B3
Alternativa A elegida: codec independiente puro `CommercialTypedPortabilityJsonCodec` que transforma snapshot H1 STAGED a bytes UTF8 de sección `PORTABLE_COMMERCIAL_TYPED_SECTION_V2_LOCAL`, incluye manifiesto local, watermark y todos los campos en orden fijo. Validación de hash de origen y tamaño acotado, sin IO externa ni salida a archivo. Referencias: RFC8785 para estabilidad semántica, serializers internos UTF8 existentes para escapes; no biblioteca nueva. Alternativa B (cambiar el writer global V1) DESCARTADA por pérdida de datos. Alternativa C (compartir/exportar directo) DESCARTADA por carecer de consentimiento, cadena preview/fingerprint/redaction/egress independiente H2B.

## B4
Unidad local exclusiva, sin escritura a disco ni exportación: esquema `formatProfile=PORTABLE_COMMERCIAL_TYPED_SECTION_V2_LOCAL`, `schemaVersion=2`, `sourceWatermark`, `notice=NOT A FARO BACKUP / NOT RESTORABLE`, `records` con ocho campos observacionales y `terms` null u objeto con los 14 campos en orden canónico. Escapar strings y Unicode de manera segura. Revalidar sourceWatermark por domain faro-portability-commercial-v2, ordenamiento H1 y tiebreaker de terms.fingerprint; rechazar sin emitir bytes cualquier inconsistencia, payload demasiado grande o registro inválido. Memoria acotada máximo 8MiB; el formato es LOCAL_ONLY, no es el PORTABLE_BUNDLE_V2 definitivo ni un archivo. Tests de decodificación semántica sólo con testImplementation JSON. Mantener writer global, plan, artifact, external receipts, Finance y V2 OFF.

**Rutas EXACTAS candidatas, tres:** 
1. `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/CommercialTypedPortabilityJsonCodec.kt` (NUEVA)
2. `v5/maintenance/src/test/kotlin/ar/com/faro/v5/maintenance/CommercialTypedPortabilityJsonCodecTest.kt` (NUEVA)
3. `.github/workflows/faro-v5-s4-w04-c1b2h2a-local-typed-json.yml` (NUEVA)

## B5
T01 formato/schema v2 explícitos y legacy V1 fuente/writer intactos; T02 terms=null vs UNKNOWN explícito y objeto completo reproducibles tras JSON parse test; T03 sourceRef hostil escapa como literal, no conexión o archivo; T04 currency ARS/USD sin FX, no inferir vigencia; T05 orden semántico y watermark H1 estable, cambio fuente invalida; T06 Unicode/quotes/control chars, sustitutos inválidos FAIL-CLOSED; T07 límites record count/size y watermark corrupto FAIL-CLOSED sin salida parcial; T08 no identidades/secretos/receipts/payloadFingerprints, no files/network; T09 Gradle :maintenance:test, JUnit XML exacto, regresión V1, guard protocolo, escritor RELEASED. La lista es matriz de aceptación, no resultados ejecutados.

## Gate
NO READY. Tras guard documental SUCCESS emitir permiso EXACT3 y verificar preflight HEAD^ antes de B6. H2B deberá hacer B1–B5 y READY propios para preview/fingerprint/approval/export, y requerirá decisión de salida humana. C1B2 global OPEN hasta cierre completo. No repetir unidades previas.
