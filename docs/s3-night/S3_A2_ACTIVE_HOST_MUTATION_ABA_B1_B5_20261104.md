# FARO V5 — S3.A2: host Chat A→B→A · B1–B5 preauditoría dirigida (04/11/2026)

**Unidad:** `S3_A2_ACTIVE_HOST_MUTATION_POINTER_ABA`. **Base inmutable revisada:** `6ba5ed4f0479193cfc00dcc01b440f3c5f86ece6`. **Alcance:** endurecimiento de sesiones del host V5 cuando el puntero durable cambia A→B→A. **DECISIÓN:** B1–B5 revisados para un delta acotado (4 sesiones + composición + prueba JVM + CI exacta). Ningún código publicado con esta aprobación; permiso funcional debe emitirse en OTRO commit y superar preflight por ruta. V2 productiva/S4/Auto/Noche OFF.

## B1 EVIDENCIA
- Evidencia histórica B7 PASS de `S3_A2_G1_ACTIVE_CHAT_READ_POINTER_ABA`: `docs/s3-night/S3_A2_G1_ACTIVE_CHAT_POINTER_ABA_B7_PASS_20261104.md`; CI 41937907083: Chat6/6, Home9/9, Workspace7/7, Bridge5/5, Avisos4/4. Reutilizado; no repetir el arreglo de `: Unit` de JUnit ya cerrado.
- Código inspeccionado: `FaroV5CompositionRoot.java` fija `ActiveGenerationPointer(sequence,generation)` para *reads* de Chat mediante `pinnedChatPointer`. Las sesiones `FaroV5ActiveChatComposerSession.kt`, `FaroV5ActiveChatSelectionSession.kt`, `FaroV5ActiveChatReviewSession.kt` y `FaroV5ActiveReviewedApplySession.kt` validan ID de generación + readiness, pero no comparan secuencia durable. El mismo ID podría volver tras A→B→A sin que una UI reciba un callback.
- `SingleProcessMaterialWriteGate` bloquea carreras con maintenance del MISMO proceso durante transacciones; no prueba identidad durable A→B→A ni exclusión entre procesos. La producción V2 sigue OFF: esto es riesgo latente, NO incidente productivo demostrado.
- `FaroV5V2CoreDevSearchReadAuthority` ya cubre la sesión de Buscar DEV aislada: no duplicarla ni afirmar que certifica el ACL real de Search shipping. S3.A2-G1 sigue OPEN.

## B2 COMPARACIÓN V4.4 Y REUTILIZACIÓN
- V4.4: conservar AtomicApply, receipts, Undo, recovery y autoridad de datos; no transferir código de V4.4 al host ni modificar su repositorio. `docs/s3-night/S3_A2_AUTHORITY_AND_COVERAGE_GATE_20261104.md` ya contiene la comparación de búsqueda global V4.4 vs V5; esta unidad no cambia Search.
- V5: reutilizar exactamente `FileActiveGenerationPointer`, identidad completa ya probada en Chat READ y el material gate ya compartido. No crear epoch paralelo, store ni nuevo writer. El hecho de que la UI mantenga un objeto port no debe crear autoridad renovada.

## B3 INVESTIGACIÓN EXTERNA / DECISIONES
- **USAR/ADAPTAR**: OWASP Authorization Cheat Sheet (https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html) exige denegación por defecto y validar permiso por acceso. Revalidación antes/después de lectura o mutación, nunca confiar en locator/UI.
- **ADAPTAR**: Android Guide to app architecture (https://developer.android.com/topic/architecture) advierte que procesos/UI se destruyen/recrean; no alojar autoridad durable en estados visuales.
- **PRUEBA NEGATIVA REAL:** Element X Android PR #7877, merged 2026-10-07 (https://github.com/element-hq/element-x-android/pull/7877), corrigió cierre de cuenta equivocada cuando otra sesión se revocaba. No es el mismo código, pero valida el riesgo de identidad de sesión equivocada: exigir que una sesión revocada jamás actúe después de otra.
- **DESCARTAR**: `Flow`/listener como único control; booleano de UI; sólo ID de generación; nueva tabla ACL; dependencias externas o APIs pagas. `Room multi-instance invalidation` no es comprobación de identidad durable de sesión y NO certifica ACL.
- **Elección falsable:** si el puntero vuelve al mismo ID con secuencia distinta, Composer/Selection/Review/Apply creados antes deben denegar, sin escritura ni receipt.

## B4 UNIFICACIÓN — DELTA ONLY
Rutas funcionales precisas:
1. `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java`: producir un `BooleanSupplier` único a partir de `pinnedChatPointer.equals(chatPointer.read())`; denegar ante error de lectura. Entregarlo a los cinco puertos, incluida lectura Chat actual.
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveChatComposerSession.kt`
3. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveChatSelectionSession.kt`
4. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveChatReviewSession.kt`
5. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveReviewedApplySession.kt`: predicado adicional opt-in para compatibilidad de constructores y comprobación durante operaciones sin cambiar contratos ni transaction gate.
6. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveHostPointerABATest.kt`: prueba JUnit real con `FileActiveGenerationPointer` A→B→A y rechazo de los puertos viejos.
7. `.github/workflows/faro-v5-s3-home-notice-directed.yml`: ejecutar nueva suite y comprobar JUnit XML exacto. La anterior Chat6/6 queda intacta.
**No tocar** `main`, V4.4, FBS2, schema, firma, manifest, Android físico, datos locales, catálogo 172, V2 productiva ni operaciones OS.

## B5 AUDITORÍA ADVERSARIAL PREVIA Y CONDICIONES
- Revalidar misma identidad durable antes y después de lectura o llamada de owner. Con `SingleProcessMaterialWriteGate` las mutaciones y cambio de generación local comparten admisión; no alegar cobertura cross-process no probada.
- Negativas: A→B→A antes de `select`, `read`, `save`, `review`, `decide`, `executeAfterHumanRequest`; `reality.read()` no cambia tras denegación; recovery/ready falso; ID cambiado; no navegar/mostrar datos de sesión obsoleta. Distinguir error de estado de dato real.
- Revisar error `FileActiveGenerationPointer.read()` y devolver DENIED, no reabrir autoridad por listener tardío. Mantener inicializador default para callers de pruebas existentes; en CompositionRoot shipping siempre conectar supplier real.
- Una comprobación *posterior* no es rollback interproceso. No afirmar corrección de cambios de puntero desde proceso ajeno durante una transacción, sin test/sincronización adicional. Riesgo residual explícito.
- Ejecutar CI con SHA exacto y XML de test, luego B7 de alcance exacto. Si falla el descubrimiento JUnit, corregir el test de la MISMA unidad (no marcar PASS). Si hay mismatch de rutas o regresión, STOP.
- **RESULTADO PREAUDITORÍA:** PLAN_REVIEWED_READY_FOR_BOUNDED_IMPLEMENTATION, sólo para las 7 rutas citadas, sujeto a permiso `READY_TO_IMPLEMENT` emitido en commit documental distinto y al preflight positivo. No acredita S3.A2 G1 ni G2–G5.

## Optimización operativa aplicada al plan
- Reutilizar evidencia PASS por SHA/alcance; documentación por unidad, sin repetir auditorías históricas ni abrir etapa nueva.
- No generar checkpoint por cada lectura; un B1–B5, permiso independiente obligatorio, B6 CI y B7. El guardia se mantiene sin relajación.
- Mientras CI corre, writer RELEASED y lectura útil del siguiente problema. Tiempo operativo medible por evidencia; no confundir número de commits con minutos.

**Estado base:** S2 8/8 CLOSED; S3 ≈40% desarrollado estimado, 0/5 formal; A2 0/5 gates; S4/V2 productiva/Auto/Noche OFF.
