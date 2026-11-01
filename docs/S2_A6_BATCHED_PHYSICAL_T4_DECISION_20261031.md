# FARO V5 — Decisión durable: pruebas físicas conjuntas de S2.A6

**Fecha local de decisión:** 2026-10-31 (Argentina).
**Alcance:** S2.A6 F1–F4 y T4; reemplaza la práctica de solicitar una prueba física tras cada bloque. No cambia los criterios congelados, ni certifica un bloque pendiente.

## Decisión humana aprobada

1. Desarrollar F1, F2, F3 y F4 de forma continua, con pruebas unitarias, de integración, de políticas y Android emulado **durante** el desarrollo. Corregir errores reales antes de avanzar; usar evidencia por commit SHA exacto y XML de tests ejecutados (nunca sólo compilación o inspección).
2. **Aplazar todas las pruebas que requieran el Moto G15, interacción humana o consentimiento externo real** a una única campaña física T4 cuando F1–F4 estén preparados. El aplazamiento no convierte los tests no ejecutados en PASS.
3. Mantener dos estados diferenciados: `LISTO_PARA_T4` (implementación y gates automáticos revisados, con evidencia física identificada como pendiente) y `CERTIFICADO` (sólo tras cumplir cada criterio de aceptación). Cualquier dependencia verdaderamente bloqueante para continuar debe informarse, no ocultarse.
4. Preparar antes de T4 una versión de APK e instrumentación reproducibles, con identidad de aplicación/firma, SHA, script/checklist físico único y criterios de resultado VERDE/ROJO. Si hay un ROJO, reparar y repetir las pruebas afectadas. No pedir instalación sucesiva de APK para cada F.
5. Mantener V1 protegido, V2 desactivado (`V2_ACTIVE_ENABLED=false`), generación de cinco stores aislada, sin escrituras a Reality activa, sin activar S3/S4, sin cambiar main, sin activar FARO Noche/Actions por esta decisión y sin secretos/API keys en el repositorio. Las pruebas de Apply usan únicamente datos aislados.
6. No exigir certificaciones externas ISO/IRAM ni crear auditorías documentales duplicadas; conservar únicamente los gates técnicos y la evidencia necesaria.

## Punto de partida verificable

Fuente: `docs/CIERRE_NOCHE_V14_RUN_STATE.json`, HEAD `ec0f896c29eff00ca7b9e0dcdb2ea93dfea2fa56` al registrar la decisión. S2.A6 **OPEN 3/8**, criterios 1–3 CERTIFIED; F1, F2, F3, F4 OPEN; T4 NOT_RUN; writer NONE epoch 44. Esta decisión **no cambia los ocho estados**.

La captura física del usuario mostró que la APK normal abre pero Chat presenta “Esta sección todavía no está disponible en esta versión de prueba”. Es esperado bajo V2 OFF: la superficie F1 aislada existe para instrumentación Android, pero no es la pantalla Chat habilitada de producción. **No es un fallo probado de Groq** ni un PASS de F1.

## Qué falta para dejar F1 preparado y cerrarlo

- **Ya implementado, no confundir con F1 certificado:** Compose y su host de cinco stores en cuarentena; flujo local simulado Composer → Workspace → respuesta → Review → Apply/receipt → Delivery con pruebas Android; transporte/provider-neutral y manejo de consentimiento/Keystore con pruebas dirigidas. Reutilizar evidencia existente; no duplicar suites.
- **Antes de pasar a F2:** consolidar/verificar sobre un SHA funcional identificado los gates automáticos relevantes de F1 (Android Compose, host/cinco stores, policy de egress/consentimiento, Review/Apply/Delivery, recuperación e idempotencia), registrando resultados XML reales. Resolver ROJOS. Asegurar que el paquete de prueba física instrumentada pueda abrir el host aislado sin habilitar la navegación de producción ni V2.
- **Diferido a T4:** recorrido en Moto G15 de Chat y UI real aislada; consentimiento explícito y credencial guardada en Android Keystore; llamada al proveedor Groq real y respuesta observable sin exponer secretos; Review humano → confirmación → Apply atómico en Reality **aislada** → receipt → Delivery sin duplicados; revocación/errores recuperables y reinicio cuando corresponda. No simular proveedor externo como prueba de red.
- Hasta superar los criterios de F1 y la verificación física que corresponda, **F1_ANDROID_CHAT_E2E permanece OPEN**. Se permite continuar el desarrollo de F2–F4 cuando F1 alcance LISTO_PARA_T4 con sus pruebas automáticas aprobadas.

## Continuación

Prioridad inmediata: completar la evidencia automática faltante de F1, **sin pedir prueba física ahora**. Luego continuar F2, F3, F4; preparar una sola campaña T4. No iniciar tareas automáticas ni modificar código sin autorización de ejecución específica. Este documento es una decisión de proceso; no constituye una orden de activar una automatización.
