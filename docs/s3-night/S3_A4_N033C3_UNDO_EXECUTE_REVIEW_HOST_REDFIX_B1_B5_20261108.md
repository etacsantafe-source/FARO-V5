# FARO V5 — Undo NEW Review host RED_FIX B1–B5
Fecha AR 2026-11-08. Misma unidad `S3_A4_N033C3_UNDO_EXECUTE_REVIEW_HOST`. Código RED `0fec033d21ec167926d29f1e00fa888bc1b07f60`. Evidencia `docs/s3-night/S3_A4_N033C3_UNDO_EXECUTE_REVIEW_HOST_B7_RED_FIX_20261108.md`.
## B1 — Hallazgo/criterio
- CI `#42498342148` falla exactamente en import de test línea 15, sin pruebas de Undo ejecutadas. Aceptación: JUnit 6/6 host REAL Workspace4/Reality8 y regressions exact SHA SUCCESS, sin introducir cambios de runtime.
## B2 — Comparación y reuso
- `SingleProcessMaterialWriteGate` ya pertenece a `:kernel:contracts` y se importa con wildcard en el test. REUSAR, eliminar import equivocado. V4.4 sin analogía material para un import JVM; no tocar legacy. Reutilizar todo el B1-B5 y código host anterior, no duplicar fixture ni lógica.
## B3 — Investigación
- Kotlin import resolution usa package declarado (no la carpeta que visualmente sugeriría un símbolo): `MaterialWriteGate.kt` declara `package ar.com.faro.v5.kernel.contracts`. Referencia código propio más precisa que dependencia externa. DESCARTAR: crear alias en `:kernel:apply`, mover clase de módulo, ampliar classpath o suppress de error. USAR eliminación de la línea sobrante.
## B4 — Delta exacto
- Único path: `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5Planning8UndoReviewedApplyHostT2Test.kt`. Quitar `import ar.com.faro.v5.kernel.apply.SingleProcessMaterialWriteGate`, conservar wildcard `contracts.*`. Ningún otro archivo funcional, tests ni workflow debe cambiar.
- Guard de protocolo: commit `FARO-UNIT:S3_A4_N033C3_UNDO_EXECUTE_REVIEW_HOST`, misma unidad RED_FIX, B7 posterior tras CI/LOGS/XML. No tocar 12 originales ni 6 Undo tests salvo falla nueva verificada.
## B5 — Adversarial y no regresión
- Corregir import NO concede permisos, no cambia el nuevo Review, la fuente COMMITTED ni Reality8 SQL. Aceptación prohibe dar por PASS sin JUnit XML exacto, o tomar el SUCCESS de un guard como éxito funcional. Si aparece fallo test semántico, nueva causa y B5, no prueba redundante.
- V2 productiva, S4, AUTO, FARO Noche OFF; 0 usuario/tap físico. Esta unidad sigue **READY_SCOPED_RED_FIX_IMPORT_ONLY**.
