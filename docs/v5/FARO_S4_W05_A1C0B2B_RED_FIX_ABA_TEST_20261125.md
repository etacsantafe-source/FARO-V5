# FARO V5 · W05-A1C0B2B · Corrección de la prueba ABA

## B7 — RED_FIX

El código `e8fecbfd31d3a6563d02a0280cf708a3b9e4879a` tiene guardia 45547259594 SUCCESS. El run de regresión de etapa Finance9 45547259447, job 138979739409, compiló el código principal y falló en `:store:generation:compileTestKotlin` antes de JUnit. El log original indica ARGUMENT_TYPE_MISMATCH en `FaroV5Household12V9StagedReplayTest.kt` línea 270. `FileActiveGenerationPointer.compareAndSet` compara `StorageGenerationId?` y devuelve `ActiveGenerationPointer?`.

Corregir exclusivamente las dos llamadas de la prueba ABA para pasar `f.origin.generationId` y `other.generationId`. Se preserva el retorno del puntero en la prueba para conservar la comprobación de secuencia. Volver a ejecutar las cuatro suites y verificar sus XML y guardia en el nuevo SHA. El bloque sigue abierto y la aceptación anterior permanece sin certificar.
