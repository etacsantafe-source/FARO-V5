# FARO V5 · A2B0B3 · Corrección de la prueba de puntero

## B7 — RED_FIX

El SHA b638f077be08221e9d67f4a5580ae799f298d3bb pasó la guardia 45818068048. La CI dirigida 45818068599 compiló el código principal y falló en compileTestKotlin. El fixture ABA pasaba ActiveGenerationPointer a FileActiveGenerationPointer.compareAndSet, cuya firma admite StorageGenerationId. La segunda llamada debe usar moved.generationId. Conservar el puntero completo en la verificación de la negativa. No se ejecutaron suites ni existe XML original de este intento.

Corrección acotada al test y al preflight del workflow dirigido. Mantener las nueve rutas del permiso y el diff exacto de implementación inicial. Para RED_FIX de esta misma unidad, comprobar que las rutas cambiadas sean un subconjunto del permiso, que contiene las dos rutas afectadas. Exigir nuevamente la guardia, CI dirigida y XML original. No se abre C1 hasta B7 PASS.
