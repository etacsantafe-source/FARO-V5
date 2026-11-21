# FARO V5 — W04-D2C1 · WORK TURBO con 9% semanal restante

## Misión única
Terminar **exclusivamente W04-D2C1 B6–B7** (notas tipadas de condiciones y beneficios diferidos de SmartCompra), una sola unidad pequeña JVM DEV de 8 pruebas. No repetir W1/W2/W3, W04-A/B/C/D0/D1/D2A/D2B/D2C0 certificados. Repo etacsantafe-source/FARO-V5, rama work/s3-night-20261102. Antes de escribir: AGENTS.md; docs/v5/FARO_CONTINUITY_POINTER.json; docs/v5/FARO_PROTOCOL_WORK_PERMIT.json; docs/v5/FARO_S4_W04_D2C1_DEFERRED_BENEFITS_B1_B5_20261121.md (blob b925f6d31f8bfe06b1ce55357d307c800c826bc1); este handoff. B1–B5 preparados, esperar guardia READY y preflight antes de B6. El presupuesto declarado por usuario es 9% semanal, 92% diario. No iniciar unidades adicionales.

## B6: exactamente tres rutas
1. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04DeferredCommercialBenefitNotes.kt
2. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04DeferredCommercialBenefitNotesTest.kt
3. .github/workflows/faro-v5-s4-w04-d2c1-deferred-notes.yml

Motor Kotlin PURE in-memory annotate(D2C0 Summary, List<ExplicitNote>) -> Decision, sin I/O, sin dependencia, sin DB, sin host Android. Fences cart/source/asOf exactos. Nota aportada por usuario, GroupKey real del resumen, BenefitKind CASHBACK_FUTURE / INSTALLMENT_CONDITION / BANK_ELIGIBILITY / OTHER_UNVERIFIED, details/sourceRef opcionales literales de longitud <=1024; ninguna nota puede llevar cantidad de dinero, fecha de cobro, confirmación ni estado PAID. Por grupo/kind duplicado => rechazar. Validar estructura de summary, no reejecutar contratos de otros owners, ordenar output determinísticamente, listas inmutables, estado REVIEW_REQUIRED_UNVERIFIED o UNCLASSIFIED_NO_CLAIM, dato Provenance USER_DECLARED_UNVERIFIED, no prometer ahorro o cashback. Información solamente; no plan de compras, FX, Finance, pago, cuotas efectivas, stock, egress, Apply, recibos o V2 productiva.

Implementar 8 JUnit5 T01–T08 exactamente según B5, Java 21 Android SDK37 y workflow dirigido
:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotesTest
Exigir XML (tests=8 failures=0 errors=0 skipped=0), 8 names distintos, artifact real. FIRST functional commit changes ONLY exact3; commit message includes FARO-UNIT:S4_W04_D2C1_CONDITIONAL_BENEFIT_NOTES_DEV; workflow performs parent READY preflight on EACH changed path, accepts 1..3 paths only during RED_FIX (first commit 3). No cambiar D2C0, D2B, Evidence, Finance, control docs in code commit.

## B7/stop
Revalidate HEAD, remote writer CAS and permit. Acquire writer before modifying ANY file, release during CI. Confirm guard and directed CI on functional SHA, inspect actual XML downloaded independently, then register B7 PASS in docs-only commit, consume READY to BLOCKED and release writer. If fail, document RED_FIX and fix only same unit; if 9% weekly credits not enough, checkpoint real estado, writer RELEASED and STOP without adding workstreams or inventing B7 PASS. G15 NOT_RUN. D3/D4/E/F unapproved.
