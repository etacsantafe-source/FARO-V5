# FARO V5 — Segundo piloto · B2 cierre de F1 y CI
Fecha: 2026-10-30 Argentina. Sesión B-1640. Estado: CLOSED / PASS_REVIEW / HANDOFF CLEAN.

## Writer y tiempo
- Repo privado `etacsantafe-source/FARO-V5`, rama única `work/cierre-noche-v11-pilot-20261030`. Writer B2 epoch 7 adquirido con CAS sobre `72df2124bbe5` (primer commit de adquisición `aa162cbeeea4`), sin otra sesión A activa. Lease renovado hasta 17:09:20 -03 con CAS; liberado a **NONE** a `2026-10-30T20:07:39.148Z`, antes de A-1710.
- Inicio efectivo observado en Automations `2026-10-30T19:43:55.304Z`, cierre `2026-10-30T20:07:39.148Z`. Tiempo transcurrido hasta checkpoint: **23.73 min**; NO atribuir cómputo activo a todo el intervalo.
- GitHub Actions en cola o ejecutándose al cierre: **0**. No se canceló ninguna Action. No se abrió otro writer.

## Entregas F1
1. **Revocación de egreso antes del modelo**: se revalidan todos los fragmentos realmente seleccionados del contexto histórico y el turno actual inmediatamente antes de llamar al ModelGateway. Si cualquiera perdió permiso, se evita el egreso y el PROCESSING queda para recuperación exclusiva, sin reintento automático. Código `d60579d19a8d1ec2e4228f05bc52a602ceb31e56`. [Actions 41118040821](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41118040821) terminal SUCCESS; XML exactos: **35/35 dirigidas, 36/36 F4 críticas, 31/31 Surface**, 0 fallas/errores/omitidos; `FaroV5ActiveChatReasoningSessionT2Test` 15/15.
2. **Resultado tipado para la UI**: el host comunica OFFLINE, FAILED, NOT_AUTHORIZED, INVALID_TURN y RECOVERY_REQUIRED al chat, además de refrescar el timeline; no hace reintento incierto ni Apply. Contrato compatible con el puerto de refresco anterior, V2 sigue dormido. Dos pruebas de integración con Workspace real: éxito e idempotencia sin doble llamada al modelo, y OFFLINE sin texto inventado. Código `a19c6854c5519b2c662165eb1d6da600b179ea33`. [Actions 41119328351](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41119328351) terminal SUCCESS; XML exactos: **37/37 dirigidas, 36/36 F4 críticas, 31/31 Surface**, 0 fallas/errores/omitidos; suite F1 17/17.
- Ambos gates incluyen compilación Kotlin/Java Android y verificaciones estructurales. **No** son Android E2E ni T4 físico; F1 continúa IN_PROGRESS.

## Estado y siguiente brecha
- **S2.A6 OPEN, 3/8**: owners, FBS2 y journal/WAL certificados; F1 Chat/Review/Apply/Delivery Android E2E pendiente; F2 activación/recovery Android pendiente; F3 exportación/restauración SAF+consentimiento aún sin UI mínima aunque opción A está aprobada; F4 Android integral pendiente; T4 físico NOT_RUN.
- Hallazgo concreto F1: `FaroV5CompositionRoot.configureTrustedAssistantRuntime` acepta ModelGateway/profile/egress pero `FaroV5Application` todavía no lo configura. Existe `ComposedModelGateway` y `:platform:ai/GroqTextModelBinding`, pero no están conectados al bootstrap de Android con preferencias/credenciales autorizadas. **No inventar credenciales, no activar V2 ni hacer llamadas externas de prueba**.
- F3: `FaroV5MaintenanceRuntime` ya posee frontera SAF segura y restore en cuarentena; falta el flujo de confirmación visible y selector conectado, manteniendo gate `V2_ACTIVE_ENABLED=false` hasta certificar.
- V2 OFF; S3/S4 no abiertos; `main` verificado en `9bae214541f466a62e149853931b96abb8d74ed9`, sin cambios. Histórico FARO/V4.4/FARO Auto sin escrituras de esta sesión.

## Handoff A-1710
Releer autorización, estado, HEAD, writer NONE/epoch7 y CI terminal antes de adquirir epoch8 con CAS. Priorizar conexión real del ModelGateway neutral (sin secretos en código) o UI mínima F3; no repetir Actions anteriores. Corte absoluto 18:10 -03.
