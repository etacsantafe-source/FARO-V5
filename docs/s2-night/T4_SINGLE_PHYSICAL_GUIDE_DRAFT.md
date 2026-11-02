# FARO V5 — T4 mínimo en un único APK aislado (BORRADOR, no ejecutar aún)

**Estado:** NO APTO PARA PRUEBA hasta que la nueva compilación y los oráculos JVM/Android del último commit den PASS y se publique un único APK debug con SHA256 y certificado de firma. S2.A6 sigue OPEN 6/8, V2 OFF, main intacta.

## Preparación automatizada antes de solicitar el teléfono

- Exigir Actions SUCCESS sobre el SHA exacto del commit funcional final, XML de F1 Chat/Review/Apply/Delivery/reinicio y A5 contrato, APK de ese mismo SHA y firma verificada.
- Comprobar que el paquete sea `ar.com.faro.alpha.s2isolated.v5dev`; no instalar sobre FARO habitual.
- No volver a pedir SAF V4/V5 ya verde. No enviar credenciales ni datos reales a CI.

## Única intervención física prevista en Moto G15

1. Instalar el APK aislado verificado y abrir **FARO S2 · Chat aislado**. En Configurar IA, introducir una clave Groq propia **solo en el teléfono** y conceder/revocar consentimiento allí. Nunca enviar la clave por chat.
2. Abrir Chat. Enviar un saludo inocuo y comprobar respuesta real. Solicitar **cambiar el nombre declarado a «Prueba T4»**, revisar la propuesta, aprobar y pulsar Aplicar. Comprobar recibo y aviso local, cerrar y reabrir: el mismo cambio y aviso deben seguir sin duplicados. Todo sobre Reality de prueba.
3. Elegir un único `.txt` pequeño y sin datos privados, comprobar vista previa, cancelar una vez (no debe aparecer en borrador), repetir y confirmar incorporación; revisar el borrador y pulsar Enviar solo si se desea enviar ese texto a Groq. No usar imágenes/PDF como si fueran texto: permanecen en vista previa local sin egreso.

**Resultado a registrar:** PASS/FAIL por cada paso, SHA256 APK, versión, package, versión Android y observación de no duplicados. Si falla algo, detener la prueba sin repetir pasos previos ya verdes.

**Limitación honesta:** la instrumentación en emulador no sustituye el consentimiento del sistema, la llamada Groq real ni el recorrido físico en Moto G15. No declarar 8/8 hasta obtener evidencia física.
