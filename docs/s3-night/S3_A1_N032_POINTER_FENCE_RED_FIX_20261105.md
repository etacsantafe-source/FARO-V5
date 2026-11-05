# FARO V5 — S3.A1 N032 · CI RED_FIX acotado (05/11/2026)

## B5 RED_FIX — hipótesis nueva demostrada
El commit funcional `bd5780fc4d7960940ecd7e906aa79f1c07e42e93` compila y ejecuta la clase JUnit. CI dirigida [42102674310](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42102674310) terminó **FAILURE** en el paso de verificación del XML, no en la compilación ni los tests. El log exacto dice `S3_HOME_XML_FAIL (11, 0, 0, 0)`: se ejecutaron los 9 tests históricos y 2 nuevas negativas de A→B→A, sin errores/fallos/skips, pero el gate aún exige literalmente `(9,0,0,0)`. `Android SDK compile platform` y `Compile app and run Home notice tests` SUCCESS; la verificación de manifest fue SKIPPED por fail temprano. El guard protocolario [42102673780](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42102673780) SUCCESS para ese código.

**Corrección mínima, sin bajar criterios:** en `.github/workflows/faro-v5-s3-home-notice-directed.yml` actualizar *solo* el total esperado de Home `(9,0,0,0)` a `(11,0,0,0)` y la salida `S3_HOME_NOTICE_XML_PASS 9/9` a `11/11`. Exigir exactamente 11 y cero fallos/errores/skips; mantener todas las demás suites, comprobaciones, runner, Java, SDK, manifest y límites sin cambios. Ninguna ampliación de permisos del app, operación ni esquema.

**B6 próximo:** commit únicamente del YAML, ejecutar workflow disparado por su propio cambio, comprobar XML exactos y que la comprobación de manifest vuelva a ejecutarse. **B7 final:** sólo PASS cuando el run de corrección sea SUCCESS completo; no heredar PASS de build anterior.

## B7 RED_FIX — auditoría independiente de `bd5780fc`
Diff acotado: `FaroV5CompositionRoot.java`, `FaroV5HomeNoticeBindingFactory.kt`, `FaroV5HomeNoticeBindingFactoryTest.kt`. Production Home usa `createPinned` con igualdad completa de `ActiveGenerationPointer`, y el graph V1 también fija secuencia antes de abrir propietarios. 2 pruebas JUnit nuevas de ABA tras publicar y durante una lectura real del owner; `11/11` constan en XML sin fallos, pero workflow final rojo por expectativa antigua. Auditoría **RED_FIX**, NO PASS, únicamente para el mismo `S3_A1_N032_POINTER_SEQUENCE_FENCE`. Se evita volver a modificar funcionalidad y se limita la siguiente escritura al control XML.

N015/N031 y Guardados siguen sin owner completo; A1 global OPEN. S2 8/8 congelada, S3.A2 5/5 automatizado, catálogo 172 intacto, V2 shipping OFF, Noche/Auto OFF, S4 OFF, G15 no usado.
