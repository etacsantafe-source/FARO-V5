# FARO W01 D1B1B — B7 RED_FIX #2 — reemplazar adaptación Compose directa por Activity Android nativa
Fecha: 2026-11-17. Unidad `S4_W01_D1B1B_SINGLE_ACTIVE_SESSION_FINANCE_UI_DEV`, commit funcional `fde63d76e1979270dba0fda5cd8ad6ea0a7697f5`, W1 OPEN, W2 STOP, V2 productiva OFF.

## B1
GitHub Actions dirigido `44198727346`, job `134446943498` FAILED con `Kotlin compiler UNRESOLVED_IMPORT` en `FaroV5W01FinanceDevActivities.kt`: `Unresolved reference 'ui'` import `androidx.compose.ui.platform.ComposeView`; target W01 no expone dependencia UI directa. Preflight EXACT6 en parent SUCCESS; guard `44198727339` SUCCESS. Código anterior RED_FIX `b54637060fe7ab7f69840e50275fd62b3c8eab1c` documentado; corregir segunda dependencia del **mismo problema de compilación** sin alterar sesión/finance reader.

## B2
Las clases `FaroV5FinanceSurfacePolicy`, `FaroV5FinanceUiSnapshot`, `FaroV5FinanceAccountLine` se exportan desde módulo surface y la app ya depende de `:surface`. El módulo app NO publica compile-time `androidx.compose.ui` ni `material3` (el surface las declara `implementation`). Para renderizar `FaroV5FinanceReadScreen` dentro de app se necesitaría ampliar Gradle/plugins, ruta fuera de permiso exact6 y cambio compartido multi-variant. El launcher/Authority D1B1B están compilados hasta esta importación y no requieren nuevos módulos.

## B3
Opciones revisadas tras segundo síntoma de la misma hipótesis:
A. Añadir Compose UI/material3 y compiler plugin al `v5/app/build.gradle.kts`: **DESCARTAR** por ruta no autorizada, riesgo multi-variant y dependencia estética.
B. Abrir nuevo graph/Finance9 en UI: **STOP**, viola owner único y stage-only.
C. **ADAPTAR** pantalla nativa `FragmentActivity` W01 actual con `TextView`/Button, puerto `FaroV5FinanceReadPort` y `FaroV5FinanceSurfacePolicy` para status/certeza/moneda. La observación continúa corriendo en el executor existente de app, borrado al onPause, se presenta PARTIAL/UNAVAILABLE sin importe Finance9. No nuevos paquetes ni internet.
Android referencias del B3 original: https://developer.android.com/guide/components/activities/activity-lifecycle y https://developer.android.com/topic/architecture/data-layer; no se repiten D0/D1A/D1B0/D1B1A ni benchmark externo.

## B4
ÚNICAS rutas nuevas a modificar dentro de SAME_UNIT + permiso EXACT6:
- `v5/app/src/w01FinanceDev/kotlin/ar/com/faro/v5/app/FaroV5W01FinanceDevActivities.kt`: sustituir import ComposeView por texto nativo; conservar Activity, one-shot Session, lifecycle y métodos D1B0; publicar lectura en thread UI sólo después de check foreground/session/ticket vigente; limpiar en Pause; invalidación de token monotónico; **no balances confirmados**.
- `.github/workflows/faro-v5-s4-w01-d1b1b-single-session-ui.yml`: adaptar aserción de pantalla W01 de Composable a nativa y mantener preflight EXACT6, 6 mapper JVM, compilación W01.
Tests JUnit mapper sin reejecución histórica de D1B1A; corridas D1B1B nuevas obligatorias por SHA. Próximo resultado binario sólo si compile+XML/guard SUCCESS.

## B5
La salida anterior `fde63d76` conserva permiso B6 en misma unidad pero requiere disposición B7 **RED_FIX** para nuevo functional commit. Auditoría: UI usa datos observados de préstamo cerrado en onPause; Render de READY no se falsifica, moneda no convertida, saldos null, ausencia de fuente muestra UNAVAILABLE, no se abre segundo graph ni BiometricPrompt. Eliminar todas las importaciones Compose directas de Activity W01. El trabajo es delta y corrige **causa de fondo** (dependencias `implementation` transitorias), no un tercer parche de imports.

## B6
Commit `fde63d76e1979270dba0fda5cd8ad6ea0a7697f5` compilación W01 FAILED; rediseño Activity nativa aún NO RUN en este documento.

## B7
**RED_FIX** para el commit `fde63d76e1979270dba0fda5cd8ad6ea0a7697f5`, autorizada corrección de la **misma unidad** y sólo sus rutas exactas tras guardar este dictamen y permiso. No PASS de 6 JVM ni Android W01 hasta nuevo workflow y XML.
