# FARO W01 D1B1B — B7 RED_FIX #1, compilación Android W01
2026-11-17. Unidad `S4_W01_D1B1B_SINGLE_ACTIVE_SESSION_FINANCE_UI_DEV`, commit funcional `b54637060fe7ab7f69840e50275fd62b3c8eab1c`. W1 OPEN, W2 STOP, V2 productiva OFF.

## B1
Permiso EXACT6 READY en `7e49c6cff2c87691922ab8f5a3fde90b3aafaeae`; guard `44197030591` SUCCESS, B6 commit `b54637060fe7ab7f69840e50275fd62b3c8eab1c`; diff 6/6 rutas permitidas, FARO-UNIT correcto. El guard de código `44197704034` SUCCESS.

## B2
La pantalla `FaroV5FinanceReadScreen` ya compila dentro de `:surface`; su consumidor `:app` no tiene dependencia compile directa `androidx.compose.material3`. Evitar ampliar las dependencias de todas las variantes por un envoltorio cosmético.

## B3
Mecanismo más pequeño: eliminar únicamente `MaterialTheme` import y wrapper en la Activity W01; ComposeView y la pantalla Compose siguen siendo existentes. La biblioteca surface conserva su propio MaterialTheme interno. No nuevo package ni migración.

## B4
Cambio de código propuesto **sólo** en `v5/app/src/w01FinanceDev/kotlin/ar/com/faro/v5/app/FaroV5W01FinanceDevActivities.kt`; quitar import `androidx.compose.material3.MaterialTheme` y usar `setContent { FaroV5FinanceReadScreen(financePort) }`. No alterar D1B1A, Session, Chat, guards, Finance9 ni esquema.

## B5
Error reproducido por log real de GitHub Actions `44197704113` job `134443377177`, paso `Run 6 new mapper JVM tests and compile isolated W01 Android` FAILURE, `Kotlin compiler UNRESOLVED_IMPORT: Unresolved reference 'material3'`, archivo W01 línea 12. Preflight parent pasó, SDK instalado y guard passed. El XML 6/6 y la compilación completa **NO están certificados** porque el paso global falló y la comprobación posterior quedó SKIPPED. No declarar PASS ni reensayar D0/D1A/D1B0/D1B1A.

## B6
Código original publicado pero build falló; corrección mínima pendiente, mismo permiso EXACT6, misma unidad.

## B7
**RED_FIX** limitado al import/contendor Compose MaterialTheme de W01. Este dictamen independiente permite un commit de corrección funcional *de la misma unidad y dentro de la misma ruta autorizada* después de registrar el disposition SHA/Unit en `FARO_PROTOCOL_WORK_PERMIT.json`; el próximo workflow debe reejecutar sus seis tests nuevos y la compilación W01, sin repetir certificaciones anteriores. Writer libre antes de CI.
