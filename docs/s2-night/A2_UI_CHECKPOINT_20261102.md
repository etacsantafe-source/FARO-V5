# FARO V5 — S2 Noche A2, checkpoint de código real y pruebas pendientes

**Rama:** `work/s2-chat-finish-night-20261102`. **HEAD funcional:** `7b740e9fcaefb264f284a0d0feed82d56aa2ac45` (A5 consentimiento UI). **Writer NONE / HANDOFF CLEAN**. S2.A6 OPEN 6/8 (F1 #4 y T4 #8 pendientes). V2 OFF, main/S3/S4 intocados.

## Implementado en GitHub (no equivale a PASS)

- Contrato A5 `ConversationAttachmentDraftConsentPolicy`: confirmación humana explícita, conversación y picker actuales, digest y longitud exactos, un solo archivo de texto hasta 8 KiB, ningún egreso automático; 9 tests JVM escritos.
- En Chat aislado, selección Android + verificación de contenido y SHA-256; para texto soportado, diálogo con identidad/resumen y confirmación humana; solo incorpora el texto al editor local y **Enviar** sigue siendo una acción humana separada con autorización Groq del host. No persiste URI ni bytes de archivo. PDF/imágenes quedan en vista previa local sin modelo. El envío real aún NO se probó en CI.
- Workflow dirigido actualizado para 9 tests JVM de A5 y Android Chat/Review/Apply/Delivery/reinicio (emulador), pero este commit fue `[skip ci]` expresamente para no lanzar otro run mientras el anterior está activo.
- Guía preliminar T4 mínima: `docs/s2-night/T4_SINGLE_PHYSICAL_GUIDE_DRAFT.md`; NO ejecutar antes de APK PASS.

## CI y continuidad sin duplicados

Actions [#41390549337](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41390549337), SHA `a630634`, estado al checkpoint **IN_PROGRESS**. No se atribuye PASS. #41387836208 y #41388266987 CANCELLED. El código A5 nuevo HEAD `7b740e9fcaefb264f284a0d0feed82d56aa2ac45` aún NO fue compilado/probado por Actions. No se lanzó una nueva ejecución por esta actualización.

**Prueba Android adicional preparada, NO publicada:** commit Git `dcc1f9653e1bfba727b3626fa77c04a5835da35e` (parent `7b740e9fcaefb264f284a0d0feed82d56aa2ac45`). Añade provider sintético local, AndroidManifest de androidTest y tres pruebas reales de ContentResolver, duplicado y URI no-content; workflow con oráculo Android total 5/5. Como este checkpoint documental mueve HEAD, NO actualizar directamente la ref a dcc1: reconstruir un árbol sobre el HEAD actual usando estos blobs y CAS:
- `v5/app/src/androidTest/AndroidManifest.xml`: `2295515ca7ac7c579e62d1d7efaabc66216d662a`
- `v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5A5TestContentProvider.kt`: `f53fe084be27cac766d6f389ac5f5d2797446076`
- `v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5A5TextReaderInstrumentationTest.kt`: `df4fc267011196bd6bf568b1d4230838f823f2d3`
- `.github/workflows/faro-v5-s2-chat-isolated.yml`: `cc92262ae1c7c317cda1fa07ee0237fc622fd12b`

## Próxima sesión

1. Leer el estado real de #41390549337 y logs/XML. Si ROJO, corregir el código afectado; si sigue ejecutándose, no duplicar CI.
2. Al finalizar el run anterior, integrar los cuatro blobs Android anteriores en el HEAD actual mediante Git tree + commit + `update_ref(expected_sha)` y ejecutar un único CI dirigido por push. No hacer force-push.
3. Resolver rojos; exigir APK con SHA/firma y oráculos Android/JVM verdes, luego T4 físico mínimo con usuario. No declarar S2 8/8 sin evidencia física.
