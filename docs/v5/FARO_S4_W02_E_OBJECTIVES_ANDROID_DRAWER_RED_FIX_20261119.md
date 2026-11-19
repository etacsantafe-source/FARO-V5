# FARO V5 · W02-E · RED_FIX del selector del menú

## B7
- [API35 44449355484](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44449355484) FAILURE en `97e1b6d2d22011c0d1fe7e6607f223399f47f6a4`, job 135309205988. Guardia 44449355527 SUCCESS. La prueba avanzó más allá de Chat/crear, editar, progreso, corrección, paso, accesibilidad y pausa/reanudación. Detenida tras recrear la Activity, al reabrir Objetivos (líneas 98/114).
- El árbol de Compose tras recrear contiene el encabezado de pantalla Objetivos y la acción Objetivos en el menú. El selector por texto esperaba un único nodo y recibió dos, con bounds diferentes. Identificar el nodo que incluye la acción de clic antes de inyectar interacción. Mantener las aserciones de restore, fuente y los restantes pasos.
- RED_FIX limitado al test Android `S4W02ObjectivesAndroidTest.kt`. API35/JUnit en SHA de corrección, guardia y APK todavía pendientes. Navegación compartida 44449355491 e integración V2 44449355593 SUCCESS. Writer RELEASED epoch 1280 al empezar.
