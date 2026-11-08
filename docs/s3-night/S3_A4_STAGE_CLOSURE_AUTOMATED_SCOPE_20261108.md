# FARO V5 — S3.A4 Seguimiento · dictamen final de alcance automatizado
Fecha AR 2026-11-08. Fuente contractual `docs/s3-night/S3_BACKLOG_20261102.md` y conciliación de responsabilidades `docs/s3-night/S3_A1_FORMAL_SCOPE_RECONCILIATION_20261105.md`. Rama `work/s3-night-20261102`, HEAD funcional de referencia `715d1069c5ea5136c336aac73542e5e25a2d6efa`.

## B7 — Matriz de aceptación sin doble contabilización
| Contrato A4 | Evidencia estable | Dictamen |
|---|---|---|
| Avisos Prioridad/Otros sin mutar origen ni auto-marcar leído | `FaroV5ActiveAvisosBindingFactory`, surface real Auxiliary y B7 en `S3_A4_CLOSURE_GATE_READ_ONLY_20261108.md` | PASS ACTIVE V1 |
| Resúmenes autorizados, sin snooze no contratado | `docs/s3-night/S3_A4_AVISOS_CONSENT_SUMMARY_B7_PASS_20261105.md`, source ACL por usuario/gesture y negación default | PASS_SCOPED |
| Actividad cronológica basada en recibos COMMITTED y Workspace | `FaroV5RealHistoryActivityHostBinding` 28/28 SQLite + `FaroV5ActivityScreen` 68/68 Surface; `FaroV5V2CoreDevActivity` M5 #42518963641 | PASS_SCOPED DEV, no shipping |
| Autoridad por instalación, dominio/epoch/puntero y revocación | `FaroV5LocalInstallReadAuthority` 10/10; DEV Activity onPause revoke; sin cuenta humana, no write grant | PASS_SCOPED |
| Pendientes transferidos desde A1: Asuntos+Recordatorios+Plan mensual reales | `FaroV5N031ThreeOwnerDueHostBinding` 8/8 SQLite reales y DEV botón montado `715d1069` con 6/6 formatter y APK M5 #42521651660 | PASS_SCOPED_READER_AND_DEV_UI, schema8 source REQUIRED |
| Total Home global completo, prioridad/snooze/expansión de recurrencias | Faltan fuentes/semántica y Reality8 activa; UI niega datos, jamás inventa cero o prioridad | INTENCIONALMENTE NO CERTIFICADO |
| E2E de navegación/pause-resume/click en dispositivo, accesibilidad instrumentada | A5 campaña física única X01–X12 | NOT_RUN, explícitamente reservado para A5 |
| Shipping V2 habilitado | `FaroV5HostCertification.V2_ACTIVE_ENABLED=false` | OFF, no es entrega A4 |

## Decisión de etapa y delimitación
**S3.A4 = CLOSED_AUTOMATED_SCOPE_READ_ONLY_DEV**, en el MISMO sentido formal de A1/A2 `CLOSED_AUTOMATED_SCOPE`: se cierra el desarrollo verificado y las guardas de Seguimiento **sin** declarar cierre productivo de las fuentes ni funcionamiento completo en Android real. El código de esta unidad está B7 PASS; la prueba física está reservada A5 y queda prohibido saltar sus gates.
- Pendiente indispensable de A5/integración: candidato STAGED Reality8 y cutover seguro/admisión de host activo sin migrar la generación activa; luego permiso local correcto, render de hechos visibles reales, navegación, recuperación y seguridad frente a background, pruebas de pantalla, Home solo con cobertura completa. Si no se obtiene schema8, Pendientes debe decir «No disponible», no 0.
- A3 sigue **IN_PROGRESS_REVIEW_ONLY**, catálogo Guardados oficial 172 intacto; no suponer aprobación de las 8 operaciones candidatas. A5 global sigue OPEN; S4/Auto/Noche OFF y V2 productiva OFF. A4 no autoriza ejecutar Apply, Undo, tareas de fondo o servicios.
- Cierre de alcance S3 computable: A1+A2+A4 = **3/5 etapas = 60% del alcance automatizado**; 0/5 entregas productivas finales, no interpretar 60% como desarrollo físico ni confirmar un porcentaje heurístico. A3/A5 continúan pendientes.
- Escritura final del estado se realiza en `docs/v5/FARO_CONTINUITY_POINTER.json`, permiso al final `BLOCKED`, writer separado RELEASED, sin repetir pruebas previas.
