# S3 final U00–U03: reconciliación y preauditoría delta
Fecha AR 2026-11-12. Base exacta 3b97da5b7a2916189ab17d6ca4b26992b50f05cb. No cierre global ni consentimiento remoto.

## B1
HEAD remoto y local coinciden. Writer inicial RELEASED846; permiso A5 consumido BLOCKED. Se leyeron AGENTS, pointer, permiso, restitución y retroauditoría, handoff y cola final, matriz X01–X12 y gate A4. U00 completada READ_ONLY. Evidencia histórica conservada sin rerun:

|Etapa|PASS reutilizable|Delta real / criterio|
|---|---|---|
|A1|Shell/navegación automatizada scoped|Aceptación conjunta, sin promover shipping|
|A2|Chat local automatizado scoped|Envío físico nuevo inequívoco; no reenviar token incierto|
|A3|M01–M05, X04/X05 2/2 run43084379020; carpetas G15|Ciclo físico guardar/quitar/restaurar/fuente|
|A4|Core local 10/10 run42518057895; DEV N015/N031 y X02|Decisión de producto local explícita y cobertura host integrada; no cero global|
|A5|X01–X12 scoped; offline misma raíz 4/4 run43305744264|Groq live, política A4, integración y aceptación humana|

Cinco owners: Reality (receipt/finance/objective/matter), Workspace (chat), Auxiliary (avisos), CommercialEvidence y WebEvidence (evidence). X06 cuatro namespaces NO son cuatro stores completos. Evidence no es Reality. No cambio de catálogo180 ni esquemas.
U01: el gate A4 antiguo dice NO AUTHORITY; documentos posteriores LOCAL_INSTALL_READ_POLICY_CORE y N015_DEV_ACTIVITY_MOUNT ya implementaron autoridad técnica y gesto DEV. El primero llama su alcance «decisión de producto», pero no cita aprobación humana verificable para política monousuario global. La cola vigente ordena decisión humana si falta. Resultado: NEEDS_HUMAN_POLICY_DECISION para ampliación global; no borrar ni repetir core existente. No prueba defecto de la app.
U02: debug actual comparte applicationId ar.com.faro.alpha.s2isolated.v5dev con S2. MAIN debug exporta cuatro launchers; Application actual ejecuta recovery/bridges shipping. Root X03 noBackup aislado dentro del mismo UID no basta para credenciales/instalación separadas. Settings ya usa FLAG_SECURE, password sin save/autofill, Keystore y consentimiento; gateway fijo Groq/openai/gpt-oss-120b. No modificar esos componentes certificados.
U03: offline Review/Apply/receipt/Actividad ya PASS de una misma raíz; no rerun. Integración global que depende de U01 y Groq live queda BLOCKED_DEPENDENCIES, no permiso implícito.

## B2
Reutilizar Activity X03, settings y Keystore existentes, sin importar V4.4 ni reescribir S2. La comparación V4.4 para sandbox/Keystore no aporta contrato equivalente, documentada en LOCAL_INSTALL_READ_POLICY_CORE_B1_B5. Historial drawer y UX S1/A1 ya evaluado; identidad de paquete es delta Android exclusivo V5. No tocar fuentes debug históricas, CORE DEV, main branch ni V4.4.

## B3
Consulta primaria actual 2026-11-12:
- https://developer.android.com/build/build-variants : ADAPTAR build type con suffix y sourceSets explícitos. initWith no hereda automáticamente archivos debug. Sin dependencia nueva.
- https://developer.android.com/build/manage-manifests : USAR overlay propio y tools remove; verificar manifest final binario, no solo XML fuente. Errores reales conocidos: launchers/providers heredados y permiso AndroidX dinámico del applicationId.
- https://source.android.com/docs/security/app-sandbox y https://developer.android.com/privacy-and-security/keystore : USAR UID independiente sin sharedUserId, datos noBackup y Keystore por app. UID no es identidad humana.
- https://github.com/android/nowinandroid/blob/main/AGENTS.md y https://github.com/android/nowinandroid/blob/main/build-logic/README.md : REFERENCIA de app Android real con flavors/build types, lógica común y variantes separadas (Apache2). No copiar código ni añadir framework/convention plugin.
- https://console.groq.com/docs/models, https://console.groq.com/docs/rate-limits y https://console.groq.com/docs/billing-faqs : modelo fijo presente en catálogo; límites por organización y planes distintos. Ninguna página acredita plan de la cuenta del usuario. Mantener verificación humana Free antes de cualquier prueba remota; no HTTP/clave en CI.
Alternativas: (A) cambiar debug global/property P6: DESCARTAR, riesgo S2 y cuatro launchers. (B) nueva variante x03AiDev con identidad fija y manifest/Application mínimos: ELEGIR. (C) duplicar host/Keystore/app independiente completa: DESCARTAR, deriva y costo. (D) activar V2 CORE DEV Internet: PROHIBIDO. Sin librerías ni API paga; coste solo CI autorizado.

## B4
Unidad funcional única S3_FINAL_U02_DISTINCT_IA_APP. Rutas exactas:
1. v5/app/build.gradle.kts: variante opt-in x03AiDev, suffix .x03aidev y label FARO S3 IA AISLADA; prohíbe propiedades physical-ID ajenas al seleccionar pruebas X03; source debug Kotlin explícito; test source separado x03AndroidTest. Debug/default y v2CoreDev conservan sus IDs.
2. v5/app/src/x03AiDev/AndroidManifest.xml: Application android.app.Application sin bootstrap/recovery shipping, único launcher X03, settings interna, sin SAF, services/receivers/providers/background; backup false, sin shared UID. INTERNET solo en X03; excluir permisos ajenos, conservar permiso AndroidX propio si merge lo requiere.
3. v5/app/src/x03AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3FinalDistinctAppUiTest.kt: solo nuevas negativas de identidad/admisión de variante; Application mínima, datos propios, no grant por checkbox restaurado, sin credencial y sin HTTP. No repetir X01–X12.
4. .github/workflows/faro-v5-s3-final-distinct-ia-app-directed.yml: build X03/test, S2 debug y CORE DEV solo para verificar identidad/coexistencia; emulador35 sin red, UID S2 vs X03 y centinela privado S2 intacto; dos tests nuevos; XML de eventos exactos; paquete, firmante, backup, componentes/permisos y hashes. Publicar solo APK aplicación X03 y evidencia; androidTest en artefacto técnico separado. No enviar S2/debug al teléfono.
Firma: congelar APK exacta y registrar certificado. No prometer que otro runner genere misma clave debug. No cambiar firma/instalación existente; futuras actualizaciones requieren cert idéntico o nueva decisión, nunca uninstall/clear. No publicar clave privada ni cachearla.
Criterio: CI35 dos nuevas pruebas sin error/skip + manifest exacto un launcher, UID distinto de S2, test target X03, backup false y solo permiso INTERNET/AndroidX propio. CORE DEV sin INTERNET. UI humana Groq posterior, jamás PASS automático de provider.

## B5
Auditoría adversarial: nuevo build type no cambia identidad debug/S2/release/core. Bloquear combinación de flags de test X03 y G5/physical overrides antes de compilar. Application mínima impide arranque de recovery/bridges shipping. Bibliotecas pueden insertar componentes/permissions: eliminar y comprobar manifest fusionado y binario. Settings no exportada; Activity X03 no recibe datos de Intent; no persistir Free ni abrir host automáticamente. Pausa revoca sesión existente; política/grants/productivo permanecen igual. Sin candidatos test incorporados al APK aplicación, sin fixtures de datos reales. Emulador disposable; scripts no se usan en G15. Verificar UID además de package, y aislamiento con centinela S2 que solo S2 puede leer. No plan/Apply causado por admission test, sin API key ni HTTP CI. Comprobar nombres exactos eventos start/success y totales XML; cero certificaciones heredadas contadas como nuevas. Ante rojo mismo error dos veces volver B2/B3/B5.
U01 BLOCKED política de producto, U03 BLOCKED integrador dependiente. Estas condiciones no bloquean la separación U02 independiente. B6 requiere permiso doc separado con blobs exactos y guard SUCCESS. B7 independiente posterior, sin S3 CLOSED ni Groq live PASS.
