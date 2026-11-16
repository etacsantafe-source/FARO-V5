# FARO V5 — W01-B2-C2 B7 RED_FIX de fixture Kotlin inline
Fecha AR 2026-11-16. Misma unidad S4_W01_B2_C2_FINANCE_RECEIPT_ONLY_RECOVERY_JVM;
código 883018665bcf823bf3703e3ec49a382848634917; guard 43885090377 SUCCESS.
CI 43885090652 COMPLETED FAILURE job 133360949206,
app:compileDebugUnitTestKotlin INAPPLICABLE_LATEINIT_MODIFIER,
línea 140 de S4W01B2C2FinanceReceiptRecoveryTest.kt.
Cinco tests NOT_RUN; ningún cambio a SQLite ni a producto.

## B1
ConversationId y FinanceAccountId son @JvmInline value classes.
Kotlin no permite declarar lateinit sobre variables locales de
tipo inline. Dos fixtures locales en lines 140–141 usaron
lateinit para chat y account. Strings/review/receipt restantes
son tipos referencia, sin ese problema.

## B2
Reutilizar el fixture estable de Rig("crash") y preservar
exactamente cinco tests y los asserts de receipt/ledger.
Evitar cambios a RealityStore, Workspace, coordinator y workflow.

## B3
Elegida: inicializar los IDs al declararlos con valores
determinísticos del fixture (chat-crash, finance-recover-crash),
comprobar que coincidan con el Rig y eliminar reasignación.
Rechazadas: alterar clases de dominio, borrar tests, usar
aserciones vacías o permitir un falso PASS.

## B4
Modificar SOLO
v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2C2FinanceReceiptRecoveryTest.kt:
dos declaraciones lateinit de tipos inline, dos asignaciones
reemplazadas por assertEquals; sin alterar casos ni workflow.
Repetir cinco tests exactos con XML+guard nuevo SHA.

## B5
Verificar que el crash de prueba siga tras COMMIT y antes
ObserveCommitted, y que al reabrir solo se reconcilie
receipt original. No asignar identidades artificiales
como permiso de App productiva. No repos, schema, catálogo
ni autoridad de otros owners modificados.

## B7
RED_FIX scoped (compilation, 5 NOT_RUN), NO PASS.
Permiso READY solo para corrección de misma unidad y B7 final
cuando XML 5/5, guard y writer RELEASED se verifiquen.
