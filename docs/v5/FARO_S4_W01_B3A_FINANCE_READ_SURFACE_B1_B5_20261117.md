# FARO V5 — S4 W01-B3A · Finanzas: pantalla de lectura segura, contrato y UI aislada
Fecha AR 2026-11-17. Unidad candidata `S4_W01_B3A_FINANCE_READ_SURFACE_DEV`. Head auditado `df06051dd71bbe9330111f0acd54c67d36f9351b`, writer CAS epoch1055. ESTADO DOC_ONLY B1–B5, sin permiso READY. W2 STOP.

## B1 — Inventario de HEAD real y límites certificados
- W01 M1C1B1C terminó B7 scoped DEV-STAGED9 código `3f0900d54b70f4f8da03fd2022ecf5c75fc41e9d`, 10/10 JUnit y 22/22 workflows, checkpoint `df06051dd71bbe9330111f0acd54c67d36f9351b`, guard `44074592817` SUCCESS. NO repeat. G15 AndroidKeyStore físico NOT_RUN, V2 shipping OFF.
- Arquitectura `docs/v5/FARO_S4_W01_ARCH05_06_UX_ACCEPTANCE_DECISIONS_20261116.md`: Inicio Chat, raíz FINANZAS ya declarada, gráfico M-6..M+5 se posterga hasta W08; saldo REAL vs unknown, segregación ARS/USD, TalkBack 200%, no mostrar "$0" si fuente DENIED/STALE.
- `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt` ya contiene `FINANZAS` y `FaroV5UnavailableDestination`; `FaroV5AdaptiveShell.kt` mantiene host genérico. No hay `FaroV5FinanceScreen` ni port Finance UI en surface.
- `v5/surface/build.gradle.kts` usa sólo kernel contracts/runtime y Compose Material3, sin Finance owner/Reality. `FaroV5HomeIndicators.kt` y `FaroV5AvisosSurface.kt` implementan lector `Flow<Unit>` de invalidaciones, `produceState`, lectura `Dispatchers.IO`, nulidad DENIED/unavailable. Sus pruebas contract son precedentes.
- `FinanceSnapshot` del owner contiene revision, balances por cuenta, `ledgerMinor`; no prueba opening REAL verificada por sí solo. Fuente Finance9 opening state LEGACY_UNVERIFIED; no derivar automáticamente saldo real en UI. Descripción de fuente debe salir de futuro adapter app autorizado.
- W01-B0/B1/B2A/B2B/B2C0/B2C2 PASS scoped; F02/F03 UI dev y W01-B3/B4, W01-C/D/E/F siguen OPEN. UI nueva NO puede hacer Apply ni simular autenticación.

## B2 — Reuso V4.4 y no repetición
Reutilizar shell Chat/drawer V5, tokens `FaroV5Spacing`, navegación, arquitectura de port/invalidation de Avisos. Conservar lecciones UX V4.4 de P02/P03 sin copiar estado ni DB. Se reutilizan tipos genéricos Kotlin/JUnit y Compose Material3; el owner Finance aportará más tarde un adaptador tipado `FinancePresentationReadPort` en app, sin dependencia surface→Reality/Finance.
NO modificar motor M1C1, catálogo 180, contratos Finance ni FBS2v4; no reejecutar 22 CI previas por inercia.

## B3 — Alternativas y decisión
A **ADOPTAR**: modelo `FaroV5FinanceUiSnapshot` inmutable con estados `READY/EMPTY_CONFIRMED/PARTIAL/DENIED/UNAVAILABLE/STALE`; cuentas con saldo `Long?` solo VERIFIED y fuente revisionada. `FaroV5FinanceReadPort` suspend + Flow invalidaciones; Composable sin escrituras que borra datos anteriores antes de reread, falla cerrado si no hay port; cuenta ARS/USD formateada desde unidades menores explícitas (2 decimales exclusivamente para esas dos monedas), otras monedas solo estado sin cantidad hasta regla de exponente autorizada; no sumas cruzadas. Fuente de presentación futura en app.
B **DESCARTAR**: consultar Reality desde Compose; reutilizar `FinanceSnapshot.ledgerMinor` a ciegas con opening LEGACY_UNVERIFIED como saldo seguro; mostrar 0 por `null`, convertir USD/ARS a tasa implícita, dibujar gráfico sintético o botones Apply.
C **ADAPTAR**: patrones existentes Home/Avisos, pero cuidar `collectLatest` y cancelación al revocar; no conservar un último snapshot en `remember` al cambiar generación.
Primarias ya consultadas en ARCH-05: https://developer.android.com/develop/ui/compose/accessibility; https://developer.android.com/topic/architecture/data-layer/offline-first. No se necesitan API/servicios pagos.

## B4 — Unidad exacta y pruebas propuestas
Tres rutas nuevas únicamente:
1. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5FinanceReadSurface.kt`: read model+typed port+Compose `FaroV5FinanceReadScreen` DORMANT, sin conexión en CompositionRoot/Navigation hasta W01-B3B/host autorizado.
2. `v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5FinanceReadSurfaceContractTest.kt`: 8 JUnit puros, sin datos de usuario.
3. `.github/workflows/faro-v5-s4-w01-b3a-finance-read-surface-directed.yml`: compilar `:surface:compileDebugKotlin` y correr JUnit exact8 + regresión navegación/avisos relevante; XML; comprobar cero product wiring.
No cambiar `FaroV5ShellNavigation.kt`, `AdaptiveShell.kt`, app o manifests. FINANZAS seguirá mostrando pantalla no disponible hasta W01-B3B que monte el port tras ACL/hardware físico.

Matriz de aceptación:
T01 denied/unavailable/stale no muestra saldo ni hace cuenta cero.
T02 EMPTY_CONFIRMED exige revisión verificada y ausencia REAL de cuentas.
T03 READY exige cuentas VERIFIED, revisión/fuente y no duplicados.
T04 opening no verificado, cuenta parcial/denied y saldos nulos se mantienen desconocidos.
T05 monedas ARS/USD separadas; no sumatoria FX y signo negativo distinguible.
T06 invalidaciones y cambio de generación/epoch jamás conservan snapshot anterior; la pantalla usa null durante reread (código/inspección).
T07 unsupported currency no formatea monto con exponente 2 supuesto, texto accesible "No disponible".
T08 proyección MUST ser `NO_DISPONIBLE_HASTA_W08`, ninguna serie inventada; UI solo encabezado/cuentas/estado.

## B5 — Auditoría adversarial antes de READY
- Presencia o fuente incompleta no prueba ledger; solo `VERIFIED` puede portar monto, **incluso 0 verificado es diferente de null**. Cuentas PARCIAL/OPENING_UNVERIFIED deben llevar null y notificar la falta de confirmación.
- `FaroV5FinanceUiSnapshot` READY exige sourceRevision y accounts concretas; EMPTY_CONFIRMED exige sourceRevision y lista vacía; cualquier estado NO AUTORIZADO no puede mantener cuentas ni importes en el modelo. Evitar fuga de datos al revocar y al recomponer Compose; fuente null => UI no disponible.
- No afirmar UI productiva porque Composable nuevo no estará montado. Tests JVM prueban contrato; compilación Android CI es sólo DEV y no certifica TalkBack/200% físico; prueba instrumental y campaña G15 al final.
- Exponentes de moneda: solo ARS/USD tienen formato 2 decimales en esta unidad; otros se renderizan sin importe. No guardar valores de dinero en logs, sin integración IA, sin Internet, sin edición ni Apply, sin habilitar V2 productiva.
- La prueba de no wiring debe verificar que ningún nuevo Compose esté invocado desde `FaroV5CompositionRoot`/shell; la conexión futura requiere B1–B5 B3B y aprobación separada.
- Mantener S2/S3/main/V4.4, Finance9 M1, W2 STOP, Noche/Auto OFF. Si alguna prueba falla, volver a RED_FIX misma unidad, no repetir otras.

## B6
NOT_RUN. No functional code autorizado hasta guard documental y READY exact3 independiente.
## B7
PENDING. Requiere CI SHA exact+XML 8/8, guard, writer RELEASED; PASS scoped UI DEV no equivale a pantalla montada ni fuente real.
