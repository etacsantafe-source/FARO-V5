# FARO V5 — S4 W00-D2 · B7 PASS scoped, Review compuesta READ_ONLY
Fecha: 2026-11-15 Argentina. Unidad `S4_W00_D2_CANONICAL_COMPOSITE_REVIEW_SCOPE_READ`, commit funcional exacto `8c329744fce9013d779ea6113dad8e3f0a6b84c8`. S4 W00 global OPEN; V2 productiva/Auto/Noche OFF.

## B1 — Verificación independiente
Baseline permiso READY documental `47ce40e2ad11eab440f0d9505780d8fd63a3e8d8`; guard de permiso `43776055980` SUCCESS y preflight de 3 rutas. Writer HELD epoch930 para B6 y RELEASED epoch930 con checkpoint `8c329744fce9013d779ea6113dad8e3f0a6b84c8`. Se cotejó HEAD remoto, guard del código `43776469882` SUCCESS y CI dirigida al mismo SHA.

## B2 — Reutilización
Se conservaron W00-A/B/C B7 PASS 12/12 (5+5+2), W00-D1 B7 PASS 11/11, contrato Workspace4 y Planning8 single-node DENY sin volver a ejecutar sus pruebas. No existe integración del lector D2 con Activity, host, Finance ni Household. No se copiaron runtime de V4.4 ni licencias externas.

## B3 — Alternativas
Se usó lectura READ_ONLY del plan canónico aprobado y dos payloads oficiales; se rechazó quitar la guarda single-node, otorgar grants desde Review y un doble Apply. La nueva clase no recibe puertos de aplicación de operaciones. Los `authorityId` derivados de capability/descriptor son etiquetas estructurales: NO una identidad/permiso de owner verificado.

## B4 — Código, CI y XML
Diff de `8c329744fce9013d779ea6113dad8e3f0a6b84c8`: exactamente tres rutas nuevas:
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W00D2CompositeApprovedReviewScope.kt`, blob `210d2c7b7d92ed7bea14b6f251ad1a008455381c`.
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W00D2CompositeApprovedReviewScopeTest.kt`.
- `.github/workflows/faro-v5-s4-w00-d2-composite-review-read-directed.yml`.
Run GitHub Actions `43776469935`, job `133003778315`, final status COMPLETED/SUCCESS exact SHA, Gradle `BUILD SUCCESSFUL in 4m 13s`. Paso de XML verificador SUCCESS con `tests=7 failures=0 errors=0 skipped=0`, lista de siete test cases exactos y marcador real `S4_W00_D2_CANONICAL_REVIEW_SCOPE_JVM_XML_PASS 7/7` en log. Artefacto JUnit `14186577055` (`faro-v5-s4-w00-d2-junit`), no expirado. Cobertura T01 Review canónica y scopes, T02 Pending/Discarded, T03 catálogo revocado, T04 generación/host stale, T05 cardinalidad/payload, T06 Workspace/envelope cambiado, T07 DAG inverso conserva identidades.

## B5 — Auditoría adversarial / NO probado
El lector solo retorna requisitos estructurales: no autentica principal/gesto humano, no detecta catálogo de caller fraudulento como fuente real, no tiene grant, nonce/lease ni fencing hasta SQL COMMIT. No certifica revocación cross-process, rollback, recuperación, AtomicApply multiowner real, host dual, Household, operaciones económicas externas, Android G15 ni producción. No hay modificación de Reality, DB, catálogo180, S2/S3, main ni V4.4. W00-C sigue DENY.

## B6 — Permiso consumido
El permiso de W00-D2 pasa a BLOCKED y contiene última revisión `PASS` al SHA de código `8c329744fce9013d779ea6113dad8e3f0a6b84c8` con esta prueba B7. El siguiente código funcional exige B1–B5 y permiso READY nuevos en un commit documental previo. Writer 931 solo para este B7+preauditoría D3.

## B7 — Disposición y checkpoint
**PASS_SCOPED_JVM_CANONICAL_READ_ONLY**: compilación, 7/7 XML, guard de código y tres rutas exactas VERIFICADAS. El B7/checkpoint documental POSTPUBLICACIÓN se confirmará SOLO al contrastar guard del SHA documental, 4 rutas docs exactas y writer RELEASED epoch931 last_checkpoint==HEAD. No declarar cierre global W00, CI Android ni campañas físicas por esto.
