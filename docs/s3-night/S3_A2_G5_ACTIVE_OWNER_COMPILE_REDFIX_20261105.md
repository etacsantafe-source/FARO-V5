# G5 V2 Core: error de import — B1–B5 RED_FIX (05/11/2026)
Unidad: `S3_A2_G5_REAL_ACTIVE_OWNER_DIRECTED`. Código base: `d1a9c68bab7d99e93c1aae60c022a40c4955848f`.

## B1 EVIDENCIA
Actions #42061296590 **FAILURE**, paso M2 directed: `:app:compileV2CoreDevUnitTestKotlin`, `Unresolved reference 'StorageGenerationId'` en el test nuevo línea 7, importado desde `ar.com.faro.v5.kernel.contracts`. No hubo pruebas G5 ejecutadas ni XML 1/1. Test V2 Core histórico usa import `ar.com.faro.v5.maintenance.*`.
## B2 COMPARACIÓN
V4.4 no tiene este tipo DEV de generación. La referencia interna es el test ya compilado `FaroV5V2CoreDevAuthorityTest.kt` y el contrato `maintenance`. No cambiar producción ni suites previas.
## B3 INVESTIGACIÓN DE ALTERNATIVAS
ELEGIDA: corregir import a `ar.com.faro.v5.maintenance.StorageGenerationId`. DESCARTADAS: mover tipo de paquete o declarar tipo ficticio de test; amplían alcance y debilitan guardas. Sin nuevas librerías.
## B4 UNIFICACIÓN
Cambio EXACTO de una línea en `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5G5ActiveFourOwnerSearchTest.kt`; mantener workflow, tests de runtime y shipping intactos. Ejecutar CI para XML V2 10/10 + G5 1/1, T4 7/7 y manifests.
## B5 AUDITORÍA ADVERSARIAL
Un error de import no prueba ni niega el funcionamiento del producto: **no declarar G5 PASS**. El commit `d1a9c68` queda auditado B7 **RED_FIX** sólo como código no compilable, autorizar la corrección dentro de la misma unidad. No modificar DB Android, ROOT, backup, V2 shipping, main ni S2. Writer Git CAS exclusivo.
