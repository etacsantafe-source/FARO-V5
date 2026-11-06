# FARO V5 — S3.A4 Avisos: resumen local y a pedido · B1–B5 (05/11/2026)

**Unidad acotada:** `S3_A4_AVISOS_CONSENT_SUMMARY`. **Entrada:** HEAD `234ff31a3e6c601f3299d241a69b034f552e0c6c`, último B7 Avisos pointer ABA PASS_SCOPED, permiso BLOCKED y writer RELEASED epoch 323. Estado de esta preauditoría: B1–B5 verificados únicamente para **resumen local explícitamente solicitado** sobre una página de Avisos ya autorizada. B6/B7 NOT_RUN; S3.A4 sigue abierta por N015 y N031. Sin resumen autónomo o contextual no consentido.

## B1 — evidencia exacta y aceptación
- `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5AvisosSurface.kt`: `FaroV5AvisosScreen` expone un `FaroV5AvisosReadPort?` y su página `items: List<FaroV5AvisoItem>` (máximo 100), `complete` explícito; el port nulo o fallido es **No disponible**, no cero. La pantalla usa `collectLatest` y borra primero la página al invalidarse.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveAvisosBindingFactory.kt`: el dueño Auxiliary filtra OPEN, no DISMISSED, conserva SEEN y oculta el resumen sensible como «Aviso privado»; fence de puntero pre/post ya certificado B7. La proyección no es owner, no escribe ni marca avisos.
- `v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5AvisosSurfaceContractTest.kt`: prueba actual de estado vacío y cobertura parcial, identidad duplicada y resumen en blanco. La CI `.github/workflows/faro-v5-s3-surface-directed.yml` compila surface y valida todos los XML de JUnit, con total dinámico.
- **Aceptación:** tras pulsar **«Ver resumen»** mientras una página autorizada está presente, mostrar solo totales (requieren atención/informativos) y advertencia si `complete=false`; nunca por defecto, sin egress ni almacenamiento de resumen. Port null no muestra botón ni resumen. Al sustituirse o revocarse la página la autorización efímera para mostrar el resumen se pierde. No inferir la cronología.
- **No cubre**: N015 `history.query`, N031 Matter/Reminder/MonthlyPlan, resúmenes con IA ni entrega física.

## B2 — comparación V4.4 y reutilización
- La comparación V4.4 READ_ONLY de `docs/s3-night/S3_P00_CONTRACT_REVIEW_20261102.md` prueba separación de Avisos, Historia y Home; no demuestra mecanismo V4.4 de consentimiento para resumen y por eso NO se afirma paridad inexistente.
- En V5, `FaroV5AvisosSurfacePolicy.message` ya distingue parcial/empty. **REUTILIZAR** `FaroV5AvisosPage`, conteos tipados de importancia, pantalla Compose y `readPort.invalidations`. **NO** crear store ni copiar notificaciones V4.4.

## B3 — investigación externa, alternativas, clasificación
1. Android Developers, estado Compose: https://developer.android.com/develop/ui/compose/state — la vida útil de `remember(key)` se invalida cuando cambia la página/port; **ADAPTAR** consentimiento efímero, nunca `rememberSaveable` porque restaurar consentimiento cruzando recreaciones sería incorrecto.
2. OWASP MASVS-PRIVACY-1: https://mas.owasp.org/MASVS/controls/MASVS-PRIVACY-1/ — consentimiento informado y acceso mínimo; **USAR** interacción explícita y datos ya redactados por owner.
3. OWASP MASWE-0036: https://mas.owasp.org/MASWE/MASVS-PLATFORM/MASWE-0036/ — riesgo de exposición innecesaria de información sensible en UI; **ADAPTAR** solo totales, no incluir summaries de cada aviso ni identificadores.
4. Tasks.org (app real y código abierto): https://tasks.org/docs/ — distingue notificaciones/reminders de historial y tareas; **REFERENCIA**, sin importar su stack o licencia al proyecto. Su UI no demuestra una implementación de resúmenes FARO.
- Alternativa A: elaborar automáticamente cada cambio (DESCARTAR: sin consentimiento y retención involuntaria).
- Alternativa B: IA remota para resumen (DESCARTAR: egress, pagos, falta de aprobación explícita).
- Alternativa C: proyección local de conteos agregados tras botón, solo sobre página vigente (**USAR**: sin terceros ni nuevos contratos). No afirmar «resumen IA», es un resumen determinista local.

## B4 — implementación mínima sin dispersión
1. Modificar solo `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5AvisosSurface.kt`: `FaroV5AvisosSurfacePolicy.summary(page)` forma conteos exactos de la página; avisa cobertura parcial si supera 100. Botón Compose «Ver resumen» ligado a port/página; no se muestra si página UNAVAILABLE; reset por cambio de página/port mediante `remember(readPort, current)`.
2. Modificar solo `v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5AvisosSurfaceContractTest.kt`: comprobar prioridad mezclada, vacío autorizado, 100 truncados y privacidad (nunca copiar identificadores/summary a texto agregado).
3. Reusar CI de superficie activada por `v5/surface/**`; además protocol guard, verificar SHA de código y XML reales antes de B7. No tocar workflow ni shell, stores, operaciones, red, permisos, V2, N031/N015.

## B5 — auditoría adversarial ANTES del código
- **Consentimiento:** no resumir al montar pantalla ni por actualización `Flow`; solo acción visible y directa. El permiso efímero se pierde al cambiar `FaroV5AvisosPage` o `readPort`; `rememberSaveable` prohibido.
- **Privacidad:** mostrar conteos agregados exclusivamente, ni texto de avisos SENSITIVE, ni IDs, ni timestamps inventados, ni logs, egress o almacenamiento.
- **Cobertura:** `complete=false` implica «página parcial», no afirmar total global; página vacía completa admite cero real, port null no admite respuesta.
- **Relectura:** owner actual mantiene ABA/readiness/ACL; el botón NO concede autorización de lectura extra ni retiene un `FaroV5AvisosPage` adicional. La página visible se borra por invalidaciones ya existentes.
- **Integridad/regresión:** ninguna mutación de attention/SEEN, Android OS, source Truth, Reality, Wallet, Workspace, Review ni FBS. Mantener Surface CI+B7; Android físico posterior en A5.
- **STOP:** si Compose no compila, cobertura falla, o un test demuestra exposición/consentimiento persistido, B7 RED_FIX. La preauditoría autoriza SOLO emisión posterior de READY por estas dos rutas, **no** autoautoriza B6.

**Dictamen B5: APTO PARA SOLICITAR PERMISO DE CÓDIGO LIMITADO.** Antes de B6 registrar `FARO_PROTOCOL_WORK_PERMIT.json` READY en commit documental distinto, verificar el permiso por SHA y ejecutar preflight. S3.A4 en su conjunto NO está cerrada.
