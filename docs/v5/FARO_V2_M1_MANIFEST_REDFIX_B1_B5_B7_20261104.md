# FARO V5 — M1 verifier B7 rojo y RED_FIX de manifiesto DEV (04/11/2026)
Unidad **M1_MANIFEST_VERIFIER**; scope previo `.github/workflows/faro-v5-v2-core-directed.yml`; próximo scope exacto `.github/workflows/faro-v5-v2-core-directed.yml` y `v5/app/src/v2CoreDev/AndroidManifest.xml`.
Fuente HEAD de la prueba `42ca878a9c3e86640546ee08112c69fd661f14c2`. No modifica S2/producción/V4.4/main. Estado **RED_FIX**, no PASS; Noche/Auto/V2 productiva OFF, S4 no abierta.

## B1 EVIDENCIA — causa exacta y pruebas conservadas
- CI [#41881443958](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41881443958): **FAILURE GLOBAL** sobre SHA `42ca878a9c3e86640546ee08112c69fd661f14c2`. Compilación y manifest packaging PASSED; M2/M3 `V2_CORE_M2_M3_LOCAL_CHAT_JVM_PASS 4/4`; T4 debug `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7` (logs exactos). No afirmar aislamiento M1 o M2B porque último step falló.
- `V2_CORE_M1_PACKAGED_MANIFEST_SELECTED debug` y `... v2CoreDev`: existen una única ruta empaquetada por variante. El selector M1 recién corregido FUNCIONA y no debe revertirse.
- Fallo concreto `V2_CORE_M2B_EXTRA_LAUNCHERS ['ar.com.faro.v5.app.FaroV5V2CoreDevActivity', 'ar.com.faro.v5.platform.android.cert.FaroV5P6CertificationActivity']`. El segundo componente no es el Chat local y proviene del manifiesto debug heredado del módulo Android (blob `cd60630e3a380be81d993a8332a61f34327dccf0`, `v5/platform/android/src/debug/AndroidManifest.xml`), junto con `FaroV5P6CertificationProvider` todavía no certificado como ausente.
- `v5/platform/android/src/main/AndroidManifest.xml` (blob `83c45cb53f8531e48af8f6a6f3eebbd094cb6532`) agrega permisos POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, REQUEST_INSTALL_PACKAGES y dos receivers de alarma/update. `v5/app/src/v2CoreDev/AndroidManifest.xml` blob `41520a1fe69e037aa207f241b5b1b373771105cb` no los remueve aún, y preserva la Activity DEV única.
- `.github/workflows/faro-v5-v2-core-directed.yml` blob `1ad91167d5d54e5b5cacb034cad74cf5161cd201` prueba una sola Activity pero sólo niega `androidx.startup.InitializationProvider`. No simular PASS de otros proveedores/receivers. El build type usa UID/ID propio y noBackup, sin Internet en root DEV.

## B2 COMPARACIÓN — V4.4 y V5 sin duplicación
- V4.4 (repo FARO histórico, commit `44f1e6eb77102a71955b4fb113582d209d94ac69`) sí usa receptores, red y actualizaciones en producción: `app-v4/src/main/AndroidManifest.xml` blob `c59580b1390ea4cd0edd08ce048ac1642dfd35c1`. No trasladar sus componentes a la V2 de desarrollo local/offline.
- V5 tiene ya la clase de Chat, Workspace y el paquete DEV independientes. El delta NO toca código de stores, journal, UI ni `v5/platform/android` porque sería alterar el comportamiento compartido/shipping. Mantener el selector de artefactos empaquetados del anterior commit y todos los tests JVM/CI existentes.

## B3 INVESTIGACIÓN — alternativas categorizadas
- **USAR**: Android manifest merger `tools:node="remove"` sobre Activity P6 nombrada (solución mínima) y `tools:node="removeAll"` en nodos provider/receiver/service/alias solamente en manifest de variant DEV; elimina todas las entradas subordinadas de menor prioridad sin alterar la librería ni otros build types. Documentación oficial: https://developer.android.com/build/manage-manifests.
- **ADAPTAR**: mantener verificaciones del `packaged_manifests` final y endurecer sólo negativos de DEV para rechazar cualquier activity extra, provider, receiver, service, alias o permiso de notificaciones, alarmas, instalación y red. Fallar cerrado y reportar componentes si queda alguno inesperado.
- **DESCARTAR**: borrar P6 del módulo compartido, permitir la Activity adicional por estar `exported=false`, saltar el control de componentes, ignorar proveedores, dar INTERNET, copiar manifest V4.4, activar V2 shipping o incluir S4.
- **SIN CANDIDATO MATERIAL**: no hay razón para introducir una nueva librería o API: Android manifest merger oficial ya soporta los marcadores. Riesgo específico: `removeAll` opera por tipo y prioridad; verificar el APK final en CI (no asumir que la fuente lo demuestra).

## B4 UNIFICACIÓN — mínimo delta para RED_FIX
- Mantener unidad `M1_MANIFEST_VERIFIER` (el B7 de `42ca878a` es RED_FIX). Rutas permitidas estrictas: `v5/app/src/v2CoreDev/AndroidManifest.xml` y `.github/workflows/faro-v5-v2-core-directed.yml`. No tocar otros archivos.
- Manifest DEV: remoción nombrada de `FaroV5P6CertificationActivity`; negar por categoría provider/receiver/service/alias externos (sin nuevos launchers); remover permisos POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, REQUEST_INSTALL_PACKAGES y conservar ausencia INTERNET. Mantener un único `MAIN/LAUNCHER`.
- CI: retener `M2/M3 4/4` y `T4 7/7`, checks paquete/backup/Internet, más prohibición de componentes auxiliares y permisos OS en el manifest empaquetado. La negativa debe enseñar tipos concretos, nunca permitirlos para conseguir PASS.
- Primero este documento B1–B5 y B7 RED_FIX con SHA de blob; luego actualizar permiso documental, con `last_code_review=RED_FIX` del SHA exacto y alcance doble; esperar CI protocol preflight SUCCESS sobre ese permiso antes del siguiente código.

## B5 AUDITORÍA ADVERSARIAL ANTES DE REPARAR
- No exponer proveedor de certificación ni Activity de prueba de P6 dentro del UID DEV; aunque `exported=false`, violaría el contrato de único launcher/contexto.
- Desactivar en DEV capacidades de background/OS: receivers de alarma/update, servicios de WorkManager y sus providers, permisos de alarma/instalación/notificaciones/red. Si el merge produce un servicio adicional, CI bloquea; no desactivar el control.
- No tocar permisos/manifest `debug` habitual, V4.4 ni production V1; `allowBackup=false`, INTERNET ausente, root `noBackupFilesDir` y `FaroV5HostCertification.V2_ACTIVE_ENABLED=false` inmutables.
- Validar XML de una sola ruta `packaged_manifests/<variant>` y un solo launcher exportado; no corregir por elegir `found[0]`. Negativas de identidad, ACL/Search, AI/Apply/OS, stores y crash-recovery siguen fuera de esta subunidad y NO son PASS de V2_DEV_CORE_READY.
- CI rojo = redactar B7 RED_FIX y volver a la causa; sin rediseñar S2 o repetir pruebas históricas ajenas. Writer CAS y ejecución sin escritura durante CI.

## B7 RED_FIX — disposición independiente de 42ca878a (NO PASS)
- SHA revisado: `42ca878a9c3e86640546ee08112c69fd661f14c2`, único diff funcional workflow, mensaje `FARO-UNIT:M1_MANIFEST_VERIFIER`.
- Guardia run [#41881443886](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41881443886) **SUCCESS** con 14/14, `FARO_PROTOCOL_GATE_PASS 1 functional units validated M1_MANIFEST_VERIFIER@42ca878a9c3e` y `FARO_PROTOCOL_PREFLIGHT_PASS M1_MANIFEST_VERIFIER 0fd347e9...`.
- Dirigido run `41881443958` **FAILURE**, no certifica manifest M1/M2B; JVM M2/M3 4/4 y T4 7/7 se conservan como evidencia del alcance exacto.
- Resultado del B7: **RED_FIX**, causalidad identificada (P6 en manifest merge); mantener misma unidad y habilitar exclusivamente el delta de B4 con auditoría previa B1–B5 verificada. Solo nueva CI dirigida exacta y B7 futuro permitirán PASS.
