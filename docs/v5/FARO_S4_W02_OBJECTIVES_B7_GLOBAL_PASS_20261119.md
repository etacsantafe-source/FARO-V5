# FARO V5 · W02-F · Auditoría integrada DEV

## B1
- Contrastar los cierres B7 de A a D, código y evidencias del SHA funcional E. La rama debe tener la revisión exacta auditada y el permiso de la unidad correspondiente. No cerrar el módulo con Android en cola ni con XML incompleto.
- Alcance de aceptación: Objetivos con contrato y persistencia, alta/edición y Review/Apply, recibos e idempotencia, avances, historial y correcciones, estados, pasos y referencias, Chat y API35.

## B2
- Reusar la marcha probada por W01 y S3, con guardia del repositorio, XML JUnit dirigido y APK DEV aislada. El flujo de V4.4 de cartera y avance trazable sirve de referencia. No retomar implementación de W01, V4.4 o S2.

## B3
- [GitHub Actions artifacts](https://docs.github.com/en/actions/tutorials/store-and-share-data) explica digest SHA-256 del ZIP. USAR el digest anunciado y validar descarga e integridad de XML/APK. DESCARTAR considerar verde un log aislado o reutilizar el mismo artefacto para dos SHA.
- [Android pruebas instrumentadas](https://developer.android.com/studio/preview/instrumented-test-suites) describe emulador y app bajo test. USAR runner API35 y escala 200%. Descartar aproximación JVM para certificar recorrido Android y cualquier campaña física en este alcance.

## B4
- Auditar diff de todos los checkpoints funcionales y permisos contra base `9c08023fb23aa7c0db9c59ffaf438ee547754bdd`. Contrastar contrato, estado DEV, reviewer, SHA, CI, digest ZIP y JUnit real. APK debe tener package W02 y el launcher propio sin permisos prohibidos. Guardar B7 final documental y consumir READY.
- Cerrar sólo si el recorrido Android y las regresiones adecuadas permiten declarar B7 DEV global. Conservar límites de evidencia por escenario JVM o Android y registrar fallas ajenas que se reproduzcan, sin reescribir sus unit tests.

## B5
- Revisar la prueba real de doble ejecución, stale y acceso cruzado; no inferir funcionamiento de permiso humano a partir de un mock de fuente. No publicar datos de usuarios, tokens, recibos o saldos reales. V2 productiva, Auto y Noche OFF. No operar G15 físico.
- Al finalizar confirmar HEAD, writer RELEASED, guardia del checkpoint y muestra exacta de evidencia. El permiso funcional debe quedar bloqueado después del cierre.

## B6
- SHA Android auditado `b32a6aa5b7eb7b953af3d0749b86ed0cdb043099`. Implementación E `bfbfa846`, reapertura de propuesta y lifecycle `c27607df`, recarga canónica al volver a Chat `97e1b6d2` y selector de menú de la prueba final `b32a6aa5`. El último cambio altera sólo la prueba. [Guardia funcional 44450546802](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44450546802) SUCCESS.
- [Android DEV API35 44450545982](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44450545982), job 135313105103 SUCCESS. GitHub Actions verificó 1/1 en JUnit XML, con suite y caso exactos, sin errores, fallas ni omitidos. Se ejecutó en emulador API35 con fuente 200%. El recorrido abrió Objetivos desde el menú, descartó una propuesta, confirmó y aplicó el alta desde Chat, editó el objetivo, registró y corrigió avances, agregó y completó un paso y comprobó persistencia tras pausa y recreación.
- [APK + XML 14496056141](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44450545982/artifacts/14496056141). Metadata enlaza el SHA funcional exacto. ZIP SHA-256 `56d1ac7082fff75e5827b65f69492f94230ef5b556634677769c786988acfd47`, APK SHA-256 `caa09fc29eb065ea980cad85d3ce943a6ba1610ebb6a6db7471847ff0c4311c9`. El job verificó package `ar.com.faro.alpha.w02objectivesdev`, launcher dedicado y ausencia de los permisos prohibidos por el workflow. Se comprobó el job, sus pasos, los logs del verificador XML y el digest publicado. El entorno de ejecución local se desconectó durante la espera; la comprobación del XML Android se apoya en el verificador exacto ejecutado por CI.

## B7
- **PASS GLOBAL ANDROID DEV** en el alcance de W02. W02-A [44440581194](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44440581194) 38/38 XML. W02-B [44441995588](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44441995588) 4/4 XML. W02-C [44444524112](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44444524112) 8/8 XML con regresión de B. W02-D [44445819526](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44445819526) 11/11 XML con regresiones B/C. Los cuatro artefactos JVM se descargaron y validaron por digest y JUnit.
- Cobertura de permisos cruzados, ciclos, stale, doble confirmación, corrección y restore G4 en JVM, e integración real Chat → Review → Apply → Objetivos en Android. Los grants de referencia del DEV son del proceso y revocables. No trasladarlos a un host productivo.
- Navegación de superficie [44449355491](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44449355491) e integración V2 [44449355593](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44449355593) SUCCESS sobre `97e1b6d2`. Regresión W01 Android [44447323509](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44447323509) 1/1 XML verificado sobre el primer código E. Las rutas W01, S2 y catálogo siguen intactas. Dos guardias históricos de suites W01 rechazaron el permiso W02 antes de correr su suite, 44447323186 y 44447323642. El fallo B2D0 preexistente sigue documentado en W02-A. Estos resultados no se acreditan como verde.
- V2 productiva OFF, W03 STOP y G15 físico fuera de alcance. Cierre documental y permiso consumido. Writer tras código RELEASED epoch 1282. Revalidar el writer del checkpoint documental al reanudar.
