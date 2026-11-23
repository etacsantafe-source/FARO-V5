# FARO V5 — W04 F2C1B1B1 · admisión de owners reales + lectura positiva Android DEV · B1–B5

Fecha: 2026-11-23 (Argentina). Rama `work/s3-night-20261102`. Baseline remoto `766ac7c4d3867928f3b23134b7e5ce56f9b08d50`.
Unidad propuesta: `S4_W04_F2C1B1B1_REAL_OWNER_POSITIVE_ANDROID_DEV`. **Estado: PREAUDIT CON STOP EN B5. NO READY; NO B6 NI B7.**
Writer manual autorizado por el usuario y adquirido con CAS, epoch 1717. F2C1B1B0 B7 PASS (no repetir).

## B1 — Evidencia del HEAD y brecha exacta

- F2C0 `FaroV5Household10Evidence2DevSelectionReadPort` solo abre una generación seleccionada ACTIVE Household10/Evidence2 con lease único y material gate; test JVM anterior de **datos tipados positivos 6/6** no autoriza políticas Android.
- F2C1B1A `FaroV5W04CommercialDevReadConsentIssuer` exige sandbox privado + dos taps locales por necesidad/producto + revocación foreground; Android35 5/5 B7 PASS limitado al consentimiento.
- F2C1B1B0 `FaroV5W04CommercialDevTrustedSelectedHost` valida N-033 `shopping.snapshot.read`, N-034 `commercial.price_history.read`, callbacks Household/Evidence independientes y raíz/puntero/policy/epoch. Código `eae8d60008f0a9de4599a20706e18adbe9e60ac1`; CI Android35 `45181057697` 5/5 + guard `45181057574` SUCCESS; checkpoint `766ac7c4d3867928f3b23134b7e5ce56f9b08d50`, guard final `45182583818` SUCCESS. **Los tests son negativos: no hay apertura positiva de ACTIVE real en Android.**
- Revisión de código de `v5/owner/household/src/main/…/api/HouseholdNeedModel.kt`, `v5/evidence/src/main/…/CommercialEvidence.kt` y el host Android: se encontraron modelos/opcode y callbacks inyectables, pero **no se encontró un emisor de capacidades de lectura de los owners atado a una autoridad real bajo la misma generación** en esas rutas. En particular, `{ _, _, _ -> true }` en un test no equivale a una autorización de owner. No publicar datos con ese atajo.
- `S4W04SelectedActiveTypedReadProjection` y `S4W04HistoricalAuthorizedReadCoordinator` ya convierten/proyectan y revalidan F1, D4 y E. El panel `FaroV5W04CommercialReviewPanel` tiene `renderAuthorized` con limpieza por revocación, pero el launcher sigue inerte.
- Resultado exigido por F2C1B1B1: **un registro REAL almacenado de necesidad abierta y una observación Evidence2 tipada**, generación ACTIVE sellada y reabierta, selección humana explícita, **políticas READ independientes emitidas por los owners** y revalidación antes/durante/después de F1/D4/E, APK Android DEV API35 con XML positivo. El resultado debe seguir siendo revisión histórica, nunca precio actual/cotización ni Apply.

## B2 — Reutilización y contraste V4.4

- V4.4 conserva la intención UX: interacción visible, selección explícita, controles sencillos y lectura comprensible. **No reutilizar ACL, base, permisos ni Apply de V4.4** para V5. El contraste puntual de UX está documentado en F2C1B1A/B0; la revisión independiente de código V4.4 no se repitió en este turno.
- Reutilizar sin recertificar: F2B2C cutover + F2C0 single-leased staged ACTIVE, F2C1A lifecycle, F2C1B0 projection, F2C1B1A consent, F2C1B1B0 trusted host, `S4W04CommercialNeedProductSelection`, `S4W04CommercialCartDraft`, `S4W04HistoricalAuthorizedReadCoordinator`, `S4W04CommercialReviewPresentation`, panel nativo E. Ninguna duplicación de SQLite/AtomicApply/owner ni nueva moneda convertida.

## B3 — Investigación externa y alternativas

Fuentes de referencia revisadas para esta preauditoría:
- Android Activities, onPause/onStop/onDestroy y revocación: https://developer.android.com/guide/components/activities/activity-lifecycle
- SQLite, readers de WAL y aislamiento de snapshots: https://www.sqlite.org/isolation.html y https://www.sqlite.org/wal.html
- OWASP MASVS (proyecto open source, control explícito de autorización y seguridad móvil): https://github.com/OWASP/masvs ; orienta revisión, **no se incorpora su software ni se necesita dependencia ni API paga**.
- Android app-specific storage: https://developer.android.com/training/data-storage/app-specific

Comparación:
1. `Intent`/flag/UID/consentimiento como grant o callbacks siempre positivos: **DESCARTAR**; falso owner, confused deputy, rotación de scope.
2. Abrir una segunda conexión SQLite o generar resultados comerciales en el panel: **DESCARTAR**; distintos snapshots y fuente falsa.
3. Mover la política al dueño de Household y al dueño de Evidence, exponer puertos READ_ONLY independientes limitados a `principal + operación + need/product + generation/epoch`, enlazarlos por composición DEV, y consumir en F2C0/host sin reabrir la base: **ADAPTAR**, depende de auditoría del verdadero origen de autorización. No confundir la existencia de un contrato N-033/N-034 con permiso positivo.
4. Solo pruebas de rechazo hasta tener el emisor real: **USAR transitoriamente** para conservar fail-closed. Es lo único actualmente certificado en Android; no habilita vista D4/E.

## B4 — Diseño delta propuesto (NO rutas autorizadas hasta resolver B5)

1. Determinar el punto de composición que posee realmente el lease y las autoridades de lectura de Household10 y Evidence2, en `v5/store/generation`, `v5/owner/household`, `v5/evidence`, sin fabricar grants en la Activity. Emitir dos verificaciones independientes **desde los owners**, no desde el renderer ni el modelo.
2. Hacer que el compositor solo admita una raíz privada canónica, `ActiveGenerationPointer` completo con secuencia, staged descriptor sellado, `MaterialWriteGate` único, epoch foreground y `technicalPrincipal` del issuer. Un estado revocado nunca reanima tras A→B→A.
3. Para una selección explícita de `needId` y `productKey`, usar lecturas reales F2C0 frescas; conectar las callbacks de F1 a una lectura revalidada en el mismo lease. Construir un `S4W04CommercialCartDraft` de una línea **solamente después de la decisión humana de producto y cantidad**, y elegir una `ExplicitChoice` para una observación existente (evidenceId real, merchant, moneda/fecha, no “más barata” automática). No seleccionar ninguna observación por defecto.
4. Ejecutar `S4W04HistoricalAuthorizedReadCoordinator.project` (histórico, max age explícito), y presentar E solo si `Decision.Historical` y una segunda revalidación de ambos owners sigue positiva. `renderAuthorized` siempre limpia ante revocación/lifecycle.
5. Emulador API35: fixture real Household10/Evidence2 con mínimo 1 necesidad OPEN y 1 producto con terms schema2, cutover ACTIVE; prueba positiva requiere gesto humano local, emisor de owner real, elección de observationId, proyección D4 y panel E. Negativas: permiso de solo 1 owner, wrong root/staged, ABA, revocación intermedia, duplicate/UNKNOWN terms, observación no elegida, epoch/foreground, leakage de otro producto, restarts. XML exacto descargado, sin skip ni fallos.
6. Congelar rutas exactas y emitir READY **en commit documental separado después de guardia documental**. Respetar `FARO-UNIT` y preflight por ruta antes de B6. Si no existe la autoridad owner requerida, recortar primera subunidad a su implementación y pruebas, nunca sustituir por callbacks permisivos.

## B5 — Auditoría adversarial: STOP verificable

**B5 RED/PENDIENTE:** los emisores de dos permisos de READ **reales** aún no están identificados y certificados. Los modelos `HouseholdNeedModel` y `CommercialEvidenceOperations` no constituyen ACL, y `readCatalogExact` certifica metadatos, no capacidad. Por ello **el permiso debe seguir `BLOCKED`**. No emitir READY y no correr una CI “positiva” con `{true}` o datos mock. Resolver el origen exacto de autoridad owner antes del primer commit funcional.

Condiciones para convertir B5 en PASS: aportar rutas y código de dos emisores verificables con policy deny-by-default, revisión de invalidación de los permisos por generación y epoch, casos de prueba de grant real/revoke; cerrar rutas exactas en B4. A continuación preflight/READY propio, B6 Android API35 XML verdadero y B7 independiente. El puerto F2C0 no puede convertir consent o callback de test en owner ACL.

**Límites inalterables:** V2 productiva, pagos, movimientos de inventario, Apply, operaciones bancarias, red/APIs pagas, G15 físico, Auto/Noche, main y V4.4 todos OFF/sin modificación. Historial de precios ≠ cotizaciones, Evidence ≠ Reality; Finanzas no recibe movimientos. F3 aceptación integral y S4 global siguen OPEN.

## Checkpoint de este turno

- Antes: B0 Android35 negativo B7 PASS y permiso consumido BLOCKED.
- Ahora: F2C1B1B1 brecha de owner policy delimitada contra código real y B1–B4 redactados; B5 **STOP**, no se afirma implementación ni CI funcional nueva.
- Siguiente acción productiva: localizar/crear los dos puertos de autorización READ en el owner verdadero; completar B5; congelar rutas; CI guard documental; READY independiente; B6–B7 positivo.
