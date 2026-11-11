# FARO V5 — Orden autorizado de ejecución S3.A5.7 · 2026-11-11

## Decisión humana y autoridad

El usuario confirmó expresamente «Ok. Déjalo asentado. Vamos a chat nuevo con ese orden», aprobando la **prioridad secuencial de trabajo** convenida en chat: **X03 → X09 → X10 → X01 → X02 → X06 → X08 → X12 → aceptación global X01–X12 → una campaña física final Moto G15**.

Este documento **asienta la planificación, NO concede permiso READY ni certifica B1–B5/B6/B7 de unidades futuras**. El permiso efectivo permanece `BLOCKED`, y se exige B1–B5 verificado, autorización documental acotada READY en commit separado, guardia/preflight, writer global CAS, implementación B6, CI real XML/APK y B7 independiente antes de cerrar cada unidad. Si una dependencia obliga a alterar el orden, registrar motivo y alcance verificable en el checkpoint; no saltar silenciosamente de unidad.

## Cola de ejecución prevaleciente para el próximo chat

| Prioridad | Unidad | Brecha concreta y resultado exigido |
| --- | --- | --- |
| 1 | **X03 — Consultar FARO con IA** | Preparar B1–B5 del gesto explícito Chat→Consultar FARO, límites de consentimiento/egreso y puerto de IA autorizado en DEV; **NO asumir que Groq aislado S2 ya está cableado al host V2 DEV**. Programar solo tras nuevo permiso READY por rutas. |
| 2 | **X09 — Review explícito** | Candidato y revisión humana real, sin Apply implícito ni falsa autoridad; integrar la salida de X03 bajo contratos existentes. |
| 3 | **X10 — Apply, receipt e idempotencia** | Apply únicamente tras autorización, receipts reconciliables, UNKNOWN sin segundo Apply, replay seguro, recovery fail-closed. No activar producción. |
| 4 | **X01 — Actividad e historial** | Completar receipts COMMITTED reales y origen/cobertura y navegación integrada. Conservar PASS acotado API35 X01/X02 3/3: el evento USER Workspace y fail-closed no certifican todo el historial. |
| 5 | **X02 — Avisos y Pendientes** | Fuentes reales admitidas y estados no disponibles/partiales; no inventar ceros ni marcar leído. Reusar PASS existente. |
| 6 | **X06 — Búsqueda federada** | Ampliar X06 Workspace/UI 2/2 API35 a owners reales Finanzas, Objetivos y Asuntos, sin revalidar suites ya cerradas sin delta. |
| 7 | **X08 — Cobertura y ACL** | Completar revocación/owner-down midread, degradación, ABA, cobertura parcial frente a sin resultados; reutilizar truncación real 1/1 y background/recreate 1/1 ya certificados en sus SHAs. |
| 8 | **X12 — Android integral** | Integrar foreground/pause/resume/recreate, accesibilidad, tamaños y recursos adaptativos; reutilizar native lifecycle 2/2 y Compose Chat 2/2 ya certificados según alcance. |
| 9 | **Aceptación integral X01–X12 y Moto G15** | Auditar X04, X05, X07, X11 ya aprobadas acotadamente, cerrar brechas globales, pasar CI final exacta; UNA sola campaña física al final, sin fingir PASS productivo. |

## Evidencias terminadas: NO repetir sin causa

- **S2 CLOSED 8/8**, congelada; no reabrir.
- **S3.A1/A2/A4 CLOSED_AUTOMATED_SCOPE**, 3/5 cierres automatizados; 0/5 entregas finales.
- **S3.A3 Guardados M01–M05 B7 PASS DEV automatizado**, no volver a programar; A3 OPEN únicamente hasta aceptación física.
- **X04/X05 B7 PASS SCOPED Android API35 2/2**, código `2d1007e8b0e557c716e95402dad5bd9fe638a433`, run `43084379020`, guard `43084378819`, B7 `docs/s3-night/S3_A5_7_X04_X05_ANDROID_B7_PASS_SCOPED_20261111.md`.
- **X07/X11 B7 PASS SCOPED JVM 4/4**, código `87b1c2ed8d31b5385249c312bbbe7ec358838a30`, run `42558390722`.
- **X01/X02 B7 PASS SCOPED API35 3/3**, run `42623474908`; **X06 API35 2/2** run `42627471693`; **X08 parcial canónico API35 1/1** run `42633273852`; **X08/X12 background API35 1/1** run `42717372040`. Todos son alcances *scoped*, no aceptación global completa.
- El catálogo oficial tiene **180** operaciones: 172 originales preservadas + 8 Guardados aprobadas y registradas (run `42927956001`).
- A5.1–A5.6 tienen sus 33/33 pruebas certificadas históricamente; conservar SHA y evidencia sin repetir gratuitamente.

## Invariantes y handoff operativo

1. Autoridad siempre: HEAD GitHub actual, `AGENTS.md`, `docs/v5/FARO_CONTINUITY_POINTER.json`, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`, este plan y documentos de la unidad exacta. El checkpoint de entrada al registro era `e115e8049cf45af889d3a56fb82d10458253a2f4`; **recuperar el nuevo HEAD real** antes de trabajar.
2. Mantener writer único mediante CAS; si ajeno HELD/UNKNOWN, STOP en READ_ONLY. Permiso de código **BLOCKED** hasta emitir otro de X03 específicamente y verificable. No reutilizar permiso consumido de X04/X05.
3. El chat preparador hace B1 investigación, B2 comparación con V4.4, B3 alternativas externas, B4 diseño, B5 auditoría adversarial. Work o turno ejecutor idealmente **programa B6, corrige CI, confirma XML/APK y B7**, sin rehacer investigación ya válida. Investigar huecos nuevos solo mediante delta.
4. No modificar `main`, V4.4, S2 congelada ni habilitar V2 productiva, S4, Auto o Noche. No usar APIs pagas ni tocar Moto G15 antes de campaña final.
5. Informar porcentajes separados: 60% = 3/5 etapas automatizadas cerradas; 0% = 0/5 aceptaciones productivas; `≈40%` es solo estimación histórica **desactualizada**, no recalcular por conteo de X. Documentar pruebas y writer al cierre.

**Instrucción breve de próximo chat:** «Continuar FARO V5 desde el checkpoint GitHub. Respetar `docs/s3-night/S3_A5_7_EXECUTION_ORDER_V1_20261111.md`. Comenzar con X03: recuperar HEAD/writer/permiso; completar B1–B5 y emitir permiso READY separado solo si procede; después B6/CI/XML/APK/B7. No repetir PASS, writer CAS, G15 físico al final».
