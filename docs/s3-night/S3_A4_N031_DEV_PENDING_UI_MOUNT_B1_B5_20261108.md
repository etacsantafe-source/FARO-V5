# FARO V5 · S3.A4 N031 DEV UI de vencimientos guardados — B1–B5
Fecha Argentina 2026-11-08. Base revisada `e2b1b3795f8955ce32ea710cd7a76a40e5da7073`. Unidad `S3_A4_N031_DEV_PENDING_UI_MOUNT`. Writer epoch 584. V2 productiva OFF; solo aplicación DEBUG aislada `ar.com.faro.alpha.v2coredev`; no pruebas G15 todavía.

## B1
- Contrato original A4 `docs/s3-night/S3_A4_CLOSURE_GATE_READ_ONLY_20261108.md`: Avisos y Actividad ya certificados en su propio ámbito. N031 reader real SQLite de tres fuentes tiene B7 8/8 en `9a6f6e46` y N015 DEV Activity M5 APK PASS en `3010ad5f`. No repetir suites sin delta.
- Déficit verificable: el launcher DEV ofrece Chat y «Chat y Actividad», pero ningún botón de Pendientes. `FaroV5N031ThreeOwnerDueHostBinding` no tiene Android caller. Requiere tres grants independientes, gesto explícito foreground RESUMED, Application UID/package verdadero, active pointer ID+sequence y epoch de host, revocación onPause; fuente parcial Reality8.
- **Limitación descubierta en lectura de código**: `FaroV5V2CoreDevAuthority.open()` abre Reality con `RealityStore(...)` por defecto y `FaroV5GenerationStoreGraph.openActiveV2WithSharedGate` usa `planningStage8=false`; lectura planning8 requiere `planningStage8=true` y esquema8 exacto. Por ello, en la generación DEV habitual Reality7, botón debe devolver «No disponible»; NO migrar una base activa a 8, NO inventar datos, NO declarar E2E completo hasta que exista cutover autorizado a un candidato Reality8.
- UI nativa muestra únicamente fecha civil y clase de fuente; jamás título, detalle, importe ni ID opaco en texto. «Sin vencimientos registrados» solo cuando página autorizada y completa en ventana real; NO un total global Pendientes. Si fuente denegada, página parcial, cerrada o inexistente: «No disponible». La pantalla está aislada de shipping.

## B2
- Reutilizar `FaroV5LocalInstallReadAuthority`, `FaroV5AndroidSandboxPrincipal`, `AndroidPlatformSandboxFactsReader`, `FaroV5N031ThreeOwnerDueHostBinding`, una `Session` y un grafo existente; no fabricar un segundo RealityStore, no cambiar esquema, no usar Restore/Apply/Undo para montar lectura.
- Acción «Consultar vencimientos guardados (DEV)» independiente de Chat y Actividad; una solicitud atómica cancela si `onPause` durante bootstrap; grant SOLO desde su click en UI RESUMED, no desde executor ni Compose side effects.
- Render puro probado en JVM, sin claves/IDs, muestra límites, fecha civil y fuente. SQLite fuera del hilo UI. Session owns binding + authority; onPause revoca; captura tardía no muestra datos.

## B3
- Android activity lifecycle https://developer.android.com/guide/components/activities/activity-lifecycle : RESUMED es condición de acción; onPause revoca lectura e invalida UI; al volver se requiere gesto fresco.
- OWASP authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html : deny-by-default y validar derechos en cada read y después de SQL.
- Android App Sandbox https://source.android.com/docs/security/app-sandbox : UID/package solo principal técnico de instalación, no identidad humana.
- SQLite isolation https://www.sqlite.org/isolation.html : snapshots de owners separados no son atómicos globalmente; revisar revisión por fuente, no mostrar total global.
- Alternativa rechazada 1: crear `RealityStore(planningStage8=true)` directamente sobre generación ACTIVE7; puede escribir en base activa sin staging, viola admisión N033C1. Rechazada 2: mostrar 0 pendencias por excepción. Elegida: fallback DENY explícito y no mutar DB; futuro cutover Reality8 solamente en generación STAGED, con protocolo separado.

## B4
- Cambiar exclusivamente `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt`, `FaroV5V2CoreDevAuthority.kt`, agregar `FaroV5V2CoreDevPendingPresentation.kt`, prueba nueva `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevPendingPresentationT2Test.kt`, workflow dirigido `.github/workflows/faro-v5-s3-a4-n031-dev-pending-ui-directed.yml`.
- Botón dedicado y boolean request; bootstrap en executor existente; read grant UI thread RESUMED; nueva renderización nativa `ScrollView`, no Contents/Compose. Lectura SQLite en executor; UI actualiza solo si `session` es misma, lifecycle RESUMED, no disposed, authority sigue válida. Datos se descartan onPause y se cierran fuentes. A diferencia de Activity port, no se conecta global HomeCount.
- CI: unit tests nuevos en variante `testV2CoreDevUnitTest`, y M5 APK existente que valida paquete, firma, único launcher y cero permisos OS. Guardia de protocolo sobre exact FARO-UNIT.

## B5
- Negativos: sin gesto = DENY; gesture Chat o Activity ≠ Pending; pause durante bootstrap = sin grant; pause durante SQL = null/no revelar; pointer A→B→A/epoch = deny; schema7 = DENY sin migrar ni reiniciar; partial window ≠ cero global; display ni IDs ni textos privados; Activity/Chat sin regresión en lógica.
- No modificar `FaroV5HostCertification.V2_ACTIVE_ENABLED`, AndroidManifest, compilación shipping, Home, Apply, Undo, writer S2 o V4.4. Ningún dato sale de la app.
- **Aceptación de esta unidad**: compilación + test de renderizador 6/6 + M5 APK exact SHA + auditoría B7. Sin emulador/phone no certificar E2E físico. La unidad NO constituye cierre productivo A4; registrar dependencia Reality8 active/staged si persiste y no elevar PASS_SCOPED a A4 CLOSED.
- Último código anterior `9a6f6e467c6f25cd5680c7398e81a6898086f3ff` B7 PASS en `docs/s3-night/S3_A4_N031_THREE_OWNER_DUE_B7_PASS_SCOPED_20261108.md`. **READY_TO_IMPLEMENT sujeto al guard y a escritura única CAS**.
