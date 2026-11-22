# FARO V5 · S4 W04-F1 · Coordinador de lectura histórica comercial autorizada · B1–B5
Fecha: 2026-11-22. Unidad S4_W04_F1_AUTHORIZED_HISTORICAL_READ_COORDINATOR_DEV. Estado **PREAUDIT DOCUMENTAL, NO READY**, no B6 ni B7 en este commit. W04-F GLOBAL todavía no está cerrado.

## B1 — Evidencia viva, riesgo, objetivo
Checkpoint validado 4cb9eee1331c80ed933248478b58fa58b9f2f9ca; guardia 45038276522 SUCCESS; writer remoto RELEASED epoch1565, permiso E consumido/BLOCKED. W04-D4 B7 PASS JVM 8/8 en código 5fe51ae3286e92bf8b7575c93e9e77dd0d6afe7f y W04-E B7 PASS Android API35 4/4 en bbcdb021ced4688ede8a41533d22deb90bf2e1ea. **No repetir** ni cambiar sus archivos. D1, D2A, D3A, D3B, D3C y D4 tienen B7 PASS acotados.
Brecha W04-F: el renderer E arranca correctamente vacío, sin host LIVE de Household+Evidence, y la suite de precios tipados opera sobre Evidence2 staged; afirmar un precio actual o un montaje de sesión productiva sería falso. La primera unidad F1 debe enlazar SOLO **la cadena JVM de lectura autorizada histórica**, sin introducir store, launcher, escritura ni interfaz.
El código existente S4W04CommercialCartCoverage.project exige HouseholdNeedsSnapshot y CommercialNeedReadFence + CommercialEvidenceStagedTypedPortabilitySnapshot, grants independientes; D2A estimate requiere elección humana de observación exacta, y D3B recheck exige coherencia de watermark/fingerprint/pointer/clock. Los callbacks son proporcionados por un host de confianza que ya tenga las sesiones autorizadas; NO abrir store en F1. `S4W04HouseholdNeedApplyHost` expone snapshot y lectura de precio comercial schema1, pero no prueba todavía un host ACTIVE de datos tipados.
Objetivo binario: **componer** D1→D2A→D2C0 escenario conservador sin promoción→D2C1→D3A→D3B→D4 con gates de acceso al principio y al final, sin elegir precios automáticamente, sin transacciones, y 8 JUnit5 adversariales. Debe preservar parcialidad, ARS/USD y fuente literal. Sin selección explícita base: STOP, no autocompletar.

## B2 — Comparación y reutilización
P02/P03 L11/L14/L15: UX SmartCompra V4.4 mostraba contexto humano y presupuestos; no importar runtime, ni prometer precio vigente. Reusar exactamente las APIs ya certificadas W04-D0–D4 y el patrón de host-inyección con revocación de W01–W03, SIN clonar modelos ni reabrir sus pruebas. D4 no es autoridad de dinero ni stock y no incluye nombre de producto verificado; E mantiene vista inerte. Nueva unidad solo de coordinación sin nueva fuente/owner. El montaje Android de sesión real, pantalla SmartCompra en shell y aceptación integral quedan como **W04-F2/F3**, condicionados a resolver la fuente tipada ACTIVE autorizada y permisos propios.

## B3 — Alternativas y fuentes externas
- Android Architecture UI state holders (https://developer.android.com/topic/architecture/ui-layer/stateholders), ADAPTAR separación: fuente de datos produce estado, vista lo consume; no hacer renderer abrir SQLite.
- Android Accessibility Views (https://developer.android.com/guide/topics/ui/accessibility/views/principles-views), REFERENCIA, preservado por W04-E; no repetir prueba 200% en F1 JVM.
- OWASP Transaction Authorization (https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html), ADAPTAR ninguna presentación equivale a aprobar compra/pago.
- CWE-367 (https://cwe.mitre.org/data/definitions/367.html), ADAPTAR doble/último recheck de generación, grants y source; NO afirmar inmunidad a carreras si caller no revalida el renderer en foreground.
- Elegir host/callback inyectado frente a abrir otro grafo, copiar Evidence STAGED a ACTIVE sin gate, usar "lowest observed" o mostrar datos sintéticos (DESCARTAR). No librerías externas ni APIs pagas. Diseño sin cambios a manifest, V4.4, catálogo 180, Finance.

## B4 — Contrato y rutas exactas
Permiso funcional futuro solo para estas TRES rutas:
1. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04HistoricalAuthorizedReadCoordinator.kt
2. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04HistoricalAuthorizedReadCoordinatorTest.kt
3. .github/workflows/faro-v5-s4-w04-f1-historical-read-coordinator.yml

Coordinador internal con callbacks `currentHouseholdSnapshot`, `currentFence`, `currentCommercialSnapshot`, `householdAdmission`, `commercialReadAdmission`, `clock`. Los seis deben proceder de host confiable; no guardar instantáneas. Recibe draft D0, ventana asOf/maxAge y selecciones de D2A **elegidas explícitamente por humano**; NO aplica "lowest observed". D1.coverage proyecta y revalida antes/después y D2A verifica vínculo a observación exacta. Con base válida, construir un resumen SIN promo automática: por cada grupo, observedBaseSubtotal del D2A, modeledBase/discount/after en 0, selectedNeedIds exactos, modeledNeedIds vacío, GroupCoverage.PARTIAL, coverage PARTIAL, unmodeled = todas las necesidades del draft, no cero supuesto; D2C1 annotate con `emptyList()` y D3A preview validados. D3B ejecuta lectura real repeat con clock; D4 seals/reconcilia observaciones. Antes de entregar, otra D1 proyecta y compara exacta; valida grants del owner y evidencia. Ante error/throw/revocation/stale/duplicado/source changed/result Rejected => rechazado tipado sin mostrar evidencia.
No aceptar Intents, URLs, salida de IA ni sourceRef como permiso. La salida es D4.Decision.Display únicamente en rama válida y aún es información histórica, no "precio actual". Sin efectos en Finance, stock, Apply, V2 productiva ni acceso a red. La llamada al renderer Android exige revalidación adicional en foreground; F1 NO conecta Activity ni shell.
CI workflow dirigido Java21, SDK37, JVM JUnit5 8 tests, XML exact, guard por SHA; tres rutas solamente.

## B5 — Auditoría adversarial diseñadas, NOT_RUN
T01: una elección explícita de dos comercios observados; D4 histórico exacto sin elegir el menor automáticamente, grupos por moneda conservados.
T02: cero elecciones explícitas => rechazado; nunca autogenerar carrito ni precio.
T03: revocar Household antes de leer => rechazado.
T04: revocar Evidence durante proyección/recheck => rechazado sin display.
T05: cambiar source watermark o observaciones en una lectura posterior => rechazado.
T06: cambiar generación/fence después de una lectura => rechazado.
T07: observar necesidad sin precio comparable => salida parcial únicamente si otra elegida explícitamente es válida; sin convertir faltante en 0.
T08: duplicar/sustituir selección/forjar fingerprint => rechazado; no filtrar IDs en UI.
B7 necesita XML descargado 8/8, cero failure/error/skipped, guardia SUCCESS sobre código SHA, diff exact3, checkpoint, permiso consumido y writer RELEASED. Si rojo, registrar RED_FIX de F1 y corregir solo fallo real.
**STOP de W04-F global:** hasta que F2 conecte fuente Household10 + Evidence tipada ACTIVE autorizada, gestione lifecycle/foreground, y F3 pase Android extremo a extremo con una campaña aislada; F1 PASS solo compone lecturas DEV. No inventar endpoints, comprar, pagar, Apply, G15 físico, red, cambio de main/V4.4 o esquema.