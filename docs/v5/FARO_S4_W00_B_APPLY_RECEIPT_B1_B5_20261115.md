# FARO V5 - S4 W00-B. Preauditoria individual B1-B5
Fecha local 2026-11-15; baseline 02d527c5f586fd5fbf4a8460afc60b300583c6f9. Unidad S4_W00_B_REAL_MULTI_OWNER_APPLY_RECEIPT_RECOVERY_TEST.
Autorizacion humana en continuidad inmediata: 'Continuamos', posterior al siguiente paso explicitamente anunciado: "probar el Apply de dos autoridades con recibo y recuperacion mediante una nueva unidad y un permiso individual". Alcance autorizado se interpreta SOLO W00-B de tests e instrumentacion CI, sin runtime, owners, productos ni otros paquetes W01-W11. Writer epoch 916 para documentacion.

## B1 - Inventario y brecha exacta
- HEAD 02d527c5f586fd5fbf4a8460afc60b300583c6f9, writer RELEASED epoch915, guard checkpoint #43765385843 SUCCESS. W00-A B7 PASS scoped, CI #43764890237 XML5/0/0/0; permiso anterior BLOCKED CONSUMED. S2 8/8, S3 5/5 DEV con H07 excepcion no PASS, S4 formal ON DEV bounded.
- ApplyTransactionExecutor.kt blob 772decfde8aee398eda6c84149f9f0a4234d6d7f hace un transactions.run, exige orden topologico, valida ACL antes y despues, llama commits.commit una vez y vuelve RecoveryRequired ante excepcion. ApplyTransactionExecutorTest.kt blob 6f43d3b057598b99f4149956f4b0517f20134bc15d4 cubre mock de dos bindings, no SQLite real (confirmar SHA real antes de B6).
- RealityStore.kt blob be72978b28d79366452a3f43c7018db72b2ca9f7 mantiene un BEGIN IMMEDIATE y recibo+journal dentro del COMMIT; resolveAttemptIdentity crea PREPARED durable, ApplyRetryReceiptGate y ApplyRecoveryResolver reconcilian por recibo.
- RealityStoreFinanceApplyT2Test.kt blob 0086827b1427363173a761b8937378529eed61ae prueba Finance + receipt individual, pero no dos owners.
- FinanceAccountCreateBinding.kt blob 963abdbbc2f35a39afcbebad5da54950a51487db ya existe con contrato productivo; Objectives tiene persistence real (ObjectivePersistencePort.kt blob 8393d043cf7f551c8132f5ce48152be44528c201) pero NO binding productivo aprobado.
- DOS rutas nuevas comprobadas ausentes: v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/S4W00BMultiOwnerApplyReceiptRecoveryTest.kt y .github/workflows/faro-v5-s4-w00-b-apply-receipt-directed.yml; ambos test/CI, ningun archivo de produccion.

## B2 - Reutilizacion y V4.4
Reusar tests reales W00-A y helpers/conceptos de RealityStoreFinanceApplyT2Test, W00-A y mocks kernel; NO repetir esos resultados ni importar runtime V4.4. Mantener single Reality SQLite, finance owner autentico y objectives real, recibo idempotente por plan, journal PREPARED/COMMITTED. Dos cambios no atomicos del legado descartados. No crear nuevo owner Household.

## B3 - Investigacion de alternativas externas
ADAPTAR: transaccion single-DB SQLite + receipt en mismo COMMIT; documento oficial https://www.sqlite.org/lang_transaction.html: COMMIT puede dejar transaccion activa en BUSY y algunos errores I/O pueden tener estados ambiguos; se requiere reconcile por receipt y no inventar commit. https://www.sqlite.org/isolation.html: serializacion de escrituras por SQLite.
REFERENCIA: https://www.sqlite.org/wal.html: WAL commit con marca, ATTACH no atomico para grupo de ficheros.
DESCARTAR: segunda DB/ATTACH WAL, dos confirmaciones secuenciales o saga automatica para opcion A, porque puede existir gasto sin compra. DESCARTAR: binding productivo inventado de Objectives, porque solo tiene persistence y no contrato operativo admitido.
Elegir **binding minimo TEST_ONLY para Objectives** en el archivo de test, con contrato sintetico 's4.test.objectives.upsert' inhabilitado para runtime/registro; Finance sigue binding REAL existente. No simular stores; dos owners realmente mutan RealityStore.

## B4 - Primera unidad minima y criterios
- Unico Kotlin test NUEVO v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/S4W00BMultiOwnerApplyReceiptRecoveryTest.kt. Unico workflow CI NUEVO .github/workflows/faro-v5-s4-w00-b-apply-receipt-directed.yml. Ningun archivo de runtime, app, Room, schema, operation catalog, framework o gradle.
- Plan de dos nodos en orden: FinanceAccountCreateBinding de produccion y binding Objectives TEST_ONLY con ObjectivePersistencePort de Reality. Preparacion test-encapsulada, dependencia de segundo sobre primero, fingerprint/idempotencia fijos por caso; ApplyTransactionExecutor REAL, ApplyRetryReceiptGate y ApplyRecoveryResolver REALES.
- W00B-T01 commit de dos owners + UN receipt COMMITTED durable tras reopen + retry receipt-first sin segundo gasto. T02 segundo binding REJECTED revierte Finance, 0 receipt. T03 revocacion simulada entre bindings y recheck final revierte ambos, no receipt. T04 excepcion inyectada luego de store.commit ANTES de SQLite COMMIT devuelve RECOVERING_UNKNOWN, ambos rollback, journal PREPARED y sin receipt; reopen + resolver + revalidacion controlada permite exactamente un commit. T05 commit devuelve receipt con transactionId distinto: total rollback, no receipt, ambos owners sin efecto.
- CI dirigida JVM: :store:reality:test --tests class S4W00BMultiOwnerApplyReceiptRecoveryTest, XML exacto 5/0/0/0 con nombres de casos normalizando SOLO sufijo de JUnit () (leccion W00-A). Guard seven-block-evidence del MISMO SHA. B7 independiente, sin mezclar permiso con codigo.

## B5 - Auditoria adversarial antes de B6
- No otorgar autoridad productiva a synthetic Objectives binding: usarlo solo test fixture no exportable. El test NO demuestra ACL real de host/procesos ni grant/revocation durable. La revocacion es simulada dentro de callback sincronizado.
- Recordar que RECOVERING_UNKNOWN no autoriza reintento ciego: siempre resolver recibo y validar PREPARED, fingerprint y estado antes de ejecutar de nuevo. Aun un PASS de dos owners concretos NO prueba Household+Finance ni libera W06/W07.
- B7 marcar limites: power loss/IOERR/SQLITE_BUSY, ABA de host, cross-process authorization final-check-to-commit, reinicio de dispositivo, read ACL, proveedor pago real, S4 shipping OFF.
- Nunca tocar datos G15, main, V4.4, V2 productiva/Auto/Noche, secretos, API paga, catalogo 180. Si Kotlin no compila o CI falla: STOP, causa concreta B1/B5 y RED_FIX misma unidad. No repetir pruebas W00-A certificadas ni dos fallas ciegas.
