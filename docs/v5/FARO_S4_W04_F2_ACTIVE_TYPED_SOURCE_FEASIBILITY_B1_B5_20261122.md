# FARO V5 — W04-F2 · Factibilidad de fuente tipada ACTIVE y host SmartCompra · B1–B5 documental
**Fecha:** 2026-11-22. **Estado:** `PREAUDIT_DOC_ONLY`, **`BLOCKED_NO_READY`**. **Unidad candidata:** `S4_W04_F2_ACTIVE_TYPED_EVIDENCE_HOST_DEV`. **Baseline:** `1995d00adefd26339b6561a3def693f6b5758ad5`. **No se autoriza código por este documento**.

## B1 — Evidencia actual / brecha binaria

- W04-F1 código `a748c4c13e23f1ad826cda01a7fa4990500cbcce`, CI `45054446092` SUCCESS, 8/8 JUnit5 confirmados en XML artefacto `14766509616`, guardia `45054446084` SUCCESS. B7 F1 documento `FARO_S4_W04_F1_HISTORICAL_READ_COORDINATOR_B7_PASS_20261122.md`, publicado y guardia final `45055109476` SUCCESS. F1 usa callbacks que sólo un host confiable debe proporcionar, NO crea dicho host.
- W04-E interfaz nativa aislada `w04CommercialDev`, 4/4 Android API35 y UI inicial «Sin revisión comercial autorizada», sin apertura de stores ni grants; NO está montada como SmartCompra live en `FaroV5SurfaceHost`. E conserva B7 PASS acotado.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` tiene `openActiveHousehold10DevForTrustedHost`, `openActiveHousehold10DevForApplyHost` con un único root lease, OWNER Household10 ACTIVE y Commercial Evidence antiguo. `openValidatedV2Evidence2StagedDev` y `openValidatedV2Internal` tienen comprobación `!commercialEvidence2StagedDev || !activeGeneration`; **la variante tipada Evidence2 es STAGED-only**, con puertas `!planningStage8`, `!financeStage9`, `!householdActive10Dev`. No es correcto inyectar su snapshot STAGED como si perteneciera al puntero ACTIVE.
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt` tiene `stagedTypedTermsV2` opt-in y esquema2 con `commercial_evidence_quote_terms`. El host activo actual monta `stagedTypedTermsV2=false` para su Evidence; el lector C0B `S4W04CommercialPriceReadHost` usa `authorizedCommercialPriceHistory` del mismo lease, pero entrega filas de esquema antiguo sin términos tipados. Los contratos D1-D4 exigen términos tipados; **NO se ha probado esa conversión sobre datos ACTIVE**.
- Certificaciones C1B2G/H1/H2, C2 y C3 prueban STAGED Evidence2 y portabilidad tipada, no cutover ACTIVE con Household10+Finance9+Auxiliary4. B0D2 probó cutover y recuperación de Household10 con Evidence legacy, no un cutover de Evidence2 tipado. **Criterio de entrada F2**: demostrar cómo obtener snapshots tipados reales del *mismo root ACTIVE* bajo permisos y lease únicos, sin ampliar autoridad por inferencia. Hasta entonces F2 **STOP** y W04-F global **OPEN**.

## B2 — Comparación/reuso y opciones

- V4.4 SmartCompra mostraba necesidades, comparaciones por comercio y contexto humano, sin que ello verificara precios actuales o pagos. Usar únicamente semántica UX P02/P03/P06; no copiar runtime V4.4.
- Reusar el kernel V5 de generación/puntero CAS, archivos FBS2 autenticados, recibos, `MaterialWriteGate`, quiescence, recovery de Household10 B0, portabilidad comercial STAGED y contratos de D0–D4/F1/E. Conservar separación Evidence≠Reality, Household≠Finance; no crear segunda autoridad de dinero ni segundo opener SQLite.
- **Alternativa A (solo parcial):** mantener lectura schema1 ACTIVE y mostrar sin términos `NO_COMPARABLE_OBSERVATIONS`. Preserva seguridad, pero **NO cierra F2 ni global**.
- **Alternativa B candidata:** generación STAGED verificada que reúna Household10 y Evidence tipada schema2; cutover/recovery hacia ACTIVE con rollback y replay-fences y nueva lectura inmutable bajo la sesión activa única. Requiere auditoría de compatibilidad cinco stores, FBS2, generación/ACL, no cambiar código sin permiso propio.
- **Alternativa C rechazada:** abrir Evidence2 STAGED por separado y combinar datos con Household ACTIVE o copiar manualmente la DB por fuera de quiescence; no demuestra misma generación ni identidad y rompe aislamiento. Tampoco cambiar un `if` para permitir `commercialEvidence2StagedDev=true` en ACTIVE sin probar migración; sería un bypass.
- **Alternativa D rechazada:** scraping de Internet, proveedor pago, stock/checkout automático o dos commits entre Household y Finance.

## B3 — Fuentes y decisión técnica

- SQLite oficial WAL https://www.sqlite.org/wal.html y ATTACH https://www.sqlite.org/lang_attach.html: en WAL una transacción con múltiples bases adjuntas **no garantiza atomicidad del conjunto** frente a caída; para cambio de cinco stores preferir protocolo **STAGED + archivo/captura autenticada + publicación de puntero y recuperación demostrada**, nunca asumir magia de ATTACH.
- SQLite official isolation https://www.sqlite.org/isolation.html: lecturas deben observar snapshot consistente por la misma sesión; una fuente STAGED de otro lease no se convierte en fuente LIVE de producción por coincidencia de IDs.
- Android database testing https://developer.android.com/training/data-storage/room/testing-db y migration https://developer.android.com/training/data-storage/room/migrating-db-versions: adaptar pruebas de migraciones de datos y esquema, incluso desde versiones anteriores; la implementación FARO usa stores y journal propios, no introducir Room como segunda authority ni reconstruirla sin diseño.
- Android UI data state https://developer.android.com/topic/architecture/ui-layer/stateholders: pantalla no abre fuentes por sí misma; host entrega estado autorizado y lo revoca al pausar. Mantener tests E 4/4 sin reejecutar si no cambia su código.

## B4 — Diseño y límites (NO congelados para B6)

Dependencias obligatorias en orden lógico:

**F2A — prueba de generación/esquema:** revalidar cómo se construye una generación STAGED Household10 + Evidence2 tipada conservando Workspace/Finance9/Auxiliary4 y sus datos; demostrar archivo FBS2 auténtico, integridad/replay, rollback ante crash, reconciliación de generation/pointer CAS, esquema legacy1→2 sin pérdida ni valores UNKNOWN falsamente enriquecidos. **El código actual no ofrece el opener tipado ACTIVE; la factibilidad de esta combinación es objeto de F2A, no resultado.**

**F2B — host de lectura ACTIVE:** sólo tras F2A PASS real, exponer snapshot tipado de Evidence y Household desde un único lease/generation, con grants independientes por necesidad y producto, `asOf`/watermark y read fence, `D1.project` y F1, no source directo a Activity. Revocación foreground, lease/puntero revalidado antes/después de la lectura y antes de pintar. No abrir segundo graph/store.

**F2C — presentación Android de datos LIVE autorizados:** integrar SmartCompra en shell y destino de W04-E, con decisión humana explícita de producto y observación histórica antes de componer D4; si faltan datos o grants, estado `SIN_DATOS`/`PARCIAL`/`NO_AUTORIZADO`, sin demo. Conservar historial ≠ precio vigente, escenarios WHAT_IF no pagados, ARS/USD separados, fecha fuente, links literales, TalkBack/200%, borrar en pausa. El permiso de Android será separado del esquema.

**F3 — aceptación integral W04:** pruebas dirigidas Android/API35 de NEEDS↔Evidence↔Cart↔Review UI sobre SAME generation, permisos/revocación/restauración; APK DEV y XML exact SHA; solo después valorar campaña física G15 independiente. No inventar datos observados.

La matriz definitiva de rutas/owners y la decisión humana sobre migración de datos ACTIVE **NO ESTÁN APROBADAS/CONGELADAS**. Rutas de auditoría candidatas (SOLO LECTURA hasta nueva autorización): `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`, `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`, componentes de `v5/maintenance` para FBS2/activación, `S4W04HouseholdNeedApplyHost.kt`, `FaroV5SurfaceHost.kt` y `w04CommercialDev`; no conceder READY con una lista incompleta.

**Puerta binaria:** `F2A_READY` sólo si código actual auditado define ruta de migración/restore sin pérdida, exact paths y tests, se decide expresamente habilitar la evolución **DEV aislada** del esquema ACTIVE, y un permiso individual READY supera su guardia. En ausencia de ello, `F2A_BLOCKED_NO_READY`. No habilitar schema2 ACTIVE con importación automática ni en V2 productiva.

## B5 — Auditoría adversarial diseñada, NOT_RUN

- Un esquema1 legacy y un esquema2 typed con misma clave pero distintas condiciones NO se fusionan silenciosamente.
- Corrupto/FOREIGN KEY/orphan quote terms, watermark incorrecto, archivo incompleto, recuperaciones repetidas, crash antes/después del puntero, ABA generación y restore con versión incompatible => FAIL CLOSED, rollback/recovery explícitos.
- Lectura Household ACTIVE + Evidence STAGED en dos leases debe ser rechazada; no segunda apertura paralela ni deadlock contra writer único.
- Cambios de ACL/grant/foco/pointer durante consulta/recheck y dos procesos con revocación => no display ni leak.
- Observaciones vencidas/desconocidas/promoción restringida no certifican cotización vigente; faltante no es $0; reintegro futuro no es abono real, no sumar ARS/USD nominal.
- Selección humana explícita obligatoria: nunca tomar lowestObserved como opción automáticamente; texto hostil sourceRef permanece literal; 200% fuente, TalkBack, navegación back/rotación no reviven estado stale.
- Compra, recepción, pago, cuotas y stock separados; W06/W07 no se abren por herencia. Sin Apply, Finance, OS delivery, V2 productiva, Groq, Internet/egress, G15 físico ni cambios en main/V4.4.

## Resultado documental de este turno

**F1 B7 PASS** conservado. **F2 B1–B5 como preauditoría de factibilidad/contratos, NO implementación ni permiso READY**; T/F2 pruebas NOT_RUN, Android F2 NOT_RUN, global W04-F OPEN. No código, APK, BD real ni archivos personales tocados. Siguiente: autorización específica de alcance DEV y fragmentación exacta de F2A, luego B6–B7 por unidad sólo con evidencia real.
