# FARO V5 — S4 W00-D1: preauditoría B1–B5 de matriz de autoridad (implementación limitada)
Fecha local 2026-11-15. Baseline HEAD `237bbc048f2d9089becd73e7596ef06c7a45572c`. Usuario autorizó explícitamente «Autorizo» a W00-D acotado sin Household ni pagos. S4 formal OPEN solo W00, V2 productiva/Auto/Noche OFF. Writer global CAS epoch 925 DOC_ONLY. Unidad funcional `S4_W00_D1_SCOPED_ADMISSION_SHAPE_VALIDATOR`.

## B1 — Evidencia del código vivo y brecha
- AGENTS.md, puntero, permiso BLOCKED y protocolo B1–B7 recuperados; último checkpoint W00-D documental `237bbc048f2d9089becd73e7596ef06c7a45572c`, guard `43769501383` SUCCESS, writer RELEASED epoch924.
- W00-A 5/5 JVM SQLite real y W00-B 5/5 Apply receipt/recovery son PASS scoped; W00-C 2/2 host existente DENY de plan compuesto, B7 SHA `b5c2906654c1a6b88aadfa4997c3324d3395f20b` CI `43768537598` y guard del checkpoint `43769148919` SUCCESS. No repetir.
- W00-D documento matriz: `docs/v5/FARO_S4_W00_D_MULTI_AUTHORITY_HOST_CONTRACT_B1_B5_20261115.md`, blob `587ac323e8ef9c9ac1cc62faa2692fe07ce8d5d6`; origen de esta unidad, NO permiso genérico.
- `FaroV5Planning8ApprovedObjectGate.kt` blob `2bd779e96cfafc897bf96379bc8d55b5f33169d4` y `FaroV5Planning8ReviewedApplyHost.kt` blob `b7655b4cf63266050100dcb08a69ee450271d948` mantienen DENY para `nodes.size!=1`. `FaroV5Planning8ObjectGrantOwner.kt` blob `5301094c14dabdca7a024dc5bd6b505fe146f746` solo sirve para UN objeto in-process; no claims de producción cross-process.
- `ApplyTransactionExecutor.kt` blob `772decfde8aee398eda6c84149f9f0a4234d6d7f` permite varios bindings en UN SQLite scope, pero NO autoriza grants compuestos. `PreparedPlan.kt` calcula fingerprint con DAG inmutable.
- 3 rutas NUEVAS verificadas ausentes en HEAD: `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W00D1AdmissionShape.kt`, `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W00D1AdmissionShapeTest.kt`, `.github/workflows/faro-v5-s4-w00-d1-admission-shape-directed.yml`. El catálogo180/Household no se altera.
- Brecha mínima: no existe verificación tipada, puramente estructural y fail-closed de correspondencia PLAN fingerprint → nodeId→operation→owner→scope→grant epoch/generation. No crear un host que ejecute ni un ACL productivo.

## B2 — Reutilización comparativa V4.4/V5
Conservar kernel V5, fingerprint, SQLite single-writer, receipts/recovery, ACL independiente, separación Evidence/Reality. V4.4 G0D3 tenía mutaciones cruzadas no atómicas: NUNCA trasladar sus rutas de pago ni un permiso genérico. W00-C prueba denegación del host, que permanecerá INTACTO. El nuevo código aporta únicamente **comprobación estructural read-only**; éxito del validador NO prueba derechos de usuario ni libera W00 global/W06/W07.

## B3 — Alternativas, evidencia externa y decisión
A) **ADAPTAR / elegida:** clase Kotlin `internal` no montada de validación de forma de dos nodos, identidad del plan y snapshots de grants por owner; callback de lectura revalidada, sin emisión de permisos/leases, sin acceso a DB. Positiva solo sobre forma de datos, no autoridad.
B) **RECHAZADA:** quitar `singleOrNull` del host Planning8, o convertir APPROVED Review en grant; permitiría elevación de autoridad no probada.
C) **RECHAZADA:** dos Apply secuenciales/duplicar writer, DB o conceder Household por catálogo.
D) **REFERENCIA:** mantener check-to-COMMIT transaccional en futura unidad con fencing comprobado.
Fuentes técnicas: SQLite https://www.sqlite.org/lang_transaction.html, https://www.sqlite.org/isolation.html, https://www.sqlite.org/wal.html; AndroidX https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase. Ninguna dependencia nueva ni código/licencia externa.

## B4 — Alcance, contrato y pruebas exactas
`S4W00D1AdmissionShape` es un validador INERT, no un `ApplyPort`: recibe plan inmutable `PreparedPlanView`, fingerprint revisado, EXACTAMENTE dos `S4W00D1ScopeRequirement`, dos `S4W00D1GrantObservation`, generación y epoch del host esperados, principal identificado desde host (NO autenticado por esta clase), lectores actuales `generation/host/grant`. Exige: mismos IDs y orden topológico del plan, operación y contrato `REALITY_MUTATION+BINDABLE`, dos autoridades distintas y scopes no vacíos, mismo principal y fingerprint por cada grant, epoch/nonce diferentes por scope, generación/host no cambiados; vuelve a consultar los lectores y falla cerrado ante excepción. Retorno booleano es solo **shape check**, nunca poder de Apply.
Tres rutas estrictas para un único commit B6 de código: `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W00D1AdmissionShape.kt`; `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W00D1AdmissionShapeTest.kt`; `.github/workflows/faro-v5-s4-w00-d1-admission-shape-directed.yml`. Test JVM `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W00D1AdmissionShapeTest` con JUnit XML exacto 11/0/0/0, guard del mismo SHA, ninguna UI/Android física. Test directo no modifica SQLite ni Workspace.
Casos de aceptación: T01 forma coherente de dos autoridades (retorna true únicamente como dato); T02 grant ausente; T03 sustitución de objeto; T04 epoch stale; T05 generación stale; T06 lifecycle host stale; T07 nonce duplicado; T08 fingerprint stale; T09 plan de un solo nodo; T10 misma authority repetida; T11 actualización concurrente del grant detectada al consultar dos veces. Un host real NUEVO, con consent/grants y commit atómico, será una unidad posterior SEPARADA.

## B5 — Auditoría adversarial anterior a B6
- No cambiar código viejo de host, grant owner, ACL, spec, SQLite, catálogo180 ni operations: el host continuará denegando planes de dos owners. No permitir que callback `readCurrentGrant` ejecute Apply ni cree grants; el validador no debe llamarse bajo mutex Reality en futuro host.
- Entrada de observación proviene del caller y NO es evidencia de identidad humana. Este check no es apto para proteger final-check-to-SQL-COMMIT, cross-process policy revocation, power loss o permisos productivos. Marcar estos casos como NOT_RUN, no PASS.
- Si falta UNO de los grants, cambia fingerprint, generación, owner, scope, principal, host epoch o nonce => false. No normalizar valores a «válidos por defecto».
- T01 no concede permiso de aplicación; tests T02–T11 son negativas estructurales, no los W00D-T01–T10 de pago/commit del contrato superior. W06 Household+Finance real sigue exigido antes de W07.
- CI roja => RED_FIX solo esta unidad; el permiso READY se consume tras B7. Sin pruebas G15 ni Work, Auto/Noche/secretos/API pagas.

## B6 — Admisión previa y próxima puerta
Publicar este B1–B5 y permiso individual READY, por rutas exactas, en commit exclusivamente DOCS, guard siete bloques SUCCESS y writer RELEASED. Revalidar SHA y permiso padre antes de adquirir writer para B6. No certificar código sin JUnit XML exacto ni guard del SHA.
## B7 — Auditoría obligatoria
Resultado futuro PASS_SCOPED únicamente si la implementación cumple las tres rutas, CI exacta 11/11 PASS, auditoría independiente, permiso consumido BLOCKED, nuevo checkpoint y release/guard posteriores. De lo contrario RED_FIX/STOP. W00 global sigue OPEN.
