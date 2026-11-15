# FARO V5 — S4 W00-C · Host multi-owner: denegación comprobable antes de Apply
**Fecha:** 2026-11-15 AR. **Unidad:** `S4_W00_C_COMPOSITE_HOST_FAIL_CLOSED_TEST`.
**Origen HEAD:** `6017e77b45c0ccd5633c863572bdb02934232ad6`. Writer global epoch 919 DOC_ONLY; S4 formal ON exclusivamente W00; permiso W00-B BLOCKED. Esta preparación NO autoriza cambios productivos, owner Household ni W01–W11.

## B1 — Evidencia y problema preciso
- W00-A: SQLite real 5/5, sin receipt. W00-B: Apply+receipt+recovery SQLite real 5/5, pero Objective binding TEST_ONLY, no autorización real de host. B7 `docs/v5/FARO_S4_W00_B_B7_PASS_SCOPED_20261115.md`, código `307ee6c851fefb33a215a40c345cd7c7de6bd7f0`, CI 43766359680 SUCCESS, guard 43766849753 SUCCESS del checkpoint W00-B.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5Planning8ApprovedObjectGate.kt`, blob `2bd779e96cfafc897bf96379bc8d55b5f33169d4`: `approvedScope` rechaza cualquier plan cuyo número de nodos sea distinto de 1. No existe concesión canónica multi-node.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5Planning8ReviewedApplyHost.kt`, blob `b7655b4cf63266050100dcb08a69ee450271d948`: host dormant, no montado; su `nodeScope`, `authorization` y preview utilizan `singleOrNull`. Solo admite Reminder CREATE, MonthlyPlan ITEM_ADD o Undo en solicitud separada.
- `FaroV5Planning8ObjectGrantOwner.kt` blob `5301094c14dabdca7a024dc5bd6b505fe146f746` y `FaroV5Planning8ReviewGrantLeaseBridge.kt` blob `5fa2f461455bbf312a7240d0d1346be95530b340`: lease en memoria para UN objeto, no es autoridad multi-owner productiva ni garantía cross-process.
- W00-C debe probar que el host EXISTENTE rechaza un ApplyPlan de DOS owners reales existentes (Reminder + MonthlyPlan) con Review humano APROBADO, ANTES de formar intento o receipt. Probar rechazo no equivale a implementar admisión dual.
- Rutas NUEVAS a confirmar antes de B6: `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W00CCompositeHostDenialTest.kt` y `.github/workflows/faro-v5-s4-w00-c-host-denial-directed.yml`. Solo JVM test y CI, sin código producción.

## B2 — Reuso y límites del legado
Reusar `FaroV5Planning8ReviewedApplyHostT2Test.kt` como patrón de Workspace4 real + Reality8 real + Review canónico; no duplicar sus pruebas single-owner. W00-A/B mantienen su PASS scoped. V4.4 G0D3 mostró el peligro de rutas de pago no atómicas; no copiar runtime. Persistir un Apply de dos owners requiere aparte host/grants independientes, proceso único o fencing demostrado, recibo único y W06 con Household+Finance. Ninguna prueba de W00-C habilita pagos.

## B3 — Alternativas y fuentes externas
1. **A elegida:** test negativo dirigido de contrato host real ya existente con plan de Reminder+MonthlyPlan, aprobado, sin concesión dual; si se deniega, comprobar ausencia de journal/receipt y de ambas mutaciones. Es pequeño y no permite éxito artificial.
2. **B rechazada ahora:** ampliar host para aceptar planes múltiples sin modelo de scopes/lease por authority y sin prueba de revocación/Workspace/Reality; podría producir falso aprobado.
3. **C descartada:** dos Apply secuenciales, otra DB o prueba mock en lugar de host real.
Fuentes primarias revisadas: SQLite https://www.sqlite.org/lang_transaction.html (BEGIN/COMMIT, incertidumbre por error); https://www.sqlite.org/isolation.html (escritor serial); AndroidX https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase (ciclo transaccional). Son REFERENCIAS técnicas, no certificación de permisos ni autorización multi-owner.

## B4 — Unidad y aceptación binaria
**ID exacto** `S4_W00_C_COMPOSITE_HOST_FAIL_CLOSED_TEST`.
Solo dos archivos nuevos, un test JVM Kotlin y un workflow dirigido. Fixture aislado con Replay Workspace4 + Reality8 y dos descriptores productivos existentes: `ReminderOperations.CREATE` y `MonthlyPlanOperations.ITEM_ADD`, ambos catalogados dentro del test y ejecutores productivos reales disponibles en Reality8. El host ejecutado es REAL (`FaroV5Planning8ReviewedApplyHost`), no implementación ficticia.
- W00C-T01: plan aprobado de dos nodos Reminder → MonthlyPlan: denegación sin PREPARED, sin receipt y sin escrituras.
- W00C-T02: plan aprobado en orden inverso MonthlyPlan → Reminder: misma denegación y cero efectos.
- Ambos se prueban sobre SQLite real del host, no pantalla Android ni puerto productivo expuesto.
- CI: `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W00CCompositeHostDenialTest`, exigir JUnit XML exactamente 2/0/0/0, nombres exactos (se normaliza solo sufijo `()`). Guardia `seven-block-evidence` del SHA de código, SHA por test y B7 independiente. Permiso individual READY se emite SOLO en commit DOC_ONLY con evidencia B1–B5 antes de cualquier código, y se consume tras B7.

## B5 — Auditoría adversarial pre-código
- No provocar falsos PASS de atomicidad: resultado PASS significa solamente **host denies unsupported composite**. El rechazo de plan multiowner no prueba concesión de grants por owner ni integridad final-check-to-COMMIT de un futuro host.
- Aprobación de Review no equivale a ACL; sin dos grants correctos, negar.
- Ningún plan con dos operaciones debe crear PREPARED o receipt en host actual. Si crea efectos, RED_FIX/STOP, no ajustar expectativas.
- No simular ingreso/gasto financiero, no crear Household, no tocar catálogo 180, schema, kernel, app productiva ni S2/S3. Sin APIs pagas ni Android físico.
- Failure por Gradle/XML => RED_FIX de esta unidad; no declarar PASS por compilar. Sin validación de host cross-process/grants para dual-owner, W00 global y W06 siguen pendientes.

## B6/B7 — Puertas
B6 DOC_ONLY de esta preauditoría y permiso separado bajo writer epoch 919; guardar READY solo si guard documental SUCCESS y rutas exactas verificadas; liberar writer antes de CI de código. B7 aún NOT_RUN: ejecutar luego del código, inspeccionar XML exacto, guard/regresión y consumir permiso. Sin evidencia, unidad sigue OPEN. W01-W11 OFF.
