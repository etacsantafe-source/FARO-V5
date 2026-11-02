# FARO V5 · S2 Noche A1 — HANDOFF CLEAN

**Rama exclusiva:** `work/s2-chat-finish-night-20261102`.
**Último código:** `505672b359844eb8efb7ab70585b2e69cb2cbcbe`.
**Writer:** NONE. **V2:** OFF. **main:** no modificado. **S2.A6:** OPEN 6/8; #4 F1 y #8 T4 abiertos.

## Desarrollo realizado
1. Se creó una Activity de Chat accesible desde launcher **solo debug**, con Workspace V2 cinco stores aislados y persistentes en `noBackupFilesDir`, sin mover el puntero V1. Conecta Groq real únicamente después de consentimiento/Keystore local; no hay respuestas sintéticas en uso humano.
2. Composer, Timeline, Review y Apply humano con receipt sobre Reality **aislada**, más Notice idempotente en Auxiliary (sin notificación del SO).
3. Se añadió recuperación exclusiva de PROCESSING al reabrir sin reenvío de modelo y cierre de Activity con guard de inicialización.
4. Se añadió prueba Android de launcher con consentimiento negado y apertura consentida con clave sintética **sin enviar mensajes ni llamar a Groq**.
5. Workflow dirigido con assemble debug, JVM 4/4 esperados, prueba Android 1/1 esperada y artefacto APK. **No declarar PASS sin XML y run exitoso.**

## Actions al cierre
- [#41387836208](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41387836208), SHA `38631765d331ee558b35f2b98615b4f61d3d172c`: **IN_PROGRESS**, etapa assemble al último control.
- [#41388266987](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41388266987), SHA `505672b359844eb8efb7ab70585b2e69cb2cbcbe`: **PENDING** por concurrencia serial. Incluye test Android nuevo.
- No cancelar ni duplicar runs; esperar resultado, leer logs/errores reales y corregir ROJO dirigido. No asumir que una compilación pendiente es PASS.

## Siguiente trabajo prioritario
1. Verificar ambos runs, especialmente segundo SHA. Si compila ROJO, corregir código y relanzar por push dirigido. Registrar XML.
2. A5: existen `FaroV5AttachmentPicker` en `surface`, `AndroidConversationAttachmentReader` en `platform/android`, `ConversationAttachmentIngressPolicy` en `kernel/contracts`; **NO están cableados a Chat/Workspace/egreso**. Implementar selección ligada a conversación, límites, vista previa y consentimiento explícito **por contenido** antes de enviar; nunca guardar URI o compartir bytes por selección solamente. No fingir compatibilidad multimodal sin binding.
3. Ampliar prueba Android integrada en la Activity real (Review→Apply aislado→receipt→Notice→reinicio, sin red real), verificar revocación y un solo Apply.
4. Preparar **una sola APK debug aislada** con SHA/firma y guía física de pocas acciones. No repetir SAF verde; T4 en Moto G15 y consentimiento Groq real quedan para el usuario.

El estado anterior 6/8 permanece sin cambios porque los nuevos gates aún no terminaron. No se abrió S3/S4, no se tocó V1 ni main, sin secretos, sin ISO/IRAM.
