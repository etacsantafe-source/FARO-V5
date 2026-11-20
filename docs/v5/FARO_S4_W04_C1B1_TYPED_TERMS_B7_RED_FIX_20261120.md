# FARO V5 — W04-C1b1 · B7 RED_FIX técnico, sintaxis del opener Evidence2

Fecha 2026-11-20. B6 primer commit `825ca56f3cf15e792e9cda3ce72df092d824ca80`, unidad `S4_W04_C1B1_STAGED_TYPED_TERMS_DEV`, scope exact4 B1–B5 verificado en blob `912e7fe6916de994532a4d57ba16d6473a5d654f` y guard `44811911050` SUCCESS.

## B7
**RED_FIX y NO PASS** del primer B6: CI dirigida `44812383640`, SHA `825ca56f3cf15e792e9cda3ce72df092d824ca80`, FAILURE. Job `136551961322`, `:evidence:compileKotlin` falla en `CommercialEvidence.kt:801` con `Syntax error: Expecting member declaration`. Preflight exact4 y protocolo guard `44812383513` SUCCESS; **no JUnit XML ni 9/9** por fallar compilación. No repetir C0A/C0B/C1a.

Auditoría adversarial específica: el bloque `if (stagedTypedTermsV2) { ... }` de creación de `commercial_evidence_quote_terms` quedó inmediatamente *después* del cierre de `initializeSchema()`, fuera de método. No es un bug de SQLite ni permiso; error sintáctico localizado y comprobado por CI + fuente.

**Corrección autorizable solo para la misma unidad y ruta:** mover ese bloque DENTRO de `initializeSchema()` antes de su llave de cierre, manteniendo `BEGIN IMMEDIATE`, `COMMIT/ROLLBACK`, preflight y rechazo schema2 en opener legacy. No cambiar otras unidades, owner, Finanzas, C1a, ni habilitar backup de v2. Luego CI Kotlin/JVM exacta, XML 9/9 y B7 independiente. Si surge un fallo diferente se registra nueva evidencia real, no se inventa PASS.
