# FARO V5 · W05-A1 · mapa de unidades posteriores y condiciones (sin READY automático)
Fecha: 2026-11-24/25. Baseline `1f5d75663514b830a3fad4662d9d0202cbc76249`. Documento de continuidad para Work; NO certificación B7 futura, NO reemplaza permiso por unidad.

## C2C1 — Android Evidence2 SQL11 ACTIVE reopen (siguiente tras C2C0 B7)
Reutilizar B1–B5 y código `docs/v5/FARO_S4_W05_A1B3C1C_ANDROID_SQL11_CLOSED_HOST_B1_B5_20261124.md` + `FaroV5Household11ActiveDevReadHost` Evidence1 y `FaroV5W05Household11DevStartup`, sin tocar sus contratos históricos si no hace falta. Nueva autoridad tipada Evidence2 schema2, full-pointer y ticket v2, `SQLiteDatabase.OPEN_READONLY` y WAL, no `immutable=1` ACTIVE; ABI v2CoreDev aislada sin producción.
Rutas candidatas a congelar tras C2C0 PASS:
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2ActiveDevReadHost.kt`
- `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05Household11Evidence2DevStartup.kt`
- `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt` (solo si realmente necesario, auditar diff)
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2ActiveDevReadHostTest.kt`
- `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05Household11Evidence2DevStartupTest.kt`
- `.github/workflows/faro-v5-s4-w05-a1b3c2c1-evidence2-android-reopen.yml`.
B5 mínimo: VALIDATED jamás auto-switch; COMPLETED active close/reopen (incluido WAL y segundo reopen), source/schema2 exactos, pointer ABA, rollback no target, host background revoca, no CREATE, sin motor SQL10/productivo, cero port Apply, 0 leaks, tests JVM/Android por SHA y XML. Antes de B6: verificar C2C0 PASS/guard/HEAD, comparar rutas vivas, completar delta B1–B5 y emitir nuevo READY exacto en commit solo documental. No dar permiso ahora.

## A1C — recepción de inventario DEV con Review/Apply, receipt y Undo
Requerimientos P05 S04 PF21–PF23, P08 W05, A0 preview B7 y A1A schema SQL11 STAGED. La rama actualmente NO prueba `stock.receive` real. No inferirlo de `HouseholdStockReceiptPreview` ni de tabla `household_stock_receipt`.
Secuencia propuesta sin inventar nombres ni pasar de alcance: A1C0 auditar descriptor catálogo180 y grants/authority `stock.receive` (si no registrado o falta autorización: STOP, no mutación de catálogo); A1C1 operación Review/Apply explícita sobre Reality11 DEV, un único WriteCoordinator y SQL CAS de revisión/generación, `intent_key` unique, recibo idempotente y cantidad física parcial; A1C2 crash/UNKNOWN recovery y Undo compensatorio auditado sin borrar evento; A1C3 integración Android DEV de lectura/revisión y negativos; A1C4 B7 global dev con backup/restauración y aceptación de no pago.
Pruebas obligatorias: comprado10 recibido7 → físico+7 pendiente3; repetición mismo intento → un efecto/receipt; recepción3 posterior → 10 sin duplicado; sobre-recepción rechazado; unidades no compatibles STOP; crash PREPARED/COMMITTED UNKNOWN reconciliar por receipt sin retry ciego; rollback y Undo vinculado; reabrir/restaurar/Evidence≠Reality; revoked grant/pointer ABA STOP; stock real productivo OFF. Las pruebas deben usar SQLite real y XML, no mocks que aparenten stock real.
B1–B5 de A1C NO están formalmente terminados mientras C2C1 no tenga B7 y se ignoren rutas/admisión de catálogo. Preparar delta y READY individuales luego de evidencia. No acoplar pagos/Finance; W06 y W07 son posteriores y prohibidos aquí.

## A1 global — puerta de cierre
Exigir C2C0/C2C1/A1C0–A1C4 B7 por alcance y SHA; ejecutar regresión dirigida contra C1 Evidence1, C2 Evidence2, SQL11/5 stores y stock receipt+Undo. Verificar errores, 0 fallos/skip, guard, artefactos, APK aislada y no activación productiva. Si falta la campaña G15, registrar `NOT_RUN`, no decir PASS físico. Solo cerrar `A1 DEV` con alcance preciso, writer RELEASED, permit BLOCKED y pointer durable verificado.