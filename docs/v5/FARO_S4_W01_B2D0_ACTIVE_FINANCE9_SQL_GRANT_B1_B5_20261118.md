# FARO W01 B2D0 · Autorización SQL activa Finance9 para F02/F03

## B1 — Evidencia exacta

Base `f068534e596e7de1fdbe729ea839958d236abe57`, guard 44295619688 SUCCESS, writer anterior RELEASED epoch 1210, permiso D1B1C2D consumido. La sesión Finance9 DEV y su grafo único están certificados por API35 44294254314 y el informe B7 D1B1C2D. En esa base, `RealityStore.kt` blob `0d2892733485f400966b3ae385830ffd1e048c49` habilita el bridge de grants sólo para STAGED9. `RealityFinance9Acl.kt` blob `4301d478827a2c8cd144339f65fd9a8c783963e0` posee las tablas y primitivas monotónicas. Los hosts de prueba usan política simulada o una admisión STAGED con biometría. Falta conectar una autorización SQL F02/F03 a la misma SQLite ACTIVE9. Aprobar esta unidad exige probar un grant duradero vinculado a su receipt real y que una denegación revierta también el ledger.

## B2 — Comparación V4.4 y reutilización

P02 L04 documenta una única fuente financiera y retry idempotente en V4.4; L17 documenta AtomicApply y también rutas legacy no atómicas en G0D3. P03 R04/R17 selecciona reimplementar los flujos en V5 manteniendo las invariantes de intentos y receipts. Se reutilizan el owner Finance, `RealityStore.run/commit`, las tablas SQL y el gate posterior al receipt y previo a COMMIT. Se conserva el contrato STAGED9 y su suite histórica para no abrir el montaje biométrico certificado.

## B3 — Alternativas externas

- USAR la transacción existente SQLite BEGIN IMMEDIATE para publicar época y grant juntos. SQLite serializa escrituras y documenta que BEGIN no anida. https://www.sqlite.org/lang_transaction.html
- DESCARTAR añadir SAVEPOINT en la ruta de emisión inicial. SQLite permite anidar savepoints, pero esta emisión es una única transacción de owner independiente del Apply posterior. Durante Apply, ADAPTAR el rollback exterior de Reality, que ya cubre efecto y receipt. https://www.sqlite.org/lang_savepoint.html
- REFERENCIA Synapse, ticket sobre alcance del ID de transacción y mecanismo de búsqueda del mismo evento por dispositivo y sala. Una clave de retry debe quedar atada a su contexto. Se prueba intento duplicado sin invalidar el grant original y se liga el uso a principal, generación/sequence, operación, objeto, fingerprint y receipt de FARO. No se integra red, dependencia ni servicio. https://github.com/matrix-org/synapse/issues/13064 y https://github.com/matrix-org/synapse/blob/develop/synapse/handlers/message.py

## B4 — Unificación y alcance

Exponer un puerto exclusivamente DEV ACTIVE9. La futura Session suministra principal, puntero y comprobación de admisión viva. La solicitud sólo admite `finance.account.create` o `finance.entry.record`; la autorización devuelta encapsula su referencia SQL y se usa exclusivamente en el gate final de Apply. Una sola transacción de emisión actualiza política e inserta grant usando las primitivas ACL existentes. El consumo reutiliza el SQL posterior al receipt. El workflow histórico D1B1C1 conservará sus pruebas y comprobará dinámicamente el permiso del commit que lo activó; su preflight fijo sobre cinco rutas viejas falla para un cambio compartido legítimo.

Rutas exactas propuestas:
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Acl.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ActiveDevGrantPort.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ActiveDevGrantPortTest.kt`
- `.github/workflows/faro-v5-s4-w01-b2d0-active-finance9-grant.yml`
- `.github/workflows/faro-v5-s4-w01-d1b1c1-finance9-cutover.yml`

## B5 — Auditoría adversarial

Denegar factory en schema7/8 y STAGED9. Denegar otros tipos de operación, autorización caducada, fingerprint ajeno y pérdida de admisión. Un intento duplicado que choque con la unicidad de SQLite debe revertir también la política, de modo que el grant original siga vigente. Verificar receipt y mutación atómicos, consumo único y rollback del ledger y receipt tras pérdida del fence. Una devolución negativa final exige que el caller revierta su transacción, como en el executor existente. El puerto SQL confía en el owner que suministra el fence, y no autentica al usuario ni vincula la Review por sí mismo. UI y confirmación Android quedan para el siguiente delta. Revisados disparadores por rutas: el wildcard Android V1.3 usa otra rama. La nueva prueba es JVM SQLite. No requiere ADB, red de app, otro store, balances supuestos, biometría, nuevo catálogo ni liberación productiva.
