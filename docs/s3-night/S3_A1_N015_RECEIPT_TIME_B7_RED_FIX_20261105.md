# FARO V5 — N015 Reality+backup B7 RED_FIX · 05/11/2026

## B7 AUDITORÍA RED_FIX
Código inspeccionado: `78a5e9beaa3a2ef946fd6b27d7c3def36a274adf`. Workflow guard #42106005370 SUCCESS. CI G4 #42106005289 **FAILURE** en `:store:reality:compileTestKotlin`: `Unresolved reference 'assertTrue'` exactamente en `RealityBackupFilteringT2Test.kt:165`; falta import `org.junit.jupiter.api.Assertions.assertTrue`. CI N015 #42106005190 reproduce el mismo fallo de compilación. Los tests **NO** fueron ejecutados, no decir PASS. N015 B6 PENDING.

## B5 RED_FIX previo a corregir
Mantener exclusivamente unidad `S3_A1_N015_RECEIPT_TIME_STORAGE_BACKUP`. Agregar import faltante en su archivo test y actualizar expectativa literal de `S3_REALITY_RECEIPT_XML_PASS` en workflow de 2/2 a 3/3: hay test `rollbackNeverExposesRecordedReceiptTime` nuevo. Conservar exigencia cero fallos, errores, skipped. Añadir cobertura B6 dirigida de FBS1 v1/v2, FBS2 y manifest/tamper, y verificar mediante CI exacta; no modificar datos externos ni activar History/Home. Los cambios extra sólo bajo rutas del permiso; no exceder ni confiar en un código que no compiló.

**Dictamen:** RED_FIX autorizado solo para el mismo N015 storage unit; no B7 PASS, no S3.A1 cerrado. FBS1 antiguo y catálogo 172 no se cambian silenciosamente; V2 productiva, S4, Noche y Auto OFF.
