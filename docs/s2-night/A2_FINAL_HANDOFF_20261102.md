# FARO V5 · S2 Noche A2 — A5 consentimiento de borrador (contrato)

Rama exclusiva `work/s2-chat-finish-night-20261102`. Sesión A2 02/11/2026. Writer NONE, handoff CLEAN. S2.A6 OPEN 6/8, criterios 4 y 8 abiertos. V2 OFF, S3/S4 sin abrir, main intacta.

## Código real

Se agrega `ConversationAttachmentDraftConsentPolicy` (kernel/contracts) y 8 pruebas JUnit 5 dirigidas. Este segundo gate requiere confirmación explícita posterior al picker, vincula conversación, picker request, digest SHA-256 y longitud exacta, admite un único archivo de texto soportado y limita la incorporación a borrador a 8 KiB. No autoriza egreso a IA ni modifica Workspace: exige revalidar contenido/identidad al integrarse con UI y que el usuario pulse Enviar; la política global de egreso se aplica además. PDF/imágenes y selecciones múltiples no se convierten implícitamente a texto. No hay URI, bytes ni grants persistidos en el contrato.

**Estado de integración:** el gate todavía NO está cableado al botón de confirmación de Chat. No declarar A5 completo. La selección local B1 sigue sin egreso. Próxima sesión: mostrar confirmación específica por archivo, revalidar contenido y estado, incorporar texto al editor solo después de confirmar; pruebas UI/Android y revocación. Evitar enviar bytes/URI en silencio.

## CI y evidencia

Al comenzar, Actions #41390549337 (SHA a630634) seguía en paso de compilación, sin conclusión. #41387836208 terminó CANCELLED; #41388266987 CANCELLED. No se declara PASS de esta política ni de la APK. Este commit solo toca kernel/contracts, sus tests y documentación; el workflow S2 Chat dirigido observa cambios `v5/app/**`, por lo que no se fuerza otro run mientras #41390549337 siga ejecutándose. Revalidar resultados en el próximo turno, compilar los tests del contrato junto a las pruebas afectadas.

## Próximo trabajo

1. Verificar #41390549337 y logs/XML; corregir cualquier error.
2. Integrar A5 gate en pantalla Chat con consentimiento humano real, bytes efímeros, verificación de digest, límite de borrador y envío separado.
3. Prueba Android end-to-end Chat→Review→Apply aislado→receipt→Delivery→reinicio; UNA APK SHA/firma; T4 físico mínimo posterior.
