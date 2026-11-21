# FARO V5 — W04-C1b2H1 · Portabilidad comercial tipada Evidence2 STAGED · B7 PASS

Fecha local: 2026-11-20. Unidad `S4_W04_C1B2H1_STAGED_TYPED_PORTABILITY_DEV`.
Código certificado `db4fdd3485deff9ab36aee9b5335b6328f90c4ba`, parent permiso READY `c8aba02926fd157ae9b3c919bb927736c8dcf0b9`. Preauditoría B1–B5 `docs/v5/FARO_S4_W04_C1B2H1_TYPED_PORTABILITY_B1_B5_20261120.md`, blob `fcfea3ffd48b6c0ab8cd999f2c5f174fade3a0bc`. READY EXACT4, gate documental `44827940406` SUCCESS.

## B7

**PASS_SCOPED_STAGED_DEV_H1.** Dos ejecuciones del SHA exacto:

- Guardia `44846516826`: COMPLETED SUCCESS, job `seven-block-evidence`, 14 self-tests del gate y protocolo B1–B5/preflight validados. No bypass.
- CI H1 `44846516892`: COMPLETED SUCCESS, job `136667453280`. Preflight `FARO_PROTOCOL_PREFLIGHT_PASS` para las CUATRO rutas con HEAD^ `c8aba02926fd157ae9b3c919bb927736c8dcf0b9`; Gradle `:evidence:test` `BUILD SUCCESSFUL in 2m 30s`; JUnit XML validado por script con `(tests=8,failures=0,errors=0,skipped=0)`, salida literal `W04_C1B2H1_STAGED_TYPED_PORTABILITY_JVM_XML_PASS 8/8`. Artefacto `14683143016`, `w04-c1b2h1-staged-typed-portability-xml`, 8384 bytes, no expirado al verificar.

Rutas funcionales EXACTAS del commit (sin documentación ni permiso mezclados): `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidencePortabilityPort.kt`; `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`; `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceStagedTypedPortabilityTest.kt`; `.github/workflows/faro-v5-s4-w04-c1b2h1-typed-portability.yml`.

Se incorporó proyección determinista `faro-portability-commercial-v2` sobre snapshot STAGED Evidence2 opt-in, sin Identity/Idempotency/PayloadFingerprint de ejecución; diferencia ausencia física `terms=null` de fila explícita `terms=UNKNOWN`, orden estable por valores observacionales y fingerprint de terms, lectura protegida por preflight schema2, integridad/FK y límite 100000. Las ocho pruebas nuevas validan gates v1/v2, separación de estados, exclusión de identidades, orden independiente del ID/inserción, variación de campos de términos, sourceRef hostil literal y USD sin FX, rechazo por fingerprint corrupto, condiciones inválidas/bound y snapshot repetible sin mutación.

El puerto legacy `CommercialEvidenceStore.portability` continúa FAIL-CLOSED en schema2; `PORTABLE_BUNDLE_V1` no se alteró. No se modificaron maintenance, generación, FBS1/FBS2, Finance, V2 productiva, UI, Android ni main; no hubo exportación, egress, prueba física, cambio de puntero ACTIVE ni autorización de intercambio de datos. H1 **no constituye** backup portable ni autoriza JSON/schema2 export. La verificación es JVM STAGED DEV, no aceptación end-to-end ni física.

**Resultado:** B7 PASS acotado; consumir READY H1, mantener writer global RELEASED tras checkpoint. Siguiente H2: diseño B1–B5 propio para codec JSON versionado, perfil/preview/fingerprint/aprobación y salida explícita; **NO READY, NO B6, NO EXPORT**. C1b/C1 global OPEN; C2/C3 pendientes. No repetir H1 ni unidades anteriores.
