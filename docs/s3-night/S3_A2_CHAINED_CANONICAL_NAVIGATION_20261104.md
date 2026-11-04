# FARO V5 S3.A2 — Búsqueda → Chat fresca y fragmento canónico de texto largo · 04/11/2026

## Tareas encadenadas (única prioridad A2)

### A. Diagnóstico de G1 (READ_ONLY, sin simular ACL)
- El host activo V1 no dispone del quinto Workspace. `FaroV5V2WorkspaceHostBinding` es candidato cuarentenado: no activa ni promueve generación.
- `FaroV5WorkspaceSearchReadSource` requiere ACL epoch, generación y admisión reales; en los tests los suppliers ACL son inyectados para casos de rechazo. `FaroV5HomeReadinessInvalidations` no es un emisor independiente de ACL.
- `FaroV5BootstrapActivity` deja Search unbound; mantener `searchReadPort=null`. No inventar grants, no V2 ni promoción.

### B. Corregido: Search CONFIRMED → Chat con selección vieja
- Commit `abca920c3300a2d51f55b2633eb0e945094eec73`, rutas `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt` y prueba `FaroV5ShellNavigationContractTest.kt`.
- Antes, el adaptador seleccionaba en Workspace, pero el scaffold retenía el ID activo leído antes de Search y navegaba de inmediato a Chat. Ahora borra ese estado, incrementa la revisión de lectura y espera una nueva lectura canónica; si falta Workspace, falla o no hay selección válida, no navega con el Chat anterior.
- GitHub Actions **#41853477492 SUCCESS**, mismo SHA del código, log XML `S3_IN_CHAT_SEARCH_XML_PASS 7/7`, `S3_SURFACE_XML_PASS 59/59` y `BUILD SUCCESSFUL in 3m 10s`. El test dirigido no reemplaza instrumentación de UI ni físico.

### C. Publicado: prefijo de mensaje canónico largo sin falsa certeza
- Commit `f64699e59e9fe0c287d07e3655b49a69234cb1e9`, `ConversationWorkspaceStore.readSearchableConversationText` mantiene el filtro USER ACCEPTED / FARO COMPLETED, límite de 250 elegibles + sentinel 251 y presupuestos existentes.
- En vez de descartar el mensaje reciente si excede `maxChars`, conserva solamente el prefijo que entra dentro del presupuesto y emite `complete=false`. Se preserva un resultado verdadero al inicio y nunca se afirma ausencia definitiva para la cola omitida.
- Se agrega `oversizedRecentMessageKeepsBoundedPrefixAndReportsPartialTail`; el workflow `faro-v5-s3-home-notice-directed.yml` ahora requiere XML Workspace **7/7**. No hay cambios de esquema, FBS/backup, claves, credenciales o índices secundarios.
- CI [#41854013498](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41854013498), SHA exacto `f64699e5`: **IN_PROGRESS**, conclusión PENDING en momento de este checkpoint. **NO declarar PASS sin log XML**. Si pasa, esperan 9/9 Home +7/7 Workspace +5/5 Bridge +4/4 Avisos = **25/25**.

## Bloqueos restantes / retorno a G1
- G1 OWNER ACL REAL ABIERTO: monotonic epoch independiente + revocación/regrant observable y enlazada a host activo; puntero/readiness no sustituyen ACL.
- G2 Search shipping no montado; G3 contenido e in-chat parcial sin salto directo global al mensaje certificado; G4 multi-owner real pendiente; G5 aceptación X01–X12 y única campaña Android final.
- S2 100% (8/8), S3 ≈40% desarrollado (35–45% estimado) / 0% de cierre formal (0/5), A2 0/5 gates integrales; no aumentar cifras por sumar tests.
- S4, V2 app, Auto y Noche OFF, cola CLOUD STAGED, catálogo 172 sin cambio, Guardados +8 solo candidatos.
- No repetir 59/59 ni 24/24 aprobados sobre SHA previos; siguiente: confirmar CI de prefijo y, si verde, registrar XML exacto; si falla, corrección mínima y revalidación. Luego G1 real; si bloqueo persiste, trabajo G3 estrictamente adyacente sin inventar autoridad.
- Writer epoch 132 debe quedar RELEASED al cerrar checkpoint. Tiempo efectivo no instrumentado; no inferirlo de CI.
