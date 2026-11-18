# FARO W01 F1 · Corrección acotada de un caso de CI

## B1

SHA `8f283c74d53d8f1ff3dd7cfd4bc093a2a3e05d1d`. CI 44382213057 ejecutó 8 casos con un fallo de preparación. El caso `refusesMalformedAndAlteredAdditionalReviewText` lanzó IllegalArgumentException en la línea 222 al exigir una carga para un Review PENDING. La guardia 44382212893 pasó.

## B5

La implementación Workspace limita `load(context)` a estados admisibles de Apply. Usar la aprobación de fixture existente antes de cargar el envelope original. La prueba seguirá creando una revisión distinta con el texto alterado, aprobándola y comprobando que Apply la deniega sin registrar deuda. Se conserva íntegra la validación de PENDING, que ya ejercita el caso de transferencia y la regresión previa. Alcance de cambio único en el test. Los B2–B4 del permiso siguen vigentes.

## B7

RED_FIX de la misma unidad. El resultado de CI queda abierto hasta que la suite exacta y su XML estén verificados en el SHA de corrección. Sin cambios de producción ni de Android en esta corrección.
