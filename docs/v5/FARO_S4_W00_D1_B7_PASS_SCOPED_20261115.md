# FARO V5 — S4 W00-D1 · B7 auditoría final independiente PASS acotado
**Fecha:** 2026-11-15 AR. Unidad `S4_W00_D1_SCOPED_ADMISSION_SHAPE_VALIDATOR`. **Código certificado SHA** `caf4d2c0bb187e93f667b5a7a7f4eabd575ecc79`. S4 formal OPEN solo W00, V2 productiva/Auto/Noche OFF; main/V4.4/S2/S3 intactos.

## B1 — Recuperación, reglas y baseline
Se verificó AGENTS.md, puntero/permiso, protocolo siete bloques y HEAD real. Baseline `237bbc048f2d9089becd73e7596ef06c7a45572c` con W00-D DOC_ONLY B7 y guard `43769501383` SUCCESS, writer RELEASED epoch924.
La autorización humana nueva «Autorizo» se interpretó como **W00-D limitado**, NO Household ni pagos. B1–B5 individuales en `docs/v5/FARO_S4_W00_D1_ADMISSION_SHAPE_B1_B5_20261115.md`, blob `b82ebbfec2f1528cabbb455f678be795322de659`, permiso READY exactamente 3 rutas en parent `79039a2cf12d45e9d9add67fb1b2f75d30396b3b`, guard de preflight `43770906820` SUCCESS. Writer CAS epoch925 DOC y epoch926 B6, ambos liberados.

## B2 — Reutilización
No repetir W00-A 5/5, W00-B 5/5, W00-C 2/2 (PASS scoped 12/12); conservar host Planning8 SINGLE-node con `nodes.size==1`, denegación multiowner, Reality/Workspace/Apply/recovery inalterados. No copiar runtime V4.4 G0D3 ni catálogo HOUSEHOLD a código productivo. W00-D1 no ejecuta Apply.

## B3 — Alternativas y límites
Se eligió un validador `internal` de forma/frescura sin conexión al host y sin emisión de grants, en vez de quitar la guardia `singleOrNull`, duplicar owners/DB/writer o conceder autorización por catálogo. Referencias oficiales SQLite transacciones/aislamiento/WAL y AndroidX figuran en B1–B5; no dependencia nueva. La comprobación de la forma **no equivale a permisos productivos ni fencing cross-process**.

## B4 — Evidencia B6 por SHA
Commit `caf4d2c0bb187e93f667b5a7a7f4eabd575ecc79`, exactamente estas 3 rutas:
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W00D1AdmissionShape.kt`, clase `internal` `S4W00D1AdmissionShape`, chequeo de fingerprint, DAG de dos nodos, operationId, scope/authority, principal declarado por host, generación, host epoch, policyEpoch, leaseNonce únicos, doble lectura fail-closed.
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W00D1AdmissionShapeTest.kt`, 11 tests JUnit JVM (uno positivo SOLO estructural, diez negativos), sin SQLite ni port Apply/host UI.
- `.github/workflows/faro-v5-s4-w00-d1-admission-shape-directed.yml`, workflow dirigido; sin modificar archivos preexistentes de producción, host, catálogo180, schema, Gradle, S2/S3, APK ni main.
GitHub Actions W00-D1 **run `43771193795`**, job `132987258037`, conclusion SUCCESS al mismo SHA. `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W00D1AdmissionShapeTest`: `BUILD SUCCESSFUL in 4m 3s`; verificador JUnit XML **SUCCESS**, `tests=11 failures=0 errors=0 skipped=0`, conjunto exacto de nombres. Log `S4_W00_D1_ADMISSION_SHAPE_INERT_XML_PASS 11/11`. Artefacto `14184925212` (`faro-v5-s4-w00-d1-junit`), visible y no expirado.
Guardia del SHA de código `43771193752` SUCCESS; writer RELEASED epoch926 con last_checkpoint igual al SHA de código.
Tests: twoDistinctScopedObservationsMatchImmutablePlanWithoutGrantingApply; missingSecondObservationFailsClosed; crossObjectScopeSwapFailsClosed; stalePolicyEpochFailsClosed; staleGenerationFailsClosed; hostLifecycleEpochDriftFailsClosed; reusedNonceAcrossOwnersFailsClosed; reviewFingerprintMismatchFailsClosed; singleNodePlanCannotBePromotedToComposite; duplicateAuthorityCannotImpersonateSecondOwner; grantRotatingDuringValidationFailsClosed.

## B5 — Auditoría adversarial y límites NO certificados
La clase consume requisitos/observaciones que el **caller** suministra. No demuestra que ese caller sea confiable, ni autentica consentimiento humano ni vincula un owner REAL con el contrato oficial. Un retorno true certifica solo estructura observada, **no concederá Apply/COMMIT**. No protege revocación cross-process, race final-check-to-SQL-COMMIT, restore/ABA externo, crash/power loss, permisos productivos ni un commit Household+Finance. NO se ejecutó campaña G15, ni CI Android de producto para esta unidad. Los diez W00D-T01–T10 de prueba transaccional del diseño padre siguen PENDIENTES, no sustituibles por los 11 tests de shape.
No habilitar W07; requiere W06 PASS REAL con Household implementado y autorizado aparte. W00 global y host dual productivo continúan OPEN.

## B6 — Publicación de auditoría y consumo de permiso
Este B7, el puntero de continuidad y el permiso W00-D1 CONSUMED/BLOCKED se publican como un solo commit DOC_ONLY bajo writer CAS epoch927. No confundir la guardia de código con la guardia del checkpoint documental posterior. V2 productiva OFF, sin automatización ni secretos.

## B7 — Disposición y cierre verificable
**PASS_SCOPED_JVM_SHAPE_ONLY** de `S4_W00_D1_SCOPED_ADMISSION_SHAPE_VALIDATOR`: suite 11/11, CI y guard del código SUCCESS. No implica PASS de W00 global ni habilitación de host compuesto.
Para confirmar checkpoint final POST-PUBLICACIÓN exigir HEAD exacto, diff de 3 documentos, guard nueva del SHA documental SUCCESS y writer RELEASED epoch927 last_checkpoint==HEAD. Si falta, estado WAIT_CI/BLOCKED, sin liberar unidad sucesora por herencia.
