# FARO V5 — F1 Noche A1 checkpoint (18:20–18:50 Argentina)

- Programado: **18:20**; inicio efectivo **18:22:31**; writer epoch 1 adquirido **18:23:02**. Checkpoint/release: **2026-10-31T21:45:29.924Z UTC**.
- Rama exclusiva: `work/f1-noche-v14-20261031-1820`; base `ec0f896c`; último código `382a7c23771d1d48aeb5247cc4e384331ecb32e9`.
- **F1 OPEN**, S2.A6 3/8 sin cambio, V2 OFF, F2/F3/F4/S3/S4 no modificados.
- Tablero: Groq **PROBADO (solo sintético)**; Chat **IMPLEMENTADO**; Review/Atomic Apply **IMPLEMENTADO**; Delivery Android **IMPLEMENTADO**; integración final **EN DESARROLLO**. Ningún subhito se declara COMPLETO.

## Producción funcional
- Migrado modelo Groq retirado `llama-3.3-70b-versatile` (retirado del plan free 16/08/2026) a `openai/gpt-oss-120b`, recomendado oficialmente, con límite de contexto 131072 y respuesta 2048 tokens.
- Transporte Groq GPT-OSS usa `max_completion_tokens`, `reasoning_effort=low`, `include_reasoning=false`; otros endpoints conservan `max_tokens`.
- Errores HTTP 404 -> MODEL_UNAVAILABLE, 413 -> CONTEXT_TOO_LARGE, 498 -> PROVIDER_CAPACITY retryable; 429 y revocación mantienen compuertas existentes.
- Commits funcionales: `79fc4ea7376facd7f92509057da7b1dc9317bba4`, `91cb91f76ef0b15441ce926ec4ec905a37d87a0b`, `ee1f6ecd6a406ce7fcfad467e15624f1cf00e47c`, `382a7c23771d1d48aeb5247cc4e384331ecb32e9`. Workflow dirigido nuevo solo F1, sin secretos.
- Documentación oficial: https://console.groq.com/docs/deprecations ; https://console.groq.com/docs/models ; https://console.groq.com/docs/reasoning ; https://console.groq.com/docs/api-reference ; https://console.groq.com/docs/rate-limits .

## CI y ROJOS
- [Actions #41243431547](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41243431547) SHA `91cb91f76ef0b15441ce926ec4ec905a37d87a0b`: **FAIL** compilación Kotlin en test, JSON quoting; sin XML. Corregido en `ee1f6ecd6a406ce7fcfad467e15624f1cf00e47c`.
- [Actions #41243481358](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41243481358) SHA `ee1f6ecd6a406ce7fcfad467e15624f1cf00e47c`: **SUCCESS**, XML **14/14**.
- [Actions #41244061014](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41244061014) SHA `382a7c23771d1d48aeb5247cc4e384331ecb32e9`: **SUCCESS**, XML **15/15** (10/10 transporte, 5/5 fábrica), cero errores/fallos/omitidos. Ninguna Action pendiente a las 18:45.
- Duraciones wall CI: #31547 **186 s**, #81358 **410 s** (cola proxy 149 s), #61014 **326 s**; son paralelas al trabajo y **NO se suman** como tiempo humano. Cola exacta no registrada; proxy desde primer timestamp del log.

## Tiempo y límites
- Tiempo observado desde inicio hasta checkpoint: **1379 s**; writer hasta liberación **1347 s**. División exclusiva investigación/código/pruebas/espera/coord./inactividad: **NO_REGISTRADO**. No atribuir los 30 min completos como producción.
- Groq real **NOT_RUN**, Android físico **NOT_RUN**, APK de prueba **NO LISTA**. No claves ni gastos; no activar V2.
- Próximo turno B: verificar writer/HEAD y conectar salida estructurada validada del modelo con Review canónico sin permitir Apply automático; continuar con pruebas dirigidas. Preparar entrada segura de credencial en Android y prueba física como WAIT_HUMAN.
- Writer: liberación CAS en el commit que contiene este checkpoint, verificar HEAD y JSON tras actualización.
