# FARO V5 — W01 B3A · Independent B5 and exact READY for dormant Finance read-only surface
Fecha AR: 2026-11-17. BASE `d7b7ceab9a6feeab4b51c22d4f1498d687498341`, guard B1–B5 `44076134595` COMPLETED/SUCCESS. Unidad `S4_W01_B3A_FINANCE_READ_SURFACE_DEV`.

## B1
Inventario real de `FaroV5ShellNavigation` FINANZAS existente, `FaroV5HomeIndicators`/`AvisosSurface` port + invalidation + Compose, `FinanceSnapshot` no garantiza opening verificado. Preauditoría `docs/v5/FARO_S4_W01_B3A_FINANCE_READ_SURFACE_B1_B5_20261117.md` blob `937cac22cdde8b8ae03eac8c0f40f2a36efbae6e`, exact guard SUCCESS.
## B2
Reusar patrones y estilos de Surface ya certificados; no dependencia directa :surface→:owner:finance ni :surface→:store:reality. No reabrir M1C1B1C B7 10/10 22/22 ni código V4.4. Nada conectado en product Activity. La pantalla es un puerto inerte futuro.
## B3
ADOPTAR data model inmutable, `null` para importe desconocido, `EMPTY_CONFIRMED` distinto de `UNAVAILABLE`, invalidaciones fuertes antes de releer; ADAPTAR Home/Avisos Compose; DESCARTAR fake 0, lectura DB directa, importación FX, proyección hasta W08. SQLite/Android offline first y accesibilidad en doc original.
## B4
Exactamente tres rutas nuevas, sin autorizar otras:
1. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5FinanceReadSurface.kt`
2. `v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5FinanceReadSurfaceContractTest.kt`
3. `.github/workflows/faro-v5-s4-w01-b3a-finance-read-surface-directed.yml`
Se requiere 8/8 JUnit XML exact sobre modelo, render helper y certeza; :surface:compileDebugKotlin; regresión aislada shell/home/avisos pertinente; pantalla NO montada en Shell ni Java CompositionRoot. Sin buttons Apply, saldo real sólo de fuente futura revisada.
## B5
**Auditoría adversarial independiente:** `READY` con cuentas verificadas exige sourceRevision consistente y visible; `EMPTY_CONFIRMED` exige revisión verificada y cero cuentas, no se inventa; `DENIED/UNAVAILABLE/STALE` no transportan cuentas o saldo; `PARTIAL` puede mostrar cuentas verificadas y no verificadas por separado con etiqueta y `null`. ARS/USD explícitamente formato minor de 2 decimales, para otra moneda no inferir escala ni mostrar amount. No totalizar ni convertir ARS/USD; negativo separado visualmente; proyección `NO_DISPONIBLE_HASTA_W08`. La pantalla usa `Flow<Unit>` de invalidaciones, borra estado antes de lectura; Cancelled rethrow; no caching de auth en Compose.
Hay tres límites que impiden B7 shipping: el puerto no está alimentado por Reality autorizada, el host no monta el nuevo composable, y no existe prueba física TalkBack G15. No dar PASS físico/productivo. Permiso solo para desarrollo aislado.

## B6
READY_TO_IMPLEMENT previo guard preflight, no se publicaron rutas funcionales.
## B7
PENDING_FINAL_AUDIT: JUnit exact 8/8, Android compile, CI SHA y guard, writer RELEASED; W2 STOP.
