# FARO V5 — PROTOCOLO UNIFICADO DE DESARROLLO V2.0 (PROPUESTA DURABLE)
Fecha: 2026-11-02. Estado: DOCUMENTADO / NO ACTIVADO / PENDIENTE DE VERIFICACIÓN OPERATIVA.

## Fuente de verdad y alcance
- Repositorio: etacsantafe-source/FARO-V5. Rama documental actual: work/s2-chat-finish-night-20261102.
- S2.A6 CLOSED 8/8 según docs/s2-night/RUN_STATE.json y dictamen T4; NO volver a abrir ni repetir pruebas sin regresión demostrada.
- Históricos de Noche V1.5 que muestran 3/8 son snapshots antiguos, no estado actual; conservarlos sin sobrescribir.
- V2 de la aplicación OFF; S3/S4 no abiertos; main y FARO histórico intactos.
- Este protocolo V2.0 es de DESARROLLO, no activa V2 de la app.
- Ninguna automatización, worker o tarea queda autorizada para iniciarse por este documento. Antes de S3: conversar, revisar estado real y obtener permiso.

## Arquitectura de ejecución
- ChatGPT coordina, planifica, revisa y toma decisiones con el usuario.
- FARO Auto: ejecución interactiva/local mediante Termux cuando proceda; conservar cola durable, daemon/worker, polling histórico de ~15 segundos, Resource Guard, auto-stop y estados PASS/FAIL. La presencia, funcionamiento y rutas reales DEBEN verificarse; no afirmar que el instalador está en este repo.
- FARO Noche: ejecución autónoma con automatizaciones temporales A/B, exclusivamente bajo permiso con alcance, fecha, hora de corte y rama específicos.
- GitHub Actions: pruebas, compilaciones y artefactos dirigidos; registrar run ID, SHA, conclusión y XML, sin asumir PASS por build verde.
- Auto y Noche comparten backlog y exclusividad. Termux puede verificar recursos y ejecutar pruebas locales, pero no se convierte en segundo escritor simultáneo.

## Exclusividad y recuperación
- Un único writer global entre Auto, Noche A/B y agentes manuales. Identidad owner, epoch, lease, branch y HEAD esperado.
- Adquisición, commits y liberación mediante CAS sobre HEAD exacto; revalidar antes de escribir. Un lease vencido NO habilita reemplazar a escritor aún activo.
- Si no puede demostrarse exclusividad, READ_ONLY; no force, no pisar commits, no inferir writer NONE.
- En cada reanudación: leer checkpoint vigente, HEAD real, writer/lease, Actions en vuelo, automatizaciones activas, permisos, próxima tarea; evitar auditorías generales y repetición de PASS.
- Estado durable de tarea: ID, objetivo, dependencias, criterios, responsable, estado, SHA, Actions, evidencia, siguiente acción y bloqueo.

## Modalidad FARO Auto
- Bloques interactivos de hasta 30 minutos con cola durable, Resource Guard, límites de RAM, auto-stop, seguimiento de PASS/FAIL y checkpoint.
- Recuperar instalación y protocolo exacto del entorno real antes de poner en marcha; el historial de Termux no equivale a instalación actual comprobada.
- Acciones de teléfono físico requieren interacción explícita del usuario; no automatizar credenciales ni consentimientos.

## Modalidad FARO Noche V1.5
- Dos automatizaciones A y B, cada una horaria, desfasadas 30 minutos, más informe final READ_ONLY; solo tras autorización expresa.
- Ventanas respecto al horario PROGRAMADO, no al inicio efectivo: 0–20 desarrollo y tests; 20–25 finalizar, sin nueva tarea/Actions; al 25 registrar Actions en curso; 25–27 checkpoint y liberación CAS; 27–30 READ_ONLY.
- Si writer ocupado: hasta ~5 min de investigación READ_ONLY y revalidación; no iniciar escritor sin exclusividad. Si CI ocupado, investigar el siguiente paso de la misma tarea sin lanzar duplicados.
- Corte absoluto autorizado, sin prórroga implícita; al terminar, desactivar A/B, verificar desactivación y emitir informe final único. No cancelar CI en vuelo por corte; dejar ID/SHA.
- Tareas con dependencias: no adelantar por ROJO; si solo resta humano/físico, congelar READY_FOR_T4/WAIT_HUMAN y seguir solo trabajo independiente y seguro.

## Pruebas, productividad y seguridad
- Agrupar cambios y gates por lote; reutilizar PASS por SHA y XML. No relanzar Actions repetidas ni hacer pushes para consumir minutos.
- ROJO: diagnosticar evidencia real, corregir y probar mínimo; tras dos intentos fallidos cambiar hipótesis, investigar o detener; riesgo de corrupción/autoridad/egreso => fail-closed.
- Métricas por turno: tiempos observados de código, investigación, pruebas, coordinación, espera de writer/CI, inactividad; no sumar CI solapado dos veces; ausencia de dato = NO_REGISTRADO.
- Permisos limitados por alcance y duración. No tocar main, V1/V4.4, repositorios históricos, secretos, firmas, pagos, visibilidad, datos sensibles, producción ni activación V2 sin autorización separada.
- No activar Noche ni Auto solo porque existe este archivo.

## Pendientes ANTES de desarrollo S3
1. Verificar estado real de Auto (instalación, daemon, worker, cola, Resource Guard, auto-stop y comandos), sin asumir que scripts/v5 contiene instalador Auto.
2. Consultar automatizaciones reales, especialmente tareas antiguas y pendientes; desactivar o corregir únicamente con autorización y evidencia.
3. Confirmar mecanismo compartido de writer/CAS entre Auto y Noche; si falta, implementar y probar aislado, no sobre FARO productivo.
4. Definir backlog único y formato de checkpoint, reconciliar con RUN_STATE.json de S2 cerrado sin modificar históricos.
5. Ejecutar prueba inocua, aislada, sin escrituras funcionales ni activación V2; revisar resultados.
6. Solo entonces discutir autorización y alcance de S3.

## Documentos de referencia
- docs/faro-noche-v15/AUTH_PROTOCOL.md
- docs/faro-noche-v15/RUN_STATE.json (histórico)
- docs/faro-noche-v15/BACKLOG_READ_ONLY.md
- docs/CIERRE_NOCHE_V14_AUTHORIZATION_20261031.md
- docs/s2-night/RUN_STATE.json (S2 actual)
- docs/s2-night/HANDOFF_NEXT_CHAT_S2_CLOSED_20261102.md

## Handoff
En próximo chat: «Continuar FARO V5. Recuperar protocolo unificado de desarrollo V2.0». Primero conversar y verificar infraestructura real; NO iniciar tareas automáticamente. Estado esperado: S2 CLOSED 8/8, V2 OFF, S3/S4 sin abrir, writer NONE sujeto a revalidación.
