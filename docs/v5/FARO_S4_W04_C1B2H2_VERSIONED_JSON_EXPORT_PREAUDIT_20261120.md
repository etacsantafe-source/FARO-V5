# FARO V5 — W04-C1b2H2 · JSON versionado, aprobación y egress comercial · Preauditoría de diseño

Fecha local: 2026-11-20. Base H1 `db4fdd3485deff9ab36aee9b5335b6328f90c4ba` verificada 8/8 (CI `44846516892`, guardia `44846516826`). **DESIGN_ONLY / READ_ONLY / NO READY / NO B6 / NO EXPORTACIÓN NI EGRESS.** No reemplaza un B1–B5 final por rutas exactas ni aprobación humana para compartir datos.

## B1 — Evidencia de código y estado real

- `EvidencePortabilityPort.kt`: puerto H1 `CommercialEvidenceStagedTypedPortabilityPort` devuelve snapshot v2 en memoria; contiene `PortableCommercialTypedObservation` con terms null físico/UNKNOWN explícito y watermark `faro-portability-commercial-v2`. **No** genera archivos.
- `CommercialEvidence.kt`: `store.portability.snapshot()` exige schema1. `store.stagedTypedPortability.snapshot()` exige schema2+opt-in, integridad/FK. El camino legacy impide pérdida silenciosa de términos.
- `PortabilityCore.kt`: `PortabilitySourceReader` toma sólo `CommercialEvidencePortabilityPort`; `PortabilitySourceSnapshot.commercialEvidence` es tipo v1. `PortabilityPreview.FORMAT_PROFILE=PORTABLE_BUNDLE_V1`, `EXPORT_SCHEMA_VERSION=1` y `PortabilityPlanBuilder` usa `faro-portability-plan-v1`; `PortabilityBundlePreparer` comprueba fingerprint aprobado y vuelve a leer fuentes antes de generar.
- `PortableBundleJsonWriter.kt`: `writeCommercial` serializa únicamente `PortableCommercialObservation` v1; el manifiesto hereda profile/schema y digest. **Cambiar la entrada a Evidence2 sin una nueva cadena completa perdería términos.**
- No hay B7 para JSON/export schema2 ni permisos de egress; H1, C1b2G/F/E, C1b1, C1a, C0A/C0B no se repiten.

## B2 — Compatibilidad y límites

Mantener `PORTABLE_BUNDLE_V1` byte-equivalente y `portability.snapshot()` schema2 FAIL-CLOSED. Evidence != Reality, exportación NO es backup/restore, no copiar execution IDs, receipts, recovery journals, tokens, credenciales, fuentes HTTP ejecutadas ni valores de Finance. `sourceRef` y `promotionSourceRef` se tratan como texto, sin ejecución de URL. Moneda original sin FX, UNKNOWN no significa vigente. Exportación con aprobación humana explícita y destino elegido por el usuario; no operaciones bancarias.

## B3 — Alternativas y referencias a verificar

1. **Preferencia de diseño**: subunidad H2A pura de codec JSON schema2 y manifest v2 en memoria local, sin escritura de archivos; H2B separada para SourceReader/preview/fingerprint/approval/egress, con permisos independientes. Facilita prueba de equivalencia v1 y evita activar egress al habilitar serialización.
2. **Referencia** RFC 8785 `https://www.rfc-editor.org/rfc/rfc8785`: serialización determinista y firmas/huellas; evaluar contra la implementación propia `Utf8JsonSink`, no copiar librería sin auditoría.
3. **Referencia** Android Storage Access Framework `https://developer.android.com/guide/topics/providers/document-provider`: salida a documento expresamente elegido; no autoriza escritura automática.
4. **Descartar** adaptar `PORTABLE_BUNDLE_V1` a schema2 sin subir profile, omitir `terms`, usar JSON en `sourceRef`, disparar egress automático, ocultar promoción/localidad, inferir divisas/vigencia o añadir una API paga.

## B4 — Puntos de decisión antes de congelar rutas

Separar H2A (DTO+sección JSON versionada y roundtrip local **sin salida**, perfil explícito distinto y serialización determinista) de H2B (SourceReader typed, PortabilityPreview/schema2, plan fingerprint v2, approval, redaction, writer/document y recibo externo bajo confirmación). **Rutas candidatas, NO autorizadas:** `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/PortableBundleJsonWriter.kt`; `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/PortabilityCore.kt`; suites `v5/maintenance/src/test/kotlin/...` y nuevos workflows con rutas exactas a inspeccionar. No tocar `:finance`, graph ACTIVE o V2 productiva. H2A puede requerir nuevo archivo codec en vez de modificar writer v1; verificar primero.

## B5 — Auditoría adversarial pendiente

T01 export v1 byte-equivalente y watermark v1 intacto. T02 schema2 con null físico/UNKNOWN/terms completos no pierde campos en codec/parse. T03 corrupción/oversize/foreign key falla sin salida parcial. T04 aprobación firmada con fingerprint de preview v2; cambios de datos o perfil invalidan aprobación y salida; revocación bloquea. T05 sourceRef hostil literal, sin acceso a file://, HTTP ni filtraciones de secretos. T06 export de moneda ARS/USD sin FX, vigencia UNKNOWN no inventada. T07 interrupción antes/durante/después de materializar: temp atomic, no compartir archivo incompleto, receipt solo tras resultado. T08 B7 CI dirigida/JUnit XML, regresión v1 y protocolo exacto. T09 Android DEV/G15 solo campaña separada y nueva autorización. Rutas/permisos aún sin congelar, pruebas H2 **NOT_RUN**.

## Gate siguiente

`H2_PREAUDIT_DESIGN_ONLY`. Antes de un permiso H2, completar B1–B5 verificables de la subunidad elegida, revisar los consumidores actuales y generación, congelar rutas exactas y superar guard documental. **NO emite READY, NO código H2 ni exportación con esta preauditoría.**
