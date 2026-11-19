# FARO V5 · W02-C · B7 RED_FIX

## B7
- SHA `a297b8358c5e9b8d5cf3ef413e3bace58ecd02ae`, guardia 44443303206 SUCCESS. [CI 44443303274](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44443303274) FAILURE en el conteo exacto. XML 14492587172 inspeccionado: 3/3 ejecutadas en ObjectiveHistoryTest, 4/4 en regresión de ObjectiveReviewHostTest. Falta lifecycleIsExplicitAndEditsKeepTheJournal.
- La función Kotlin con cuerpo por expresión termina en assertThrows, que devuelve la excepción capturada. El método JVM no devuelve void y JUnit no lo descubre. Cambiar a cuerpo de bloque para que devuelva Unit y mantener las cuatro aserciones previstas por el workflow. No ajustar el conteo esperado para acomodar el caso omitido.
- RED_FIX limitado a `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W02ObjectiveHistoryTest.kt` con el mismo permiso W02-C. Verificar guardia del checkpoint antes del cambio y CI/XML en el SHA de corrección antes de emitir PASS. Writer tras SHA de código RELEASED epoch 1268.
