# FARO V5 — S4 W01 · ARCH-05/06: contrato UI e implementación/aceptación
**Fecha AR:** 16/11/2026. **Estado:** diseño DOC_ONLY, sin Compose/Android nuevo. **Baseline:** `7a9631541c6952b178a508fdda931d0227a6c3a9`.
**Autoridad de producto:** `FARO_S4_P05_FUNCTIONAL_PRODUCT_SPEC_20261113.md` F02–F06/PF03–PF09, P06 UX, P07 Finanzas único dueño del dinero, P08 W01, `FARO_S4_W01_ARCH04_CATALOG180_CORRECTION_AND_CONTRACTS_20261116.md`. No repetir código/CI de subunidades W01-A/B0/B1/B2A/B2B/B2C0/B2C2 aprobadas acotadamente.

## ARCH-05 / B1 · Superficie y transiciones comprobadas
Actualmente `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt` ya declara `CHAT`, `INICIO`, `FINANZAS`, otros roots y subdestinos; `FaroV5AdaptiveShell.kt` tiene drawer modal/permanente. `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java` es raíz de inyección; `FaroV5Application.java` publica puertos; FINANZAS como destino existente **no prueba pantalla funcional**. Compose module `v5/surface/build.gradle.kts` depende sólo de kernel contracts/runtime, no de Finance owner: **NO introducir dependencia directa surface→Reality/Finance**. UI lee un `FinancePresentationReadPort` tipado inyectado desde app. El presenter define cobertura y campos, no concede Apply.

## B2/B4 · Navegación y estados decididos
**Entrada y módulos:** FARO abre Chat. Menú lateral existente con Finanzas simple, accesos con íconos etiquetados, Herramientas desplegables, sin barra inferior duplicada. En Finanzas:
1. Resumen por cuenta/moneda con saldo **REAL confirmado** y revision; cada cuenta muestra `VERIFICADO`, `OPENING_NO_VERIFICADO`, `PARCIAL`, `NO_AUTORIZADO` o `NO_DISPONIBLE`, nunca cero en estado desconocido. ARS y USD en secciones no sumables.
2. Módulo de acciones: Cuenta, Ingreso, Gasto, Transferencia, Compromiso/Cuota, Presupuesto. Flujo común `BORRADOR→PREVIEW→REVISION→CONFIRMACION_HUMANA→APPLYING→COMMITTED+RECEIPT` o `CANCELLED/DENIED/STALE/UNKNOWN`; `UNKNOWN` muestra consulta de receipt y bloquea otro Apply del mismo intent hasta reconciliación. Doble tap se deshabilita durante petición, pero la garantía dura proviene de idempotencia/SQL, no del botón.
3. Transferencia: preview muestra origen/débito y destino/crédito en un único confirmation; alertar explícitamente si quedaría saldo negativo; moneda distinta → denegar en W01.
4. Cuota: visual diferenciado `comprometido/previsto` vs `pagado REAL`; vencido no implica pagado; importe parcial enlazado a entry real.
5. Presupuesto: tope, consumo REAL, compromisos previstos y disponible ESTIMADO en líneas distintas, fuente y certeza visibles.
6. Gráfico: ventana doce meses **M-6 .. M+5** (6 anteriores + actual +5). Línea 0 siempre visible; segmentos negativos en rojo solo bajo cero; gráfico compacto expandible; información por moneda; mes actual M0 completo y próximos 5 resumidos con drill-down. **W01 define estructura de lectura, estados y componentes reutilizables; el cálculo predictivo `finance.horizon.read` F07, series y gráfico REAL son W08**. Hasta W08, NO dibujar valores fabricados ni anunciar proyección lista; en W01 mostrar sección `PROYECCION_AUN_NO_DISPONIBLE` con etiquetado de cobertura.
7. Accesibilidad: fuente hasta 200%, TalkBack con etiquetas semánticas e importes legibles, alternativa tabla textual para gráfico, rotación, IME y restauración de draft/review/receipt, sin pérdida de estado al volver al Chat.

**Read model** explícito `FinanceUiSnapshot(status, generationId, pointerSequence, sourceRevision, accounts, ledger, plans, budget, projectionAvailability, currencyCoverage)`; `status` one-of `READY, EMPTY_CONFIRMED, PARTIAL, DENIED, UNAVAILABLE, STALE`; importes de estado no conocido deben ser nullable, no 0. `Evidence` no alimenta ledger; `MonthlyPlan` sólo ofrece lectura/coordination y su denegación no autoriza Finance. Las operaciones financieras se preparan en app usando contracts Finance y Review, nunca en Compose sin el host.

## B3 · Investigación/alternativas específicas
A: UI Compose de estado inmutable tipado, presentación desacoplada de Reality y confirmación mediante host Android no-exportado: **ADOPTAR**.
B: UI leyendo SQL directo y botón ejecutando DAO sin Review: **DESCARTAR**.
C: implementar gráficos con valores sintéticos antes de W08: **DESCARTAR**.
D: copiar V4.4 shell/portabilidad de DB: **DESCARTAR**.
P06/P04 ya compararon patrones Actual Budget y UX V4.4 sin importar código. Fuente Android primaria `https://developer.android.com/develop/ui/compose/accessibility` y `https://developer.android.com/identity/sign-in/biometric-auth`; se deberá validar semántica con tests Android API35 y físico G15 autorizado, no dar PASS por diseño.

## ARCH-06 / B4 · Grafo de implementación y permisos independientes
**Unidad M0 (FENCES/SQL9 + backups):** `RealityFinance9Schema.kt`, `FaroV5Finance9StagedMigration.kt`, `FaroV5Finance9BackupCodec.kt`, tests de schema7/8/legacy5/G4/FBS2v1–4, GENERATION/WAL/corte, CI de JVM. Sólo después del B7 arquitectónico y READY exact-path con preflight y writer CAS.
**Unidad M1 (ACL/credencial real):** `RealityFinance9Acl.kt`, `S4W01FinanceLocalPrincipal.kt`, `S4W01FinanceAuthHost.kt` + tests Kotlin dos procesos y Android auth; revoke comparte `BEGIN IMMEDIATE` con efecto y receipt; COMMITTED vs UNKNOWN recuperables. Dev aislado, host shipping sigue OFF.
**Unidad B (F02/F03 host real/UI):** reusar `FinanceManualDraftPreview.kt`, `FinanceManualReviewedProposal.kt`, `S4W01B1FinanceReviewHost.kt` y las suites PASS; montar puertos revisados/grants reales `FinancePresentationReadPort` + `FaroV5FinanceScreen.kt` por app/surface, no duplicar 10/10,12/12,7/7,8/8,7/7,5/5 históricas.
**Unidad C (F04):** `finance.transfer.record` ya canónico N025; crear DTO canonical v1, binding, revalidación, DDL de enlace/2 entries y UI dual effect; error segundo efecto demuestra ROLLBACK único y receipt.
**Unidad D (F05):** `finance.debt.create/payment.record`, `finance.schedule.*`, `finance.plan_item.*` N025/N027; modelo de cuota, revision, M+36, journal, UI, reconciliación MonthlyPlan sin otorgar su permiso.
**Unidad E (F06):** `finance.budget.read/set/clear` N029 con clave por mes/category/currency y read model de certeza/cobertura, sin escribir ledger; UI límite/consumo real y estado parcial.
**Unidad F (integración W01):** Android API35 dev aislado, CI exacta SHA+logs/XML, regresión de catalog180, backups, ACL cross-process, crash/restore/ABA, TalkBack/200%, flujos PF03–PF09; B7 independiente. G15 físico sólo por campaña y autorización específica; no modificar APK habitual.

Cada unidad usa B1 evidencia del HEAD, B2 V4.4+reuso, B3 investigación externa primaria, B4 unificación, B5 negativa, **permiso READY independiente en commit DOC_ONLY**, preflight `scripts/quality/faro-protocol-gate.py` para cada path, B6 código/CI, B7 con XML real + SHA, writer RELEASED; no hereda permisos consumidos. Orden M0→M1→B→C→D→E→F, salvo dependencia explícita demostrada; no empezar W02 hasta completar B7 W01.

## B5 · Casos de aceptación para W01
| Prueba | Resultado esperado |
|---|---|
| PF03 cuenta sin moneda/apertura explícita | UI no crea cuenta ni muestra 0 confirmado |
| PF04 doble tap gasto + crash tras COMMIT | un receipt, un entry, recuperación idempotente |
| PF05 review cancelada o importe cambiado | cero escrituras; fingerprint viejo inválido |
| PF06 transferencia con fallo entre 2 efectos | rollback total, jamás débito unilateral |
| PF07 obligación/deuda fuera M+5 | cuota visible M+7/M+12/M+36 en persistencia y calendario; sin pago ficticio |
| PF08 cuotas vencidas / parcial | `PAID_CONFIRMED` sólo por entry real+receipt, parcial distinto |
| PF09 budget con lectura Finance revocada | PARTIAL/NULL, no $0 falso |
| ACL1 revoke proceso 2 antes/después COMMIT | orden serializable según política y generation fence |
| REST1 7/8/5 backup restore crash/ABA | source intacto, nueva generación/epoch, grants inválidos |
| UX1 TalkBack/200%/rotación/IME | focos y resúmenes accesibles, estado conservado |
| W08 boundary | sin cálculo predictivo ni gráfica ficticia |
Todos los anteriores **NOT_RUN de W01 final**; repetir suites pasadas sólo si las modifica una nueva ruta funcional. No certificar app ni activar V2 productiva por documentación.

## Condición binaria de ARCH-06
El plan está definido en pasos, owners y rutas candidatas y negativos. B7 global de arquitectura requiere comprobar la compatibilidad con ARCH-01/03, catálogo ARCH-04 y fuente de identidad ARCH-02; hasta entonces `W01_ARCHITECTURE_OPEN`. W02 STOP.
