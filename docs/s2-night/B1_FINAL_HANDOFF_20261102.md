# FARO V5 · S2 Noche B1 — desarrollo A5, handoff limpio

**Rama:** `work/s2-chat-finish-night-20261102`. **Commit funcional:** `a630634bf96ff89a5cb9c0366eca6112d2ce585e`. **Writer:** NONE. **V2:** OFF. **S2.A6:** OPEN 6/8 (F1 #4 y T4 #8 pendientes). No se modificó main, S3 ni S4.

## Código implementado (no confundido con PASS)
Se conectó el selector real de Android `FaroV5AttachmentPicker` a la pantalla Chat, propagando un callback suspendido desde el host debug por la shell adaptativa. Al elegir archivos, `AndroidConversationAttachmentReader` realiza lectura local acotada, verifica MIME/firma y digest; `ConversationAttachmentIngressPolicy` comprueba límite de cantidad, bytes, identidad de conversación y request. Se reconsulta el Workspace antes y después de leer para rechazar selección obsoleta. Los bytes se destruyen en `finally` y solo se muestra un resumen local de cantidad/tamaño; **NO se envían archivos ni contenido a Groq, NO se persisten URI, NO se presenta esta vista previa como adjunto enviado**. El siguiente paso A5 es consentimiento por contenido y una vía de egreso real limitada a formatos soportados.

## Actions observadas
- [#41390549337](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41390549337) SHA `a630634bf96ff89a5cb9c0366eca6112d2ce585e`: **PENDING**
- [#41388266987](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41388266987) SHA `505672b359844eb8efb7ab70585b2e69cb2cbcbe`: **COMPLETED / CANCELLED**
- [#41387836208](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41387836208) SHA `38631765d331ee558b35f2b98615b4f61d3d172c`: **IN_PROGRESS**

La ejecución #41388266987 quedó CANCELLED al ser sustituida en la cola de concurrencia por la nueva compilación. No es PASS. No iniciar más runs hasta conocer resultado del candidato más reciente. La compilación y las pruebas del commit nuevo aún NO están certificadas.

## Próxima sesión
1. Leer el resultado de los runs y los logs/XML. Si hay ROJO, corregir y ejecutar solo los gates afectados.
2. Implementar A5 consentimiento explícito por contenido y entrega soportada, sin envío implícito ni almacenamiento de bytes/URI; test Android real del picker y revocación.
3. Completar el recorrido emulado Chat→Review→Apply aislado→receipt→Delivery→reinicio; producir una única APK debug identificada por SHA y firma.
4. Dejar solo T4 mínimo en Moto G15. No repetir SAF ya verde ni declarar 8/8 sin prueba física.
