# FARO V5 · S3.A4 N033C2B4 REALITY8_TRANSACTION_HOST · B7 RED_FIX + B1–B5 correctivo acotado
Fecha local 2026-11-07 AR. Código de primera implementación `7b13660194eb0f832efff18fae6af5f237d1cffc`. Unidad S3_A4_N033C2B4_REALITY8_TRANSACTION_HOST. ÚNICO permiso posterior: `v5/kernel/apply/src/main/kotlin/ar/com/faro/v5/kernel/apply/ReviewedConversationApplyCoordinator.kt` (cambio lexical de smart cast Kotlin).

## B1 — Evidencia roja exacta
- Preauditoría anterior `docs/s3-night/S3_A4_N033C2B4_REALITY8_TRANSACTION_HOST_B1_B5_20261107.md`, blob `42b33e6884f8d20749077b62593bf2d51c2a1cdc`, docs commit `766fcf1bbd91a90a317f31e2a9ea7b0c03413bf5`, guard #42434586138 SUCCESS.
- Código `7b13660194eb0f832efff18fae6af5f237d1cffc` con FARO-UNIT:S3_A4_N033C2B4_REALITY8_TRANSACTION_HOST, exactamente 5 paths B1–B5. Guard #42434881437 SUCCESS. CI histórica #42434881436 `G4 Reality domain owners directed`, job #128591488642, **FAILED** en `:kernel:apply:compileKotlin` línea 87 de `v5/kernel/apply/src/main/kotlin/ar/com/faro/v5/kernel/apply/ReviewedConversationApplyCoordinator.kt` (error Kotlin `UNSAFE_CALL`): `afterBeginApply.invoke(...)` es referencia nullable dentro de un lambda `runCatching` aunque el `if` externo la comprobó !=null. `RealityStore` sí compiló en esa ejecución; NO hay todavía evidencia de tests end-to-end del nuevo host.
- Otras CI de RealityStore del mismo SHA pueden estar en vuelo y fallar por ese mismo error; no cancelar ni reejecutar manualmente, tratarlas como superseded al publicar RED_FIX y verificar SHA posterior.

## B2 — Reuso sin reescritura
- Mantener `ReviewedConversationApplyCoordinator` antiguo Profile/Finance sin callback (null por defecto) y el nuevo host. Sólo capturar el callback nullable en una `val` local estable antes del `if` y ejecutar `afterBegin.invoke` dentro del `runCatching`, sin cambiar transiciones, reportes o stack.
- V4.4 no participa. NO tocar tests, RealityStore, owner bindings, app host, permisos u otros workflows. No bajar conteos ni ignorar el error de compilador.

## B3 — Fuente externa y verificación falsable
- Kotlin null safety https://kotlinlang.org/docs/null-safety.html y smart casts https://kotlinlang.org/docs/typecasts.html : una propiedad nullable referida desde lambda no recibe el mismo smart cast seguro; ADAPTAR una `val afterBegin = afterBeginApply` local para satisfacer el compilador.
- Java/Kotlin callbacks dentro de `runCatching` deben conservar fail-closed ante excepción: FALSE => markRecovering, null legacy => no callback. No cambiar esta semántica.
- Hipótesis: la CI del nuevo SHA supera `:kernel:apply:compileKotlin`, `:app:testDebugUnitTest` host SQLite6/6, regresión Kernel Apply sin skips/fails, guard siete bloques y otras CI relevantes.

## B4 — Un único delta correctivo autorizado
- Reemplazar SOLO:
  `if (afterBeginApply != null && !runCatching { afterBeginApply.invoke(start.review, observed.identity) }.getOrDefault(false))`
  por una `val afterBegin = afterBeginApply` y `if (afterBegin != null && !runCatching { afterBegin.invoke(start.review, observed.identity) }.getOrDefault(false))`.
- Nada más: ningún nuevo test, ruta o dependencia; exact CI + B7 antes de abrir otra unidad. Mensaje de código `FARO-UNIT:S3_A4_N033C2B4_REALITY8_TRANSACTION_HOST`.

## B5 — Auditoría de alcance
- Es un error de tipos del compilador, NO evidencia de fallo de política/Reality, pero tampoco es PASS de ninguna ejecución de SQL. No marcar A4 cerrado ni V2 activo.
- B5 **READY RED_FIX solamente para el kernel source**; mantener la suite del host y kernel como gate. Sin main/V4.4/S2/S4/Auto/Noche/actividad Android/G15/cloud/catálogo172.
- Writer CAS y revisión previa del permiso obligatoria, release durante CI, tras rojo RED_FIX de nuevo, no fuerza ni permite escritura de componente no autorizado.

## B7 — Resultado hasta este commit
- Dictamen exacto `7b13660194eb0f832efff18fae6af5f237d1cffc`: **RED_FIX** porque `:kernel:apply:compileKotlin` no compiló; `RealityStore` compila, resto de tests de esta unidad NO CERTIFICADOS. No culpar SQLite/owner ni reportar PASS del host.
- Requiere commit de corrección y CI dirigido con XML real + revisión adversarial final. La unidad de host aislado y N033C3 permanecen abiertas.
