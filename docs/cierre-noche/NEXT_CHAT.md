# FARO V5 — inicio de chat nuevo después del protocolo CIERRE Noche

## Texto para iniciar la siguiente conversación

Continuar el proyecto FARO V5 usando el protocolo durable del repositorio privado `etacsantafe-source/FARO-V5`:

- Rama documental: `docs/cierre-noche-v1-20261030`.
- Contrato: `docs/cierre-noche/PROTOCOL_V1.md`.
- Índice corto: `docs/cierre-noche/LAST_KNOWN_STATE.json`.
- Auditoría de preparación y requisitos pendientes: `docs/cierre-noche/VALIDATION_20261030.md` (validación estática PASS; motor de 50 min y recuperación operativa NO CERTIFICADOS).
- Último checkpoint de producto conocido: rama `work/cloud-afternoon-pilot-20261030`, `docs/CLOUD_PILOT_CYCLE_02_20261030.md`, HEAD de referencia `03f3cf146b06807324858e824845dc9713db94fa` (**revalidar en GitHub**).
- Evidencia de CI: Actions `41071061340`, 20/20 dirigidas + compilación, NO cierre F1/F2.
- S2.A6 OPEN 3/8, V2 OFF, F1/F2/F3 abiertos, F4 pendiente, T4 NOT_RUN. El conflicto F3/S5 es humano.
- Original `etacsantafe-source/FARO` READ_ONLY; `main` de repositorio nuevo intacta.
- Protocolo acordado: 0–45 min investigar/implementar/probar/encadenar (VERDE sigue; ROJO hipótesis nueva; decisiones humanas/T4 se difieren y se avanza un frente independiente); 45–50 min cierre, checkpoint, writer NONE. Este periodo es objetivo, NO una capacidad todavía certificada de ChatGPT.
- **Continuidad**: nunca depender de memoria del chat ni incluir todo el código en contexto; recuperar estado/HEAD y leer archivos puntuales, checkpoint luego de unidades seguras y antes de límite de contexto. Reconciliar commits y Actions tras cualquier corte abrupto.
- **No hay autorización de ejecución**: automatizaciones apagadas, piloto anterior vencido. No activar Noche, no programar, no ejecutar escrituras producto/Actions, no publicar ni hacer merge sin autorización expresa.

Primero verificar READ_ONLY el estado real de GitHub, el contrato y si todas las automatizaciones permanecen desactivadas. Informar qué falta para probar el protocolo de 50 minutos y para activar Noche de forma segura. Luego continuar la conversación con el usuario.

## Recuperación mínima

1. Verificar HEAD real de `work/cloud-afternoon-pilot-20261030`, `main` y rama documental.
2. Comprobar Actions recientes y su estado terminal.
3. Leer `PROTOCOL_V1.md` y `LAST_KNOWN_STATE.json`.
4. Confirmar que el piloto de tarde terminó y que no hay autorización nocturna vigente.
5. Nunca convertir automáticamente el checkpoint histórico en certificación o permiso.

**Esta guía no constituye una orden de activar el ejecutor.**
