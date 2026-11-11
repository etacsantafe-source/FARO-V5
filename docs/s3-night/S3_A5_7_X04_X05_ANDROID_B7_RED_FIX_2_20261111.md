# FARO V5 — S3.A5.7 X04/X05 Android — segunda auditoría B7 RED_FIX

Unidad: `S3_A5_7_X04_X05_ANDROID_GUARDADOS`. Código revisado: `84d983d4c0cbe193de8e5237e4bc6d8272e453a5`.
Guard protocol: run `43074404482` SUCCESS. Android run `43074404487`, job `130710347442`, FAILURE.

## B7 — RED_FIX segundo fallo real de compilación

- La primera corrección de import está aplicada. `:app:assembleV2CoreDev` terminó; `:app:compileV2CoreDevAndroidTestKotlin` falló.
- Error de compilador comprobado: `NO_VALUE_FOR_PARAMETER: No value passed for parameter 'atLeastOneRootRequired'`, en `FaroV5S3A57BookmarksUiTest.kt:198`.
- Motivo delimitado: `onNodeWithText("Enviar").fetchSemanticsNodes()` no corresponde a la API de interacción de nodo individual; es una consulta de colección. El patrón en este mismo test y en los tests DEV ya verdes es `onAllNodesWithText("Enviar").fetchSemanticsNodes().isNotEmpty()`.
- Corrección permitida en la misma unidad: reemplazar sólo esa llamada por `onAllNodesWithText("Enviar")`; conservar el resto de los dos tests y workflow.
- X04/X05 continúan `0/2 NOT_RUN`: sin emulador, sin Android instrumentación ni APK de test final. No declarar PASS ni atribuir culpa a SQL/ACL.
- Nuevos commits funcionales sólo después de esta B7 documental, con writer CAS adquirido y permiso mismo unit READY; CI API35 2/2 exacta, guard, APK y XML son obligatorios para B7 PASS.

Ningún componente productivo, V2, A3, S4, Auto/Noche, main o Moto G15 se modificó.
