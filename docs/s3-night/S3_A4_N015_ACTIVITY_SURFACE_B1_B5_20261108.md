# FARO V5 S3.A4 N015 Actividad: pantalla read-only autorizada — B1–B5
Fecha AR 2026-11-08. Unidad `S3_A4_N015_ACTIVITY_AUTHORIZED_SURFACE`, baseline `63092634ed4089ab7dde7b8cf0f65f3fcdf114de`. Writer CAS epoch562; S3.A4 OPEN, V2 productiva OFF, S4/Noche/Auto OFF.

## B1 — Aceptación y estado real
- S3 backlog A4: Actividad receipt-backed, Avisos prioridad/Otros, resúmenes autorizados y no auto-mark-read. Avisos ya existe y está activo sobre Auxiliary real B7; no tocar sus 4 tests ni reimplementar.
- Drawer incluye `FaroV5ShellDestination.ACTIVIDAD`, pero `FaroV5ShellNavigation` no crea pantalla de Actividad: pasa a destinationContent genérico. Hay `FaroV5FederatedHistoryReadSource` 2-owner B7 PASS 6/6, pero es **app read source dormido**, no owner autenticado ni permiso Activity en shipping. No agregar cliente UI directo que consulte Reality/Workspace ni forzar `V2_ACTIVE_ENABLED=true`.
- Objetivo scoped: pantalla Compose `FaroV5ActivityScreen` integrada al destino Activity, puerto de lectura **neutro** inyectable solo por futuro host de confianza; null=NO_DISPONIBLE, page empty=vacío SOLO dentro de fuentes admitidas. Indicador de cobertura parcial siempre: dos fuentes ≠ global history.query. Cero datos de objetos privados en DTO, no UI click mutante.
- Aceptación dirigida: 5 JUnit de modelo/política, source code compilable, screen render branch, regresión Surface existente exit0 con XML; no fingir Android physical G15 ni certificado host. Revisión UI final A4 requiere después proveedor canónico autorizado y pruebas e2e.

## B2 — Reuso y comparación
- V4.4 proveía concepto Actividad/seguimiento en drawer, solo referencia de navegación; no importar source V4.4 ni datos. V5 ya tiene `FaroV5AvisosScreen` Compose `produceState + invalidations + reset consent`; copiar **patrón**, no lógica de dominio.
- V5 `FaroV5FederatedHistoryReadSource` conserva payload opaco, procedencia temporal y cursor ligado a ACL; no duplicar Query/SQL en Surface. Adaptación DTO mínima expresa COMMITTED_REALITY o WORKSPACE_MESSAGE, epoch tiempo, explicit provenance, sin title, detalle, body, ID visible ni consentimiento persistente.
- `FaroV5ShellScaffold` acepta puertos opcionales Home/Search/Avisos; agregar Activity opcional con default null, sin modificar callers existentes ni encender DEV.
- Excluir Agenda/MonthlyPlan/Finance como «fuente global de Activity» hasta cobertura probada; la pantalla no puede afirmar historia global completa.

## B3 — Investigación comparada y decisiones
- Android architecture https://developer.android.com/topic/architecture/ui-layer : UI observa estado del owner y muestra indisponibilidad, no inventa owner. ADAPTAR Compose state con readPort nullable.
- Android UI state production https://developer.android.com/develop/ui/compose/state : `produceState` y estado ligado a key readPort; ADAPTAR reset de caches/autorización ante invalidaciones, evitando render obsoleto.
- OWASP Authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html : negar sin autoridad. NO es suficiente visualizar un puerto para conceder datos. MANTENER null por defecto para shipping.
- SQLite https://www.sqlite.org/isolation.html : Reality y Workspace no tienen snapshot causal común. Marcar `Cobertura parcial` aunque ambas fuentes hayan emitido todas sus filas; no presentar cronología causal global.
- Alternativas: A USAR puerto UI neutro read-only, sin derechos implícitos; B RECHAZAR inyectar `FaroV5FederatedHistoryReadSource` en Surface (dependencia circular app→surface); C RECHAZAR usar backup como history, D RECHAZAR activar V2/ACL fake, E RECHAZAR tiempos o texto de eventos sintéticos.
- Sin API pagos, licencias ni librerías nuevas.

## B4 — Diff y pruebas acotadas
1. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ActivitySurface.kt` DTOs opacos y semántica `FaroV5ActivityReadPort`, pantalla Compose, invalidations y solicitud de resumen SOLO desde página autorizada, no auto mark-read, render partial.
2. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt` parámetro final opcional `activityReadPort`, rama ACTIVIDAD al lado AVISOS.
3. `v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5ActivitySurfaceContractTest.kt` cinco tests: vacío no global, parcial, conteos sin IDs/timestamps, orden y tipos, input inválido; no confundir política UI con host ACL.
4. `.github/workflows/faro-v5-s3-a4-n015-activity-surface-directed.yml` compile Surface + 5 JUnit nueva y regresión nav/Avisos (exact xml) junto con guard global.
- No cambiar SurfaceHost, CompositionRoot, app Activity ni Realm; no inventar otra base. B6 solo rutas anteriores, FARO-UNIT exacto, previo B7 certificado.

## B5 — STOP y auditoría adversarial
- `readPort=null` o exception -> mensaje «Actividad no disponible», no lista vacía ni 0 eventos. Cambios de grant/epoch emiten invalidations desde host auténtico futuro; la pantalla descarta page al inicio del ciclo; jamás conservar resumen autorizado entre páginas/epochs.
- Nunca render ID opaco ni texto message del Chat; etiqueta «Registro de aplicación» / «Mensaje de Workspace», tiempo registrado con provenance especificada, no afirmar causalidad. No permitir datos inventados/parse arbitrario de eventKind de fuentes.
- Cobertura `globallyComplete=false` fijo, incluso si ambas fuentes parciales están completas. Resumen local solo después de click y solo cuentas de la página: no IA remota ni egress.
- Sin source autorizada, no elevar la UI a un PASS de N015 ni cerrar A4; el producto necesita host que inyecte port autenticado y verificación end-to-end en fases posteriores. A5 física NOT_RUN; catálogos 172, V2 OFF, main/V4.4 untouched.
- CI exigido en exact SHA, 0 failures/errors/skipped + revisión B7 independiente; STATUS A4 sigue OPEN.
