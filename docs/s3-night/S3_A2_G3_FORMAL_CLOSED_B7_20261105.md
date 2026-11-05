# FARO V5 — S3.A2 G3 · B7 cierre formal / mensaje canónico (05/11/2026 AR)

**Unidad:** `S3_A2_G3_CANONICAL_MESSAGE_ANCHOR`. **Código verificado:** `4eb0f24a9cf6f686903259984db54db4aee58c4f`. Rama exclusiva `work/s3-night-20261102`. **B1–B5:** `docs/s3-night/S3_A2_G3_CANONICAL_MESSAGE_ANCHOR_B1_B5_20261105.md` blob `dd2929ce4ea9e65b123b4e78cdaa0df03c7baf6e`. Permiso anterior READY en commit `64572ea3912670c5608aabdca27eaf981eea65d7`; `#41961920272 SUCCESS`, `FARO_PROTOCOL_PREFLIGHT_PASS`. G1 y G2 previamente CLOSED B7, no reabiertos.

## B7 AUDITORÍA FINAL

### Código, alcance y tests nuevos

- Commit `4eb0f24a9cf6f686903259984db54db4aee58c4f`, `FARO-UNIT:S3_A2_G3_CANONICAL_MESSAGE_ANCHOR`, **nueve rutas exactamente preauditadas**: SearchSurface, InChatSearchPolicy, ShellNavigation, ActiveChatScreen, ActiveChatTimeline, WorkspaceSearchReadSource, WorkspaceSearchSurfaceAdapter, test del adapter y workflow dirigido S3. Ninguna ruta ajena ni control de protocolo mezclado con código.
- Buscar global conserva resultados opacos por conversación; al abrir un resultado publicado se revalida owner REAL G1, generación activa, ACL epoch, revisión y selección canónica. Se usa la consulta emitida con esa página y el mismo normalizador en español del Chat para localizar, dentro del timeline canónico, un `ConversationMessageId` exacto y efímero.
- El adaptador solo emite referencia a mensaje **después de selección confirmada**, y se consume una vez. Revocación, nueva búsqueda, publicación distinta, selección manual y cambio de owner invalidan la pista. Shell espera una lectura Workspace posterior a la selección; Chat desplaza únicamente si el ID pertenece a su timeline actual autorizado. No hay índice nuevo, deep-link, persistencia de pista ni permiso derivado de UI.
- Coincidencia solo en título o mensaje fuera del presupuesto devuelve ancla ausente y se abre la conversación **sin inventar** salto exacto. La búsqueda sigue acotada: 250 mensajes elegibles/64k caracteres de búsqueda local en Chat, 250 elegibles y 4.096 caracteres por conversación en búsqueda global; cobertura PARTIAL cuando procede, nunca falso resultado completo. El timeline para confirmar ancla se lee desde owner canónico, no desde índice desechable.
- Pruebas nuevas: texto real persistido `Pagué las expénsas` encontrado con consulta `EXPENSAS` y ancla consumible una vez; revocación ACL y regrant distintos sin ancla stale. Se preservan filtros USER/ACCEPTED y FARO/COMPLETED existentes.

### Evidencia GitHub Actions exacta para `4eb0f24a9cf6f686903259984db54db4aee58c4f`

1. **Guard protocolo** run [#41962522527](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41962522527): SUCCESS; mismo SHA; 14 tests sintéticos y revisión de historial de permisos, ninguna ruta fuera del alcance.
2. **Surface Navigation** run [#41962522487](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41962522487): SUCCESS; `BUILD SUCCESSFUL`; XML `S3_IN_CHAT_SEARCH_XML_PASS 7/7` y `S3_SURFACE_XML_PASS 59/59`.
3. **S3 Home/Search app** run [#41962522467](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41962522467): SUCCESS; `BUILD SUCCESSFUL`; JUnit XML sin fallas: Home 9/9 + Workspace canonical 7/7 + Search bridge **7/7**, incluye dos G3 nuevas + Avisos 4/4 + Chat pointer 6/6 + Host ABA 1/1 + grant G1 10/10 + admission G2 10/10 = **54/54**. Merged Android manifests single-process/backup **2 PASS**.
4. **V2 core aislado** run [#41962522595](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41962522595): SUCCESS; tres `BUILD SUCCESSFUL`; `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`, `V2_CORE_M2B_DEV_APP_ISOLATED_PASS 4/4`, `V2_CORE_M1_ISOLATION_PASS 4/4`. No V2 productiva.
5. **APK aislada DEV** run [#41962522468](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41962522468): SUCCESS; paquete `ar.com.faro.alpha.v2coredev`; launcher exclusivo `ar.com.faro.v5.app.FaroV5V2CoreDevActivity`; verificador de permisos OS no permitidos PASS, firma v2 verificada; APK SHA256 `b6e2022148d520a5ca4906b1cfe1a67444a9c17e79d6e20ec247df59ad7e65bf`; artefacto `13385824103`. **Compilada, no instalada físicamente**.

### Dictamen adversarial y límites

- Los casos de stale/revoke/permission e invalidaciones que integran G1/G2 permanecen verdes; G3 añade ancla exacta sin conceder autoridad. Selección confirmada no implica permiso para leer otro mensaje: el Chat valida presencia del ID en snapshot real.
- **Scope certificado:** búsqueda de contenido canónico, lenguaje español/acento, navegación al mensaje encontrado dentro de los presupuestos, relectura de Workspace y degradación honesta. No es garantía de búsqueda exhaustiva por todas las conversaciones ni todos los mensajes, ni acceso UI productivo con V2 flag OFF. Consultas más extensas siguen PARTIAL.
- **Sin pruebas físicas** Android de rotación, cold restart de UI o lifecycle, ni usuarios/procesos múltiples. La única campaña física global continúa reservada para G5. G4 federación real de Finanzas/Objetivos/Asuntos **OPEN**. No afirmar que G3 cierre S3.A2 íntegra ni habilite V2 productiva.
- S2 CLOSED 8/8 100%, S3 A1–A5 0/5 formal, desarrollo S3 orientativo ≈40% (35–45%, no certificación); **A2 G1/G2/G3 CLOSED B7, 3/5 = 60%, G4/G5 abiertos**. S4, V2 productiva, Auto y Noche OFF; CLOUD STAGED; catálogo 172 inalterado.
- Escritor exclusivo epoch 227 adquirido para documentación B7 y previsto RELEASED epoch 228; revalidar al cerrar. No usar CI verde como prueba física.

**Resultado:** `PASS_G3_CLOSED_B7_CANONICAL_MESSAGE_NAVIGATION_JVM_ANDROID_COMPILE_ONLY`. Prohibir más código G3 sin nueva evidencia/permiso; siguiente unidad prioritaria G4, repitiendo B1–B5 y nuevo permiso scoped.