# FARO V5 — S3.A1, B3–B5 investigación y auditoría dirigida — Noche B 05/11/2026 15:30 AR

## Alcance y evidencia recuperada
Continúa `S3_A1_NOCHE_A_B1_B2_RECOVERY_20261105.md` (B1/B2). HEAD inicial de B: `e4c1c8075fe946ca68a410ff280b6f0f61f17560`; writer inicial RELEASED epoch 277, cola ENABLED hasta 19:00 AR. No existe permiso de trabajo funcional A1: `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` corresponde a `S3_A2_G5_ANDROID_EMULATOR_DIRECTED`, no es transferible. S3.A2 5/5 CLOSED_AUTOMATED_SCOPE; X01–X12 y campaña Moto G15 en S3.A5 NOT_RUN. No se ejecutaron Actions ni pruebas nuevas en este turno. No hay evidencia de un fallo CI más reciente en esta unidad: el conector no ofrece listado general de runs por rama, y buscar commits globales devuelve main histórico; **CI reciente A1 no verificada en este turno**. No confundir ausencia de evidencia con SUCCESS.

## B3 — investigación externa material, alternativas y decisión
1. **Android Developers, guía offline-first**: https://developer.android.com/topic/architecture/data-layer/offline-first. Recomienda repositorios de datos con fuente local canónica y UI observadora, evitando que UI compute una verdad independiente. **ADAPTAR**: mantener Home read-only como proyección de puertos de propietarios reales, no crear un contador paralelo. Sin API ni dependencia nueva; compatible Android y offline.
2. **Flutter, arquitectura offline-first**: https://docs.flutter.dev/app-architecture/design-patterns/offline-first. Presenta repositorios como fuente única y acceso unificado a fuentes locales/remotas. **REFERENCIA** de patrón, no importar Flutter ni reescribir Kotlin: alto costo y duplicación sin beneficio para S3.A1.
3. **Android Room 3, invalidación multiinstancia**: https://developer.android.com/reference/androidx/room3/RoomDatabase.Builder#enableMultiInstanceInvalidation(). Broadcast de cambios de tablas entre instancias si ambas optan, no habilitado por defecto; no funciona con bases in-memory ni archivos distintos. **REFERENCIA CONDICIONAL** para datos realmente Room; **DESCARTAR como sustituto de revocación ACL, cambio de active-generation, autoría de Matter o lectura de Reality**. Es invalidación de datos, no autorización.
4. **OWASP MASVS-PRIVACY-1**: https://mas.owasp.org/MASVS/controls/MASVS-PRIVACY-1/. Minimización de datos/acceso; **ADAPTAR** fail-closed y revalidación de identidad/ACL antes y después de obtener cada agregado, sin enviar datos fuera del dispositivo. Sin librería ni API pagas.
5. **CommonsWare, Room Across Processes**: https://commonsware.com/Room/pages/chap-processes-001.html. Documenta que invalidaciones observadas en un proceso no se propagan automáticamente a otros; **REFERENCIA** para prueba adversarial de cambio de proceso/instancia. No asumir soporte multiproceso de FARO sin evidencia.

Evaluación: alternativas 1 (puertos de owners canónicos + invalidación explícita), 2 (caché/contador independiente en Home), 3 (Room invalidation como bus universal). **USAR 1**; **DESCARTAR 2** por divergencia, ACL stale y duplicación; **DESCARTAR 3 como bus universal** por límites de Room y falta de semántica de autoridad. V4.4 FollowUp/History son referencias semánticas, no se copian servicios mutantes al Home. No hay licencia de código externo incorporada, dependencia nueva ni coste de API.

## B4 — unificación mínima, DELTA ONLY
- **Avisos N032**: conservar binding real y tests existentes, no repetir 9/9 por rutina.
- **Actividad N015**: construir únicamente cuando se identifique read-owner canónico de receipts COMMITTED con paginación/cursor, tiempo verificable (no inferir fecha de ID), identidad de generación, admission ACL y cobertura. El RealityStore existente con cursor lexicográfico es activo parcial, no `history.query` completo. Home debe presentar UNKNOWN mientras no exista dueño seguro.
- **Pendientes N031**: reutilizar proyección Matter 4/4, pero primero identificar autoridad persistente real de asuntos, recordatorios y estados snoozed/resolved con semántica de vencimiento/zona horaria; no convertir Workspace.pendingReview en Pendientes.
- **Guardados**: mantener UNKNOWN y catálogo 172; +8 candidatas no aprobadas. Investigar un read-port ya existente de favoritos oficiales si aparece, sin inventar conteos ni ampliar operaciones.
- Mantener Chat-first, drawer único, cuatro indicadores, accesibilidad ya probada en JVM. No habilitar V2 productiva, no abrir S4, no añadir flujo de red.
- **Orden propuesto**: localizar en código propietario y ruta de lectura N015, diseñar tests negativos y permiso por rutas; si owner no existe, documentar bloqueo y pasar a N031 u otra subunidad S3 automatizable. No crear adaptadores especulativos.

## B5 — matriz adversarial previa (criterios de aceptación, NO pruebas ejecutadas)
| Caso | Riesgo | Criterio binario para un futuro B6 |
|---|---|---|
| A1-01 | Receipt PREPARED / rollback visible | No aparece en Actividad ni altera conteo |
| A1-02 | Commit sin timestamp fiable | Actividad no inventa cronología; unknown o orden contractualmente definido |
| A1-03 | ACL revocada entre read y render | Agregado sensible se suprime; no filtra conteo ni snippet |
| A1-04 | Generación cambia durante paginación | Resultado de generación anterior se descarta, no mezcla páginas |
| A1-05 | Restauración/crash con pointer stale | Home pasa a UNKNOWN y se revalida desde owner |
| A1-06 | Matter resuelto, snoozed o fuera de zona horaria | No cuenta como pendiente si política no lo permite |
| A1-07 | Doble fuente de Matter/Reminder | Identidad estable evita doble conteo; si cobertura parcial, indicar UNKNOWN |
| A1-08 | Store cerrado o ACL owner no disponible | Fail-closed; nunca mostrar 0 ficticio |
| A1-09 | Multi-proceso/Room invalidation ausente | No retener agregado stale ni asumir evento que no existe |
| A1-10 | Guardados no aprobado / catálogo 172 | No muta catálogo, no muestra conteo ficticio |
| A1-11 | Egress/log/backup | No emite contenido sensible a red, logs ni backups |
| A1-12 | A11y / UI físico | Diferenciar explícitamente 0 de no disponible; Android real pendiente campaña única |

## Dictamen
**B3 INVESTIGATED; B4 PLAN_RECONCILED; B5 MATRIX_DRAFTED, NOT VERIFIED AGAINST ACTUAL FILE PATHS / OWNERS.** No emitir permiso READY para B6: falta inventario de rutas funcionales exactas, dueño canónico de N015/N031 y prueba de admisión ACL. No se modificó código, no se lanzó CI ni se afirmó PASS. Próximo turno debe comprobar código real y completar B5 específico por rutas; sólo después actualizar permit por unidad y ejecutar B6 mínimo. Si no hay dueño, investigar otra subunidad S3 segura sin repetir G1–G5.

Estado global S3 **1/5 etapas cerradas en alcance automatizado**; desarrollo **~40% estimación histórica, no recalculada**. Tiempo efectivo/productivo **NO_REGISTRADO**.
