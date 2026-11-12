# FARO V5 — WORK A5 · Integración offline Review→Apply→Receipt→Actividad (B1–B5)

**Fecha:** 2026-11-12 AR. **Unidad futura**: `S3_A5_OFFLINE_REVIEW_APPLY_ACTIVITY_SINGLE_ROOT`. Preparación documental independiente; no concede permiso de código, no permite Groq y no certifica A5 global.

## B1 — Evidencia y brecha concreta
La matriz `docs/s3-night/S3_A5_7_ACCEPTANCE_MATRIX_20261112.md` muestra X01, X09, X10 B7 PASS **scoped** en tests separados, sin recorrido común real. X03 U01/U02 gateway real implementado en host debug pero SIN respuesta HTTP verificada; usuario posterga Groq. Tests existentes `FaroV5S3A57X09ReviewUiTest.kt` y `FaroV5S3A57X10ApplyReceiptUiTest.kt` (ejecutados y PASS en API35 43126057336 / 43128616165) usan una propuesta canónica sembrada en **test APK**, no modelo remoto. X01 recibo→Actividad probado en otra raíz/UID en Android35 43134563606. No contar esos 3 como flujo común.

Código real a integrar, sin copiar estados entre sandboxes:
- `v5/app/src/debug/kotlin/ar/com/faro/v5/app/FaroV5X03U02IsolatedAiActivity.kt`: host debug con root `faro-s3-x03-u02-isolated-debug-only`, Workspace, Reality, `FaroV5ActiveReviewedApplySession`, `ConversationApplyRequestPort`, `FaroV5SurfaceHost.install` con Review explícito, Apply y Chat. Se cierra/revoca en `onPause`. Actualmente no pasa `activityReadPort` a Surface.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5RealHistoryActivityHostBinding.kt`: proyección canónica real Reality COMMITTED + Workspace USER, requiere puntero genuino y política independiente de lectura por fuente (null=DENY). No inventar puntero/ACL.
- `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt`: navega a Actividad mediante drawer y `FaroV5ActivityScreen(readPort=activityReadPort)`.
- `v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57X10ApplyReceiptUiTest.kt`: fixture existente REAL test-only Apply con receipt COMMITTED, idempotencia/UNKNOWN. Nunca ejecutar androidTest en G15.

**Objetivo acotado:** añadir prueba de recorrido local en la **misma raíz debug aislada**, desde un candidato canónico preparado y almacenado por fixture test-only → Review explícito → aprobación explícita sin mutación → Apply explícito único → recibo canónico COMMITTED → Actividad autorizada explícitamente → lectura del mismo recibo con procedencia. No conectarse a Groq, no repetir suites previas, no prometer flujo AI live.

## B2 — Comparación y reutilización
Reusar los puertos `ConversationReviewPort`, `ConversationApplyRequestPort`, `FaroV5ActivityReadPort`, `FaroV5RealHistoryActivityHostBinding`, `FaroV5LocalInstallReadAuthority` (con evaluación de compatibilidad) y el receipt canónico Reality actual; no añadir otra tabla de recibos ni emplear Evidence como Reality. Código V4.4 se consulta solo READ_ONLY para semántica de Activity y revisión; nunca se copia DB/identidad. No crear un puente entre UID `ar.com.faro.alpha.s2isolated.v5dev` (debug) y `ar.com.faro.g15isolated.v2coredev` / `ar.com.faro.alpha.v2coredev` (DEV). La prueba local de G15 no sustituye este test.

**Punto de compatibilidad bloqueante de arquitectura:** el root debug staged no equivale automáticamente al ActiveGenerationPointer de V2 Core DEV; una instancia inventada de pointer para satisfacer la API de History sería una autoridad falsa. Work debe reutilizar una raíz/puntero realmente certificable, o diseñar una compuerta de lectura local explícitamente *debug-only* con identidad/generación y epochs verificados, sin reclamar autenticación humana productiva. Si no puede demostrarlo con reales SQLite/recibos, registrar NEEDS_PREP y STOP, no mentir sobre integración.

## B3 — Alternativas y evidencia externa
- Android Architecture y single source of truth: https://developer.android.com/topic/architecture — preferir observación desde stores existentes, no duplicar ownership.
- Activity lifecycle: https://developer.android.com/guide/components/activities/activity-lifecycle — el `onPause` debe revocar grants en el mismo hilo antes de cerrar session.
- UI/Compose test: https://developer.android.com/develop/ui/compose/testing y https://developer.android.com/develop/ui/compose/testing/semantics — gesto real en Activity, verificar estado renderizado y ausencia de Apply implícito.
- SQLite WAL: https://www.sqlite.org/wal.html — leer receipts en snapshot coherente y no reclamar commit por UI.
**A (preferida):** añadir seam de Activity DEV/debug **solo** cuando se pueda demostrar real pointer + autorización independiente de las dos fuentes y receipt de la misma generación, y una prueba end-to-end dirigida. **B (aceptable si A carece de pointer):** escenario Android test-only de misma raíz, host original y adapter de lectura explícito revocable con fuente real; estado solo debug B7 scoped. **C rechazada:** simular recibo, poblar Activity a mano, llamar Groq por CI, copiar DB entre UIDs o montar Apply en V2 productiva.

## B4 — Contrato, rutas exactas y aceptación
Rutas máximas candidatas, SIN glob:
1. `v5/app/src/debug/kotlin/ar/com/faro/v5/app/FaroV5X03U02IsolatedAiActivity.kt` — exponer **solo** acción de lectura Actividad por gesto humano explícito, con grant independiente y revocación en pause; no auto grants en startChat, no alteración de gateway.
2. `v5/app/src/debug/kotlin/ar/com/faro/v5/app/FaroV5A5OfflineActivityReceiptBridge.kt` — nuevo seam debug-only de autoridad y revocación, **si la API existente no alcanza**. No inventar datos.
3. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5RealHistoryActivityHostBinding.kt` — preferir SIN modificaciones; permiso de ruta solo para delta causal imprescindible de API de lectura autorizada, jamás bypass del null ACL. Requiere auditoría delta antes de cambiar contrato de autoridad.
4. `v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A5OfflineReviewApplyActivityUiTest.kt` — nuevo test API35 con fixture de `PreparedPlan` **solo en test APK**, sin HTTP, sin Groq live, y REAL `Workspace`/`Reality`; ningún fixture en main ni V2CoreDev.
5. `.github/workflows/faro-v5-s3-a5-offline-review-apply-activity-directed.yml` — CI Android API35 especializada, test 1 o más según casos, Android permission/manifest inspection, hashes, XML JUnit independiente del nombre de workflow, guard por SHA exacto.

**Casos obligatorios (mínimo)**:
- A5-E2E-01 REAL: un `PreparedPlan` canónico test-only en Workspace; abrir Review, aprobar sin cambio Reality; pulsar Apply **una vez**; COMMITTED en Reality y `ExecutionReceipt` canónico; pedir Actividad con gesto **independiente**, ver exactamente un receipt de la MISMA raíz, con origen y sin filtrar datos sensibles.
- A5-E2E-02 idempotencia: doble click / reabrir no duplica receipt ni cambia segunda vez; no insertar un evento de Activity manualmente.
- A5-E2E-03 ACL/lifecycle: Chat normal no autoriza Activity; pause/background revoca read; regreso sin gesto no restaura; nuevo gesto da lectura fresca con mismo receipt; no secretos/UID/IDs privados en Surface.
- A5-E2E-04 independiente: si lectura no autorizada, no reportar `0` ni estado completo: mostrar unavailable/partial; ausencia de proveedor no convierte X03 live en PASS.
- A5-E2E-05 aislamiento: ninguna modificación `ar.com.faro.alpha.v2coredev`, `ar.com.faro.g15isolated.v2coredev`, S2 original ni main; debug package no instalado en G15. CI emulador puede limpiar su propio fixture nuevo y debe verificar raíz S2 preexistente intacta si aplica.

**Aceptación Work**: Android API35 result SUCCESS; XML explícito `tests>=4,failures=0,errors=0,skipped=0`, APK y manifest inspeccionados, no conectividad HTTP, captured root source SHA, guard SUCCESS del mismo SHA, B7 PASS **scoped offline** tras auditoría independiente. Si prueba requiere fixture/autoridad que no puede montarse sin ampliar rutas, STOP NEEDS_PREP en vez de saltar protección. Nunca declarar global X01–X12 PASS o A5 CLOSED por esta unidad.

## B5 — Auditoría adversarial de fallos
- **Fuente y generación**: mismo UID/root y misma generación, no copiar DB entre hosts. No usar timestamp textual para igualar recibos; comparar identity canónica de receipt/transaction/fingerprint con store y Activity.
- **No falsa autenticación**: el principal técnico debug o clave de test no equivale a sesión humana; debug no exporta política productiva. Sin lectura real de dos fuentes autorizadas, no certificación.
- **No salida remota**: CI offline, no SDK/Groq, no token real, no precios o facturación. X03/U02 live sigue OPEN. Fixture no finge respuesta de modelo.
- **Preservar Apply**: no lanzar Apply desde Review consent sin click nuevo ni ante UNKNOWN; reconciliar sin reejecutar. OnPause invalida UI mientras commit admitido se concluye y receipt es verdad.
- **Consumo de permisos**: lector Actividad no crea un grant para Search/Avisos/Apply; revocar en `onPause`, `onDestroy` y al cambiar generación, revalidar epochs.
- **Protocolo**: ningún código si `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` no es READY de esta unidad, `preflight` de cada ruta y guard seven-block-evidence. Writer CAS; permiso separado de IME y solamente después de B7 de IME. Sin S4, auto/noche, main, V4.4, V2 productiva, APK G15 ni AndroidTest en G15.
