# FARO V5 — M4 B7 RED_FIX: permisos del manifest empaquetado V2 DEV (04/11/2026)
**Unidad:** `M4_DEV_NEGATIVE_EFFECT_GATES`. **Código bajo B7:** `b92d5407c9a856c1ca91f7e2b81d76b41dd0f15a`. **Estado:** `RED_FIX` acotado, NO M4 PASS, NO V2_DEV_CORE_READY, no prod.

## B7 RED_FIX
- Preflight previo confirmado en Actions #41894104925 (`FARO_PROTOCOL_PREFLIGHT_PASS M4_DEV_NEGATIVE_EFFECT_GATES d5354b60...`).
- Guard #41895302140 **SUCCESS**; JVM V2 #41895301999 **6/6 PASS** para las seis pruebas (incluye los dos negativos de stale); T4 #41895301999 **7/7 PASS**. Regresión S3 #41895302132 **SUCCESS**, XML 9/9+7/7+5/5+4/4 = **25/25**.
- V2 dirigido #41895301999 **GLOBAL FAILURE** únicamente en paso `Verify package isolation, no backup and offline mode`, diagnosticó:
  `V2_CORE_M4_PERMISSIONS_NOT_DENIED` = `android.permission.WAKE_LOCK`, `android.permission.ACCESS_NETWORK_STATE`, `android.permission.RECEIVE_BOOT_COMPLETED`, `android.permission.FOREGROUND_SERVICE`, `android.permission.USE_BIOMETRIC`, `android.permission.USE_FINGERPRINT`, `ar.com.faro.alpha.v2coredev.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- El manifest packaged DEV fue correctamente seleccionado de `packaged_manifests/v2CoreDev/processV2CoreDevManifestForPackage/AndroidManifest.xml`; no es un falso selector ni una falla de compilación. **B7 no es PASS** hasta la corrección y nueva CI exacta.
- Archivos cambiados en `b92d5407`: solo workflow CI y test JVM permitidos; queda como cambio functional anterior a acreditar. Este documento define el delta necesario y el permiso nuevo será otro commit.

## B1 EVIDENCIA NUEVA
- El manifest DEV `v5/app/src/v2CoreDev/AndroidManifest.xml`, blob `0387261d68d3db680cc39100535a1395aa7b2f51`, ya quita Internet, notificaciones, alarmas e instalaciones, pero siete permisos llegan del merge transitivo.
- Manifest final observado en CI, no inferencia desde Gradle; biblioteca WorkManager/AndroidX y host debug en classpath **no autorizan usos OS**. Autoridad DEV arranca con Application vacía y únicamente Activity/puertos locales. Todos los efectos fuera de Workspace deben seguir prohibidos.
- Evidencia previa reusable `docs/v5/FARO_V2_M4_NEGATIVE_GATES_B1_B5_20261104.md` y su plan de fail-closed. El test nuevo detectó defecto real, no es motivo para relajar la aserción.

## B2 COMPARACIÓN V4.4 Y REUSO
- V4.4 shipping contiene permisos, boot receivers y servicios de producto por diseño; `docs/v5/FARO_V2_RETRO_B1_B5_M0_M3_20261104.md` los compara de forma read-only. **No copiar la política V4.4 a DEV**.
- Reusar `tools:node="remove"` de manifest DEV (ya aplicado a INTERNET/OS), sin otra herramienta, nueva Authority o cambios al app normal.
- No modificar `v5/app/src/main/AndroidManifest.xml`, app `debug` habitual ni los seteos globales.

## B3 INVESTIGACIÓN / ALTERNATIVAS
- **ADAPTAR** el manifest overlay existente con `uses-permission android:name=... tools:node="remove"` para los siete nombres **exactos**, en `v2CoreDev` solamente. Manifest Merger Android documentado: https://developer.android.com/build/manage-manifests ; forma alternativa `uses-permission-sdk-23`: https://developer.android.com/guide/topics/manifest/uses-permission-sdk-23-element .
- **DESCARTAR** permitir permisos transitorios como excepción por ser bibliotecas; su presencia permanece innecesaria para Activity/Workspace local y debilitaría la invariancia. También descartar editar main, generar otro build type, apagar el verificador o eliminar dependencias globales, que arriesgan S2.
- El permiso `ar.com.faro.alpha.v2coredev.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` se aborda por nombre exacto del paquete DEV. Si merger no permite removerlo, FAIL y volver al bloque causal con evidence; no aceptar arbitrariamente la excepción.

## B4 UNIFICACIÓN
- **Delta funcional exacto:** únicamente `v5/app/src/v2CoreDev/AndroidManifest.xml`. Agregar siete instrucciones `tools:node="remove"`, sin tocar launchers, application, componentes ni `android:allowBackup`.
- Conservar tests JVM 6/6, T4 7/7, regresión S3 25/25 y verificador manifest estricto. Reejecutar CI del nuevo SHA para demostrar packaged manifest sin `uses-permission*`; no repetir otras campañas de certificación.
- **Permiso requerido:** mismo `unit_id=M4_DEV_NEGATIVE_EFFECT_GATES`, nueva lista de paths acotada (manifest DEV, workflow y test ya revisados, o solamente manifest), B1–B5 referencias al blob de este documento y `last_code_review=RED_FIX` para el SHA `b92d5407`. Emitir permiso en commit docs separado, preflight PASS antes de código.

## B5 AUDITORÍA ADVERSARIAL PREVIA
1. La supresión sólo afecta UID `ar.com.faro.alpha.v2coredev`; app habitual y V4.4 intocadas. Los siete nombres deben faltar del manifest **empaquetado**, no simplemente de fuente.
2. Mantener verificador deny-by-default por todas etiquetas `uses-permission*`, incluso futuras SDK-23, y check exacto del único launcher + Application vacía, no backup, ni providers/receivers/services.
3. Sin permiso de Internet, wake locks, auto-boot, foreground service o biometría, el host DEV únicamente lee/escribe Workspace con gate; no existen efectos de Apply/model/OS enviados. No confundir permiso ausente con prueba de no-egress dinámico integral M5.
4. Si algún permiso implícito no se remueve, falla CI (RED_FIX causal), **no** deshabilitar test. Verificar archivo final y declarar M4 PASS únicamente tras CI por SHA, XML y B7 independiente.
5. Writer CAS, un único código funcional a la vez, release temprano y no Auto/Noche; puntero/cola existentes, S2 congelado, catálogo 172, S3 0/5 formal, S4 OFF.
6. Ninguna prueba física ahora: M5 consolidará artefacto/restart/kill Android luego; no promover V2 DEV por CI de manifest.
7. Documentar desviación claramente: el permiso M4 actual no incluye manifest. **No escribirlo antes de nueva auditoría durable, permiso READY y preflight PASS**.

## B6 IMPLEMENTACIÓN CORRECTIVA
**PENDING PRE-FLIGHT**; nuevo permiso de manifest aún no publicado en este documento.

## B7 AUDITORÍA FINAL
**RED_FIX exclusivamente para el SHA `b92d5407`**, todavía pendiente de B6 correctiva y nueva CI.
