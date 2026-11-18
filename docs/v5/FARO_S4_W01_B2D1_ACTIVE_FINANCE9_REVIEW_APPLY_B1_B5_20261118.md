# FARO W01 B2D1 · Review canónica a Apply en Finance9 DEV

## B1

Base de código a0913a63c2c7123322d323e9b0a1cabe93eaab23. El cierre B2D0 de este checkpoint acredita grant SQL ACTIVE9, 44318145242 5/5. B1 `S4W01B1FinanceReviewHost` y B2A `S4W01B2FinanceApprovedScopeReader` ya preparan y leen la Review F02/F03. El flujo `S4W01B2C0FinanceDevApplyHost` sólo usa autorización simulada. Falta unir la Review canónica, el grant ACTIVE9 y el executor en el host DEV con una sola Reality. El criterio es un commit de Workspace + Reality con receipt real tras petición explícita, y denegación activa, PENDING y cambio de scope sin efecto. El caller Android se conecta por separado.

## B2

P02 L04/L17 y P03 R04/R17 conservan revisión y consistencia de cambios reales, recibos y retry idempotente. Reutilizar las propuestas de B1, lectura APPROVED oficial B2A, catálogo de contratos, bindings F02/F03, coordinator, final commit gate y reconciliación comprobados en B2C2. La ruta de V4.4 conserva contexto histórico.

## B3

ADAPTAR la comprobación de foreground de Android y el sandbox UID para el futuro caller; son invariantes de proceso y lifecycle. REFERENCIA Now in Android (Apache-2.0) para consumo de estado de UI separado del repositorio, sin incorporar librerías. USAR el journal/receipt existente y un final gate que conserva el aislamiento SQL. DESCARTAR Apply por estado de UI, emisión previa al CAS APPLYING y un store separado. Fuentes https://developer.android.com/guide/components/activities/activity-lifecycle , https://source.android.com/docs/security/app-sandbox , https://github.com/android/nowinandroid y https://www.sqlite.org/isolation.html .

## B4

Tres rutas nuevas: `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2D1ActiveFinanceApplyHost.kt`, `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2D1ActiveFinanceApplyHostTest.kt` y `.github/workflows/faro-v5-s4-w01-b2d1-active-finance9-review-apply.yml`. El host exige gesto y Review APPROVED exacta de la conversación activa, a la que liga operación, objeto, fingerprint y clave de intención. Releer el envelope tras BeginApply y antes de Reality. Emitir grant en ese punto y consumirlo en el gate final después del receipt. Reconciliación de receipt existente permite resolver UNKNOWN sin llamar a Apply. Usar el fixture de grafo activo de C2C y el catálogo generado. Los disparadores por rutas nuevas son JVM o compilación de Android en esta rama.

## B5

Probar PENDING, falta de gesto, cuenta e ingreso reales, preflight obsoleto y conversación seleccionada distinta. Verificar que devuelve receipt de Reality y COMMITTED de Workspace, sin transacción duplicada. Workspace sólo se consulta fuera de la transacción Reality. El futuro Session será el caller que aporta un fence técnico real. Probar rechazo antes de emitir grant y recobro por receipt solamente. Mantener producto OFF, W2 STOP, sin biometría obligatoria, transacciones bancarias, ADB ni otro grafo.
