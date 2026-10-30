# FARO V5 — Handoff al chat nuevo: piloto 3 horas con CI escalonada (V1.2)

Este archivo conserva el contrato para reanudar FARO en otro chat. **No es autorización ejecutable**, incluso cuando el texto sugerido dice «Autorizo»: la autorización únicamente nace si el usuario lo envía como mensaje nuevo.

## Último estado verificable

- Repo PRIVADO: `etacsantafe-source/FARO-V5`.
- Protocolo actualizado: rama `docs/cierre-noche-v1-20261030`, archivo `docs/cierre-noche/PROTOCOL_V1.md` **V1.2**.
- Índice histórico actualizado `docs/cierre-noche/LAST_KNOWN_STATE.json`; no sustituye revisar HEAD actual.
- Última rama de producto conocida: `work/cierre-noche-v11-pilot-20261030`, HEAD `f1873b71da4622cb7309e1bff082d2f76bb66fa5`; último checkpoint `docs/CIERRE_NOCHE_V11_B3_20261030.md` y `docs/CIERRE_NOCHE_RUN_STATE.json`.
- S2.A6 3/8, V2 OFF, S3/S4 sin abrir, writer NONE epoch8 en último checkpoint; **revalidar en vivo**.
- Actions #41125074207 SUCCESS para SHA `b030c2fa464a211921015fac47c1e25d37247dc2`: 38/38 dirigidas (F1 reasoning 18/18), 36/36 regresiones críticas, 31/31 Surface. Android E2E/T4 no ejecutados; no implica 8/8.
- F3 opción A APROBADA (flujo S2 mínimo con consentimiento + selector SAF, Ajustes definitivo en S5), según `docs/FARO_V5_F3_S2_S5_DECISION_20261030.md` en rama de producto.
- Prioridad técnica F1: conectar en bootstrap Android `ComposedModelGateway` desde configuración real de perfiles/credenciales bajo Keystore y política de egreso, completar recorrido de chat. Sin credenciales hardcodeadas, sin activar V2 anticipadamente.
- Última comprobación de automatizaciones al preparar V1.2: cero activas; comprobar otra vez al activar.

## Mensaje que el usuario puede enviar en un chat nuevo

FARO V5 — PILOTO CIERRE NOCHE V1.2, 3 HORAS.

Autorizo una prueba autónoma de **máximo 3 horas desde el primer inicio efectivo**, o hasta S2.A6 cerrado (8/8), lo que ocurra primero. Si sólo queda T4 físico o únicamente tareas humanas sin otro trabajo seguro, detener el desarrollo y dejar preparado el cierre, sin declarar PASS.

Repositorio exclusivo: `etacsantafe-source/FARO-V5`, privado.
Leer primero `docs/cierre-noche/PROTOCOL_V1.md` V1.2 y `docs/cierre-noche/LAST_KNOWN_STATE.json` en `docs/cierre-noche-v1-20261030`. Recuperar estado real desde GitHub, especialmente `work/cierre-noche-v11-pilot-20261030` HEAD de referencia `f1873b71da4622cb7309e1bff082d2f76bb66fa5`; comprobar commits, checkpoint, Actions, writer y automatizaciones.

**Crear y activar exactamente DOS automatizaciones de trabajo en ESTE CHAT:** Noche A cada hora y Noche B cada hora, desfasada 30 minutos, tres ejecuciones previstas por automatización. Fijar inicio, seis ventanas y hora de corte global con fechas exactas en hora Argentina y registrar las ID. No crear tercera tarea de informe. No dejar tareas anteriores activas.

Usar únicamente una NUEVA rama exclusiva `work/cierre-noche-v12-pilot-20261030`, creada desde HEAD real de `work/cierre-noche-v11-pilot-20261030`, sin tocar `main` ni el repo histórico. Verificar que no exista otra rama escritora activa. Writer único con lease/epoch y CAS `expected_sha` para todas las escrituras; A/B jamás se superponen.

Cada sesión 0–20 minutos: recuperar y desarrollar funcionalidad REAL por lotes, priorizando terminar F1 Android integrado; después F2 y F3 (opción A aprobada). Evitar perseguir microtests o subir contadores de pruebas sin cerrar un flujo. Minuto 20: no iniciar nada nuevo. Entre 20–25 cerrar trabajo en curso. Si al minuto 25 una Actions sigue en curso: registrar run ID, SHA y `WAIT_CI`, retomarla en próxima sesión, sin cancelarla ni superponer escritor. Minutos 25–30: checkpoint durable, liberar writer y cerrar, aunque haya trabajo pendiente.

**NUEVA POLÍTICA CI:** tras cambios pequeños usar verificación acotada o agrupar en un lote funcional; lanzar Actions dirigida sólo cuando existe una unidad funcional que realmente merece validación. No repetir las baterías 38/38 + 36/36 + 31/31 en cada commit, checkpoint ni simple VERDE. Reutilizar CI exitosa sobre SHA exacto. Ejecutar integración del frente y full gate al acercarse al cierre real, o ante un cambio transversal crítico justificado. Registrar cada run y duración para controlar consumo de minutos.

VERDE: continuar desarrollo funcional dentro de ventana. ROJO: investigar nueva hipótesis, corregir lo afectado y volver a probar de forma dirigida, sin reintentos idénticos ciegos. Si hay decisión humana o físico pendiente: registrar y continuar otro trabajo seguro independiente. F3 opción A ya fue aprobada, no pedirla de nuevo.

Conservar V2 OFF, S3/S4 sin abrir, T4 físico NOT_RUN, `main`, V1/V4.4, FARO Auto y repositorio histórico intactos; sin publicar, costos, secretos ni cambios de permisos. No declarar PASS sin evidencia completa. Mantener ocho criterios fijos.

**Informe final:** debe salir en la última ejecución autorizada o al cierre anticipado, con avance real S2.A6 X/8, F1/F2/F3/F4/T4, subhitos funcionales cerrados, pruebas CI y Actions consumidas/reutilizadas, bloqueos, HEAD, escritor NONE y próximo paso. Si el sistema corta antes de informar, dejar un checkpoint durable que permita recuperarlo; NO crear tercera automatización por defecto.

Desactivar A/B al llegar al corte o al completarse S2.A6. No ampliar ventana ni iniciar S3/S4 automáticamente. Verificar programación real e informar horarios exactos antes de continuar.

## Recordatorio operativo

Dos automatizaciones **individualmente horarias** intercaladas a 30 minutos. Las ventanas son objetivo y no aseguran duración real ni arranque exacto. La API de Tasks puede terminar antes; por eso son fundamentales checkpoint y writer exclusivo. La autorización expira al límite global aunque las Tasks todavía estén habilitadas.
