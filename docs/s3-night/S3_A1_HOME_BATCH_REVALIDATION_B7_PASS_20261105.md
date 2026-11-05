# FARO V5 — S3.A1 / HOME_BATCH_REVALIDATION — B7 independiente (05/11/2026)

## B7 AUDITORÍA FINAL INDEPENDIENTE

**Dictamen: PASS_ACOTADO** de `S3_A1_HOME_BATCH_REVALIDATION`, **no** cierre de S3.A1. Código revisado: `2c00fc06100c0856812b1bd5a8cec357e39713f2`; documento de B1–B5 `docs/s3-night/S3_A1_ABCD_HOME_BATCH_B1_B5_20261105.md` (blob `982caacacce156bf02afe9ebf8b9491b86a62ead`). Auditoría B7 separada de la publicación de código; no se modifica V5 funcional en este cierre.

### Evidencia verificada
- El diff de `2c00fc06` afecta **solo** `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5HomeReadCoordinator.kt` y `v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5HomeReadCoordinatorTest.kt`. No toca owners, persistencia, permisos Android, egress, catálogo, V2 productiva o S4.
- El coordinador conserva provisionalmente (owner, sello, conteo) después de validar sello previo/posterior y procedencia, y **revalida cada sello al final del lote**. Un propietario revocado durante la lectura de otro se omite, sin inventar cero ni exponer un conteo de generación anterior. `admittedStamp` conserva propagación de `CancellationException` y niega en otras excepciones.
- Dos pruebas nuevas JUnit: `earlierOwnerRevokedByLaterOwnerIsNotReturnedFromBatch` y `generationChangedAfterOwnerReadButBeforeReturnIsSuppressed`. Verifican revocación tardía y cambio de generación antes de retornar. Son dobles de owners; NO prueban emisiones ACL externas ni multiproceso.
- CI SHA exacto `2c00fc06`: Actions [42094317827](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42094317827) **SUCCESS**, log `S3_SURFACE_XML_PASS 61/61` (incluye regresión de superficie); [42094317877](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42094317877) **SUCCESS**, `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, `G5_REAL_HOST_FOUR_OWNER_XML_PASS 1/1` y `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`; [42094317812](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42094317812) **SUCCESS**, `FARO_PROTOCOL_GATE_PASS` y `FARO_PROTOCOL_PREFLIGHT_PASS`.
- El commit documental posterior `d37f80da` disparó Actions [42096308942](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42096308942): **FAILURE exclusivamente en preflight**, `STOP: previous code change has no B7 disposition`. El gate histórico pasó (34 unidades); este B7 y la actualización separada del permiso corrigen la falta de disposición. **No afirmar CI verde sobre el nuevo HEAD antes de observarla.**

### Matriz final adversarial y límites
- **PASS acotado:** revocación o cambio de generación antes de retorno descarta el conteo del owner afectado; otros owners válidos no se ocultan; cero legítimo se distingue de UNKNOWN; fuentes mal conectadas no se admiten; cancelaciones se propagan. Evidencia JVM, no certificación del sistema.
- **PENDIENTE fuera de esta unidad:** una revocación **después** de retornar requiere invalidación real emitida por propietario; no se prueban emisores ACL independientes, restauración/cross-process, Android físico ni X01–X12.
- **Inicio sigue incompleto:** N032 Avisos tiene binding real; N015 receipt history tiene cursor por transactionId pero **sin timestamp duradero de commit ni federación `history.query`**, por lo que no es Actividad cronológica. N031 es proyección Matter-only sin owner combinado Matter/Reminder/MonthlyPlan ni ACL suficiente. Guardados no tiene fuente canónica completa aprobada; +8 propuestas continúan sin autorizarse, catálogo 172. Mostrar `No disponible`, no conteos supuestos.
- Se conserva S2 CLOSED 8/8; S3.A2 5/5 CLOSED_AUTOMATED_SCOPE, y la campaña Android Moto G15 única queda diferida a S3.A5. V2 productiva OFF, Auto/Noche OFF, S4 OFF.

### Próximo paso y permiso
Cerrar esta unidad de endurecimiento como **B7 PASS_SCOPED** y dejar permiso funcional **BLOCKED** para commits nuevos hasta B1–B5 y emisión READY de la siguiente unidad por rutas concretas. Prioridad: owners reales N015/N031 y admisión ACL, evitando adaptar una fuente incompleta a Inicio. No repetir las suites históricas por rutina. Writer global del piloto ABCD B fue recuperado de forma excepcional con autorización humana y control de HEAD; recuperación trazada en la rama de coordinación, no en código funcional.

**Estado S3.A1:** OPEN, falta integración real de Pendientes / Actividad / Guardados. **S3 formal:** 1/5 cerrado en alcance automatizado (A2), **desarrollo S3 ~40% estimación histórica no recalculada**. Trabajo manual de este cierre: documental, tiempo efectivo NO_REGISTRADO.
