# FARO V5 — W05-A1B3C2A · B7 PASS scoped · Evidence2 SQL11 STAGED · 2026-11-24

Unidad: `S4_W05_A1B3C2A_FBS2V8_EVIDENCE2_SQL11_STAGED_REPLAY_DEV`. Rama `work/s3-night-20261102`. Alcance exclusivo: restauración FBS2v8 Evidence2 hasta Reality11 **STAGED DEV**, sin cutover ni stock real.

## B1–B5 heredados y RED_FIX anterior
Se conservaron las auditorías verificadas: `docs/v5/FARO_S4_W05_A1B3C2A_EVIDENCE2_FBS2V8_SQL11_STAGE_B1_B5_20261124.md` y `docs/v5/FARO_S4_W05_A1B3C2A_REDFIX_B0_EVIDENCE2_PREAUTH_45512506948_20261124.md`. En el código anterior `5a99cdfcb32c296b872938280d221740f6fb0e4b`, la primera inspección sin versión forzaba Evidence1 y rechazaba el target sellado Evidence2 antes de autenticar, confirmado en el XML anterior (Evidence2 1/6, Evidence1 10/10).

## B6 — Dos rutas exactas implementadas
- Código `25fb05a31dde9f692f3212f21aa21243b5c7cf21` con padre READY `089a1c00aea859c1d9e5d186b2f9919e0b17dab4`.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8BaseReplay.kt`: inspección previa de ambos perfiles con los validadores estrictos y read-only existentes, exigir XOR (exactamente uno), revalidar host/pointer entre ambas comprobaciones. No confiar en metadatos del ciphertext antes de autenticar. Se conserva la segunda comprobación estricta de perfil contra el archivo **después** de autenticación FBS2v8.
- `.github/workflows/faro-v5-s4-w05-a1b3c2a-evidence2-stage.yml`: agregar la ruta anterior al filtro de activación, sin tocar pruebas ni relajar los asserts de XML.
- Writer CAS global adquirido epoch 1910 y RELEASED tras código, sin mantenerlo durante CI. Sin cambios en C1A/C1B/C1C, `main`, V4.4, permisos productivos o actividad Android.

## B7 — CI y evidencia exacta
- Guardia obligatoria: Actions `45514567307` **SUCCESS** para SHA `25fb05a31dde9f692f3212f21aa21243b5c7cf21`. Preflight del workflow dirigido contra padre READY: dos rutas **PASS**.
- CI dirigida: Actions `45514567419`, intento **2**, job `138871134957`, **SUCCESS**; Gradle: `BUILD SUCCESSFUL in 3m 5s`. Paso JUnit confirma suites Evidence2 **6/6** y Evidence1 **10/10**, cero failures/errors/skips. El parser del **XML JUnit original** imprimió `FARO_W05_A1B3C2A_EVIDENCE2_SQL11_STAGED_XML_PASS_16_16`.
- Artefacto XML original de GitHub: `14976404131`, nombre `w05-a1b3c2a-evidence2-sql11-staged-xml`, 1639 bytes reportados por GitHub. No se atribuye SHA256 independiente al ZIP.
- Intento 1 de la misma CI: FAILURE por Maven Central HTTP 429 en dependencias Kotlin, **antes de compilar/tests**, sin XML; se reintentó el job fallido sin cambio de código. Intento 2 PASS.
- **Excepción de alcance documentada:** el workflow histórico `faro-v5-s4-w05-a1b3b0-fivestore-base-replay.yml`, run `45514567315`, quedó FAILURE únicamente en su preflight para la unidad antigua B0 al activarse con el archivo BaseReplay tocado por C2A; sus 8 tests no se ejecutaron. Esto NO es CI global verde ni una nueva certificación de B0. El guardia principal y la suite dirigida C2A sí son verdes. La corrección del trigger histórico, si se necesita, requiere auditoría/permiso propios; no expandir este scope.
- La CI V2 core ajena al alcance no certifica C2A. No se realizó campaña física.

## Disposición
**B7 PASS SCOPED** para W05-A1B3C2A Evidence2 SQL11 STAGED DEV: XML 16/16 y guardia real PASS. **A1 global OPEN; stock real OFF; V2 productiva OFF; pagos OFF; Auto/Noche OFF.** C2B (preflight/cutover Evidence2) y C2C (recuperación/reopen Evidence2) requieren unidades B1–B5 y permisos nuevos. No repetir C2A ya certificado.
