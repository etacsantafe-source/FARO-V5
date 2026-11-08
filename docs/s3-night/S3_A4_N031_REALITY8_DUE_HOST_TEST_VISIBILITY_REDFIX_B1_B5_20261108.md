# FARO V5 — S3.A4 N031 host seam · test visibility RED_FIX B1–B5
Fecha AR 2026-11-08. Mismo `FARO-UNIT:S3_A4_N031_REALITY8_DUE_HOST_SEAM`; revisar SHA `e2bd195cb17ad114ace884324e9bda9a7936f7cc`. Único delta: prueba `:app`.

## B1 — evidencia y aceptación
- En `RealityStore.kt`, `schemaVersion()` y `integrityCheck()` son `internal` (no API para cliente Kotlin en módulo separado).
- En `FaroV5Reality8PlanningDueHostBindingT2Test.kt`, tres llamadas (dos schema, una integridad) no corresponden a `:app`.
- Criterio binario: sustituir `f.reality.schemaVersion()` por `f.reality.maintenance.inspect().schemaVersion` (2 ocurrencias), y `assertEquals("ok", f.reality.integrityCheck())` por `assertTrue(f.reality.maintenance.inspect().integrityHealthy)` (una). Mantener tests 6 y los originales 6+6 intactos; compilar :app y exigir XML 18/18 exactos.
## B2 — comparación y activos
- V4.4 no participa en la visibilidad Kotlin `internal`; NO importarlo. V5 Reality maintenance ya expone inspección certificada en schema7/8 sin ensanchar API. Kotlin `internal` restringe módulo de compilación y friend test del **mismo** módulo, no de otro.
- No modificar owner/Reality8 ni reescribir pruebas/flux host ya hechos, solo delta de visibilidad.
## B3 — investigación externa aplicada
- Kotlin official visibility https://kotlinlang.org/docs/visibility-modifiers.html : internal es visible dentro del mismo módulo, NO entre `:app` y `:store:reality`; USAR API pública.
- Android architecture data layer https://developer.android.com/topic/architecture/data-layer : acceder a fuente de verdad a través de puerto público de datos; ADAPTAR puerto `maintenance.inspect` ya implementado, no cambiar contrato.
- Alternativa A USAR `maintenance.inspect()`, información suficiente y sin ampliar superficie. Alternativa B DESCARTAR hacer tres funciones `public` solo para tests. Alternativa C DESCARTAR migrar test a `:store:reality` (rompe la comprobación host-integrated) o silenciar asserts. Ninguna dependencia/lib nuevo.
## B4 — plan mínimo
- ÚNICO archivo funcional permitido: `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5Reality8PlanningDueHostBindingT2Test.kt`.
- No tocar `RealityStore.kt`, fuente host/proyección, workflow ni guard. Commit con trailer exacto `FARO-UNIT:S3_A4_N031_REALITY8_DUE_HOST_SEAM`, parent permit READY y B7 anterior `RED_FIX`.
## B5 — amenazas y negativas
- Preservar schema7 DENY, datos reales SQLite schema8, pointer ABA, política independiente por owner, corte de host/handle, revocación a mitad de lectura y ausencia de mutación.
- Tras el cambio CI exacta, JUnit 6/6 host + 6/6 due original + 6/6 SPI Reality, sin skip/error/fail y mandatory seven-block guard. Si CI aun falla, RED_FIX nuevo estrecho según evidencia, nunca declarar PASS.
- V2 productiva/S4/G15 OFF y S3.A4 OPEN; auditoría final posterior B7 independiente.
