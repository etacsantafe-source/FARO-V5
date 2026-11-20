# FARO V5 — S4 W04 · B1–B5 de W04-A y mapa seguro W04 completo
Fecha local: 2026-11-19 (Argentina). Unidad funcional candidata `S4_W04_A_HOUSEHOLD_NEEDS_MODEL_DEV`.
Baseline HEAD `20646219d53753cbd4129e5e732d4cf1a782387f`. **DOC_ONLY**; este documento NO es permiso READY ni prueba B6/B7.
Autorización humana de esta sesión: **comenzar W4/Household**, ejecutar **primero B1–B5** y preparar el relevo a Work o Chat. La autorización de implementación no permite saltar la guardia ni habilita automáticamente W05, W06, W07, compras/pagos o G15.

## B1 — Evidencia verificada, riesgo y criterios
- HEAD de referencia y puntero revisados; W03 LOCAL DEV A/B/C/D/E/G finalizó B7 en `20646219d...`, G Android API35 1/1 PASS run 44656219736 y guardia 44657826342 SUCCESS. W01/W02/W03 no se repiten.
- Writer consultado RELEASED epoch 1367, permiso vigente `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` BLOCKED/consumido W03-G. Antes de escribir verificar de nuevo CAS/HEAD/CI. S4 formal abierta, V2 productiva/Auto/Noche OFF.
- P08 `docs/v5/FARO_S4_P08_W00_W11_B1_B5_20261115.md` bloque 6 especifica W04 necesidades, precios, promociones y presupuesto; P05 S01/S02 (y S03 separado), P07 decisión opción A: Finance único dueño de dinero, Household dueño comercial. El owner Household estaba aprobado SOLO para diseño; **la presente autorización humana habilita preparar implementación W04 acotada a sus propias guardias**.
- Inventario P01 V22/V23 y P03 R11/R14/R15: SmartCompra V5 carece de host funcional certificado. `v5/settings.gradle.kts` @blob a2b6f7e4294537ff8add383ea8c620796d8d88ee enumera owners finance/objectives/matters/reminders/monthlyplan/profile, **sin `:owner:household`**.
- `v5/spec/generated/contract-baseline.tsv` @blob 5653a9758f682f683a852dcf286211bae71c41d0: catálogo 180 descriptores, **32 owner household**, entre ellos `shopping_need.add/update/remove/complete`, `shopping.snapshot.read`, `purchase_plan.preview/commit`, `shopping.purchase.record`, `stock.receive`. Ningún descriptor es ejecutor habilitado. `commercial.price_history.read` pertenece a commercial-evidence, NO al owner household. Prohibido ampliar catálogo por inercia.
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` @blob fda93518715ba0309e6f7192628296b0e852166f: FINANCE9 ya tiene caminos DEV de Finance; no hay evidencia de persistencia Household activa. `RealityFinance9Schema.kt` @blob f278a26dd16f7f8760aa8ceff93a669bf21e4ea7 tiene VERSION=9 y exige fuente Reality8. No se autoriza migración automática ni segundo SQLite writer.
- Unidad A objetivo binario: **modelo puro JVM tipado + test**, sin persistencia/host/manifest/Apply. Estado actual: NO_IMPLEMENTADO y tests W04 NOT_RUN.

## B2 — Comparación exacta V4.4 / V5 y reutilización
Legado READ_ONLY en `etacsantafe-source/FARO` @commit `bfa0ee71f5b9cd104baaadd71f1e77a61f203939`:
- `app-v4/src/main/kotlin/ar/com/faro/v4/smartcompra/FaroV4SmartCompraGateway.kt` @blob 856943d8a08aef04240d507ae1ed8daa88f4b510: modelos de necesidades, recurrencia, precios observados, promoción, presupuestos y stock. ADAPTAR reglas, NO importar runtime.
- `app-v4/src/main/kotlin/ar/com/faro/v4/smartcompra/FaroV4SmartCompraActions.kt` @blob 36eaae6469de0c28a0e6ddc83d059c0b79ad4cd8: resolución de nombres humanos, propuestas sin Apply automático, evidencia no igual a compra/pago/stock. Conservar interfaz legible; evitar solicitar UUID.
- `app-v4/src/test/kotlin/ar/com/faro/v4/smartcompra/FaroV4SmartCompraPurchasePlanTest.kt` @blob 300b7d49133378d15fa723dde876860f5063da65: presupuesto imposible informa brecha; límites de visitas y revisiones inmutables. Futuras pruebas W04-D nuevas, NO declarar ejecutadas.
- `app-v4/src/main/kotlin/ar/com/faro/v4/FaroV4CompositionRoot.kt` @blob 61f04aa371913e1d66fe3f94e9515af6adf78595, líneas 281–295: producción legacy inyectaba SmartCompraStore real en 11 puertos, pese a defaults InMemory; no repetir diagnóstico falso.
- Diferencias V5: no owner aún, Reality/Workspace/Evidence separados, una sola autoridad de escritura y CAS/receipt por acción; no copiar bases, gateway, dependencia IA o flujos de pago legacy. W04-A usa patrón de `owner:objectives` para tipos inmutables; certificación W02 no equivale a W04.

## B3 — Investigación real y alternativas
Fuentes primarias consultadas 2026-11-19:
1. Grocy `https://github.com/grocy/grocy` (MIT; autohospedado PHP+SQLite) y `https://github.com/grocy/grocy-docs/blob/master/tutorials/food.md`: REFERENCIA para lista, unidades, tiendas e historial de precios; DESCARTAR servidor/backend PHP y copia de código.
2. KitchenOwl `https://github.com/TomBursch/kitchenowl` (AGPL-3.0, Flask+Flutter, sincronización y offline parcial): REFERENCIA UX lista/cocina; DESCARTAR importación, servidor y sincronización, además de riesgos de licencia.
3. Android `https://developer.android.com/training/data-storage/room/testing-db` y `https://developer.android.com/training/data-storage/room/sqlite-room-migration`: ADAPTAR disciplina de probar migraciones y DB con pruebas reales; no asumir que W04-A toca Room.
4. Open Food Facts `https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/`: NO ADOPTAR API/dataset en W04 por ODbL, atribución, egress, cobertura/frescura del precio y falta de autorización específica.
Evaluación: A) modelo JVM offline desde tipos V5 e invariantes comerciales **ELEGIDO**; B) copiar V4.4 + backend **RECHAZADO** por contaminación/doble autoridad; C) usar servidor/apps de terceros **RECHAZADO** por costo, privacidad, Internet y migración; D) poner Compras dentro de Finance **RECHAZADO** por decisión P07. Hipótesis falsable: unit A compila y valida entradas sin añadir datos, llamadas ni escritores; W04-B deberá demostrar persistencia aparte.

## B4 — Diseño unificado, división y límites
**W04-A, única candidata para permiso READY después de guard documental independiente**:
- `v5/settings.gradle.kts` — registrar `:owner:household`.
- `v5/owner/household/build.gradle.kts` — módulo `faro.jvm-library`, dependencia contracts y JUnit según patrón de Finance/Objectives.
- `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdNeedModel.kt` — tipos inmutables identificador opaco, nombre humano validado, cantidad positiva escalada y unidad explícita, prioridad, estado y revisión; campos sin divisa ficticia, cuenta Finance, stock ni mutación autónoma.
- `v5/owner/household/src/test/kotlin/ar/com/faro/v5/owner/household/api/HouseholdNeedModelTest.kt` — pruebas unitarias reales con límite/negativos.
- `.github/workflows/faro-v5-s4-w04-a-household-needs.yml` — CI JVM aislada + XML, guardia de protocolo. **Las cinco rutas son propuestas EXACTAS para el primer READY, NO permitidas ahora**.
W04-B futuro: persistencia Household compatible con Reality9, grants/epoch, journal, CAS, receipt y Undo (B1–B5 nuevos en HEAD actualizado); sin abrir DB externa ni migración productiva. W04-C futuro: evidencia comercial manual con fuente, hora, tienda, localidad, vigencia, moneda y precisión; evidencia no es pago ni precio actual garantizado. W04-D futuro: carrito, promociones, límites de visitas, presupuesto sin escritura financiera, invalidar cotización stale; ARS/USD separados. W04-E futuro: Chat/drawer→SmartCompra nativo DEV, Android API35 y fuente 200%, Review/Apply bajo host real. W04-F: auditoría global LOCAL DEV B7. W05/stock/cocina y W06 atomicidad financiera siguen permisos separados, no heredados.
Aceptación A: Gradle compila modelo, tests JVM/XML sin fallos, catálogo 180 intacto, sin Internet, manifest, tablas, cambios Finance ni app productiva; B7 con SHA de código y guardia exactos. No dar PASS por diseño.

## B5 — Auditoría adversarial previa, matriz de STOP
A01 nombre blanco/longitud/controles -> rechazo; A02 cantidad cero, negativa, desborde o unidad vacía -> rechazo; A03 ID inválido/duplicado vs identidad -> rechazo a nivel de repositorio futuro; A04 fecha/revisión inválida -> rechazo; A05 valor/modelo mutado por referencia externa -> inmutabilidad o copia defensiva.
B01 guardar sin grant/foreground/generation -> no escritura; B02 doble submit / retry -> mismo efecto y receipt; B03 crash PREPARED/COMMITTED -> recover por receipt; B04 rollback/restore -> no resucitar stale ni datos cross-generation; B05 migración Reality9 fallida -> rollback e integridad FK/WAL, datos Finance intactos.
C01 precio sin fecha/fuente/localidad -> SIN_DATOS/PARCIAL, no oferta vigente; C02 precio vencido o promo no elegible -> invalidar review; C03 USD+ARS -> no suma nominal; C04 precio observado -> no gasto, deuda, compra confirmada ni stock; C05 cantidad o medio cambia tras fingerprint -> revisión nueva.
D01 presupuesto insuficiente -> brecha honesta; D02 dos productos de nombre ambiguo -> aclarar, no exigir UUID; D03 denegación/419/429/Internet OFF -> flujo manual sigue; D04 Chat+SmartCompra intentan la misma acción -> no duplicar; D05 compra preview -> Finance/stock sin cambios; D06 pagos compuestos antes de W06 B7 real -> STOP.
Pruebas PF18–PF20 / P7T08 / PX04–PX05–PX13–PX17 quedan **DISEÑADAS, NOT_RUN**. A01/A02/A04/A05 pasan a tests A; los otros exigen sus unidades B–E. No hay suite física G15 aprobada.

**B5 decisión:** preauditoría DOCUMENTADA para W04-A, condicionada a guard documental + permiso READY independiente antes de B6. No elevar W04-B–F a READY ni prometer migración/owner activo hasta probarla. Código, CI Android y B7 W04: NOT_RUN.
