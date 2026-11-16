# FARO V5 — S4 W01-A · B7 RED_FIX acotado: fixture de ID duplicado
Fecha local 2026-11-15. Unidad `S4_W01_A_FINANCE_MANUAL_DRAFT_PREVIEW`.
Baseline de código `30c8e5e8079d4c2a5d81fd848b253cc26243e67c`, guardia protocolo `43780897666` SUCCESS SHA exacto.
Directed JVM `43780897721`: **FAILURE**, `10 tests completed, 1 failed` en
`FinanceManualDraftPreviewTest.validPreviewsDoNotMutateSnapshotOrGrantApply`,
`FinanceManualDraftPreviewTest.kt:77`, antes de ejecutar el step XML final.
El job sí compiló la implementación y todos los demás nueve tests pasaron.

## B1
Fuente de falla GitHub Actions run `43780897721`, job `133017593137`, logs; test fuente
`v5/owner/finance/src/test/kotlin/ar/com/faro/v5/owner/finance/FinanceManualDraftPreviewTest.kt`.
Línea 75 llama `accountPreview(opening=0L,snapshot=before)`, cuyo helper usa
`accountIdRaw="account-existing"`, mientras `before = snapshot()` ya tiene ese mismo
`accountId`. El preview devuelve `null` por duplicación correctamente.
## B2
Se conserva el test `duplicateAccountIdDenied` (PASS) y el control del owner.
Ningún cambio a FinanceAccountCreate/EntryRecord, Reality, host, esquema ni catálogo180.
## B3
Corrección mínima de fixture; no se cambia semántica. El ID de cuenta nueva debe ser distinto
del existente para probar una preview válida. Referencia SQLite atomicidad sin relación
con este fallo: https://www.sqlite.org/lang_transaction.html. Repetir solo suite W01-A.
## B4
Modificar exclusivamente el test de la unidad, con parámetro `accountId` opcional
en helper y el caso positivo pasando `account-new`; conservar valor por defecto en
pruebas negativas. No aflojar validación duplicados ni convertir opening desconocido en 0.
Rutas restantes del permiso actual se mantienen exactas.
## B5
Caso adversarial: el test de duplicado tiene que seguir rechazando mientras el test
de ID realmente nuevo acepta; resultado de preview nunca Receipt ni Authority.
No introducir escrituras, importes reales, grants, UI, V2 productiva ni G15.

## B7
**RED_FIX** de la misma unidad; no es PASS. Prueba W01-A original 9/10,
CI `43780897721` FAILED y guard `43780897666` SUCCESS.
Autoriza solamente una corrección y nueva CI exacta, sin tocar código productivo.
Tras RED_FIX debe verificarse de nuevo 10/10 XML exacto y guard por SHA para
convertir B7 en PASS. W01 global permanece OPEN.
