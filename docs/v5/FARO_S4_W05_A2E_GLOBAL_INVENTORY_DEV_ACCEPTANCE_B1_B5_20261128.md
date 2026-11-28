# FARO V5 — W05-A2E · preauditoría B1–B5 · inventario integral DEV
Fecha: 2026-11-28 (Argentina). Unidad candidata `S4_W05_A2E_GLOBAL_INVENTORY_DEV_ACCEPTANCE`.
**Estado: PREAUDITORÍA DOCUMENTAL; GATE/CI/B6/B7 NO EJECUTADOS EN A2E; NO PERMISO READY.**
Repositorio `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`.
Baseline de evidencia: `be4b0534e7083043cd0b3167b1ee5c8825279100`. Writer CAS al inicio: RELEASED epoch 2215; exclusivo documental 2216 para este commit.
No es habilitación de producción ni repetición de A2A/B/C/D.

## B1 — Evidencia real y brecha binaria
- Roadmap autoritativo: `docs/v5/FARO_S4_W05_A2_STOCK_RESERVATIONS_CONSUMPTION_ROADMAP_20261126.md`, blob `b8a08512b815d97f7496b2fc460f92130dbe0f8b`: A2E es B7 de inventario global DEV, con XML originales, APK aislada, recuperación/crash/ABA y la secuencia 10 comprado -> 7 recibido -> 3 pendiente -> reserva -> consumo sin pago.
- A2D CLOSED DEV 5/5: `docs/v5/FARO_S4_W05_A2D2_B7_PASS_20261128.md`, 62/62 XML originales, CI 46118973625 y guard 46118973629 SUCCESS, APK DEV SHA256 `de8aa785ab5320905bd1429ff2a97bbf0dffcc5367d5ceec85eb54b75b1d826a`; guard de checkpoint 46119840277 SUCCESS, writer RELEASED 2215. G15 NOT_RUN.
- A2C CLOSED DEV: `docs/v5/FARO_S4_W05_A2C_GLOBAL_DEV_ACCEPTANCE_B7_PASS_XML91_APK_20261127.md`, 91/91 XML y APK, CI 46045293622; A2B 9/9 B7, `FARO_S4_W05_A2B1C_GLOBAL_B7_PASS_A2B_CLOSED_20261126.md`; A1 global DEV 2/2, CI 45743934153. Conservar resultados como evidencia histórica, sin sumarlos para inventar una CI A2E.
- Se inspeccionaron `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockReadGlobalDevAcceptanceTest.kt` (blob `daeb4c0c1697b934c9876b749ecb1cb952b8e98e`) y workflow A2D2 (blob `0a5120b3ea91813ce328b1e7cfb2d86f0b658a7c`). A2D2 prueba 5 cuadros de estado en FBS2v12, readback/ACL/ABA y suites reales de cutover en la *misma CI*. Sin embargo, el test global de lectura representa COMPLETED mediante `installCompletedFixture`, que escribe journal/fence/ticket de fixture: **no demuestra por sí solo un único camino end-to-end de operación real -> cutover real -> lectura UI**.
- Brecha A2E exacta: integrar en aceptación DEV la continuidad real entre compra/recepción/review, reserva, consumo/Undo, almacenamiento/restore, relanzamiento, lectura de stock/UI y fallos de autorización, sin sustituir el journal real por una ficción de test. B6 pendiente y A2 global ABIERTO hasta B7 A2E.

## B2 — Comparación V4.4/V5, reutilización y NO REPETIR
- Matriz V4.4->V5 `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md` (blob `4b9789004459eed6e95bc4d4f8032ee3b4c9f340`): R12 distingue comprado, pagado, recibido, reservado y consumido; R17 conserva AtomicApply/receipts/Undo y recovery; R19 requiere UX/200% sin transferir smokes físicos de otro producto. V4.4 es solo referencia histórica: no copiar código ni montarlo.
- Reusar A1C4 compra/recepción/Undo y cutover SQL13 real, A2B1C SQL14 reserva, A2C consumo/Undo SQL15, A2D0A/B y D1A/B lectura/Android, A2D2 62 XML. Mantener un solo owner Household, Reality SQL15, cinco stores, writer único, pointer FULL/epoch y ACL por principal. No nuevos stores, roots de Inventario/Cocina, operaciones del catálogo ni reimplementación de FBS2v12.
- Caso diferencial necesario: **el nuevo test A2E debe invocar los puertos reales autorizados de cutover y host**; reutilizar fixtures válidos para sembrar datos, pero no `installCompletedFixture` como prueba del cambio de generación.

## B3 — Investigación de alternativas externas y decisión
Fuentes oficiales revisadas, usadas solo como conceptos y sin nuevas dependencias:
1. SQLite, `https://www.sqlite.org/isolation.html` y `https://www.sqlite.org/wal.html`: snapshot isolation, lectura coherente del WAL ACTIVE; USAR el modelo, no marcar `immutable=1` en ACTIVE.
2. Gradle, `https://docs.gradle.org/current/userguide/java_testing.html`: XML de tests para CI; USAR XML originales por run/SHA, clase+caso único, failures/errors/skips=0; no aceptar sumas de runs anteriores.
3. AndroidX Test, `https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner`: ADAPTAR instrumentación real API35, fuente 200%, sin afirmar prueba G15.
4. Grocy `https://github.com/grocy/grocy` (MIT): REFERENCIA para separación conceptual de stock y consumo. No copiar backend web ni importar dependencias.

Alternativa A: una nueva CI que solo concatena suites A2C+A2D sin caso integrado real: DESCARTAR como prueba completa A2E, aunque es útil como regresión en mismo SHA. Alternativa B: motor/DB/UI nuevos o portar Grocy: DESCARTAR por duplicación, riesgo y costo. Alternativa C: **un test nuevo de orquestación E2E con puertos actuales + workflow dirigido de regresión y Android**: ELEGIR sujeto a B5, permiso y CI. Si puertos no son componibles sin tocar producción, STOP, volver a B1/B5 con delta explícito, no ocultarlo.

## B4 — Diseño mínimo de A2E; matriz de aceptación
Unidad única: `S4_W05_A2E_GLOBAL_INVENTORY_DEV_ACCEPTANCE`, posterior a A2D2 B7.
Rutas **candidatas, NO autorizadas** para B6:
1. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05InventoryGlobalDevAcceptanceTest.kt` — prueba nueva de integración real.
2. `.github/workflows/faro-v5-s4-w05-a2e-global-inventory-acceptance.yml` — CI propia en rama de trabajo, preflight de permiso padre para rutas efectivamente modificadas, Java21, SDK, Gradle JVM + Android API35, XML/APK/artefacto SHA256.

Oráculo por principal+SKU+unidad idénticos (UNIT):
| Operación explícita | Comprado | Recibido neto/físico | Reservado | Consumido neto | Disponible | Pendiente |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Compra manual COMMITTED 10 | 10 | 0 | 0 | 0 | 0 confirmado solo con ledger válido | 10 |
| Recepción COMMITTED 7 | 10 | 7 | 0 | 0 | 7 | 3 |
| Reserva COMMITTED 2 | 10 | 7 | 2 | 0 | 5 | 3 |
| Consumo COMMITTED 1 no reservado | 10 | 6 | 2 | 1 | 4 | 3 |
| Undo consumo COMMITTED 1 | 10 | 7 | 2 | 0 | 5 | 3 |
| Release reserva COMMITTED 2 | 10 | 7 | 0 | 0 | 7 | 3 |

Pruebas exigibles: Review/Confirm/Apply/receipt COMMITTED reales, reintento idempotente, una selección auténtica SQL15 por journal/cutover real, FBS2v12 autenticado cinco stores, reabrir host y comprobar misma provenance/valores e historial, no pago ni preview como trigger, revisión/grant/principal/unidad, ABA y epoch, denial/UNKNOWN/partial WAL/rollback sin datos fantasma; no inventar cero ante DENIED/STALE/UNKNOWN. Regresión same-SHA A1/A2B/A2C/A2D pertinente; XML originales con nombres/casos únicos, APK DEV aislada (package, launchable, sin Internet ni background), Android API35 y fuente 200% si la instrumentación existente es ejecutable.
Si el caso E2E prueba que no es viable usar rutas actuales, detallar error y rutas extra en B5 delta, verificar guardia y nuevo permiso. No cubrir G15 aquí; campaña física se decide aparte.

## B5 — Auditoría adversarial previa, puertas y negativas
- No acreditar A2E por 62/62 A2D2, 91/91 A2C ni fixture `COMPLETED` sintético. A2E debe producir su **propia** CI exacta y auditoría B7.
- Confirmar que compra no es ingreso físico, pagar no aplica, receta preview no consume, reserva no es consumo; CONSUME con `reservationRef != null` exige release previo separado (no consumo directo de reserva).
- Bloquear oversubscription, cantidades negativas, mezcla de SKU/unidad/principal, replay doble, Undo doble, receipt duplicado, timeout/corte antes y después de COMMITTED, journal UNKNOWN, root lease/epoch ABA, pérdida de foreground o ACL, snapshot WAL de generación distinta, disclosure de otro principal. `EMPTY_CONFIRMED` ≠ `UNAVAILABLE`.
- Prohibiciones: sin cambios en main/V4.4/S2, sin activar V2 productiva, stock real, pagos, internet, Auto/Noche ni APIs pagas; G15 `NOT_RUN`.
- **Preflight B6:** solo después de guard documental B1–B5 SUCCESS y commit independiente de permiso `READY` con paths exactos, guardia SUCCESS y `python3 scripts/quality/faro-protocol-gate.py --mode preflight --head HEAD^ --unit S4_W05_A2E_GLOBAL_INVENTORY_DEV_ACCEPTANCE --path ...` para todos los archivos modificados. Writer GLOBAL CAS propio contra HEAD vigente; no mantener writer durante CI.
- **B7:** verificar HEAD y guard de código SUCCESS, CI exacta SUCCESS, XML originales sin omisiones, APK bytes/hash/manifest, regresiones, fuentes tocadas/negativas y G15 NOT_RUN. Luego consumir permiso BLOCKED, checkpoint puntero `A2E CLOSED DEV / W05-A2 CLOSED DEV` solo si evidencia es real; liberar writer CAS y confirmar guardia documental. Si cualquiera falla: `BLOCKED/RED_FIX` con causa exacta, sin PASS inventado.

**Salida de esta publicación:** B1–B5 documentados para revisión; NO declarar aún preauditoría guardada/READY ni B6/B7. Siguiente paso: observar guard de este SHA, resolver hallazgos, emitir permiso separado exact2 solo tras verificación.
