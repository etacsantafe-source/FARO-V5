# FARO V5 — S3.A4 Avisos, fence de puntero activo ABA · B7 final (05/11/2026)

## B7 AUDITORÍA FINAL INDEPENDIENTE

**Dictamen:** `PASS_SCOPED` solo para `S3_A4_AVISOS_POINTER_ABA`. No acredita cierre de A4, historial N015 federado, N031 atención, Android físico, catálogo Guardados, ni V2 productiva. Tres archivos funcionales y sus pruebas dirigidas; el resto de S3 sigue abierto. Esta disposición se emite DESPUÉS de observar todas las CI de este SHA.

### Evidencia previa y permiso acotado

- B1–B5 documentados en `docs/s3-night/S3_A4_AVISOS_POINTER_ABA_B1_B5_20261105.md`, blob SHA `dee66b089b6c457561f36ac8d4bfecb40779cefd`, HEAD previo `f416f8809035fa4d7d06434941469b20ee09ec26`.
- Permiso `READY` emitido en commit documental independiente `9ba2980e7b0fe52540ea39577ed7e66c6e1e0835`; preflight real y guard [42125560617](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42125560617) **SUCCESS**. Las únicas rutas autorizadas fueron la fábrica Kotlin de Avisos, la llamada Java en CompositionRoot y la clase de pruebas existente.
- Commit código **exacto** `07f57cc8907f666b15596d2843e9d3943decb472`, mensaje incluye `FARO-UNIT:S3_A4_AVISOS_POINTER_ABA`; diff verificado contiene exclusivamente los tres archivos permitidos, sin workflows ni permisos ni contratos.

### Implementación verificada por diff

`FaroV5ActiveAvisosBindingFactory.createPinned` ahora comprueba readiness, ID de generación y validez actual de la secuencia del puntero. `readNotices()` vuelve a validar después de leer snapshots; si cambia el fence descarta toda la página y devuelve `null` (**no disponible**, jamás falso cero). El único caller productivo `FaroV5CompositionRoot.readActiveAvisosPort()` inyecta la comparación ya existente `graph.pinnedPointer.equals(graph.searchPointer.read())` (con null guard), igual que Inicio. El `create` histórico se conserva solo como interfaz de compatibilidad local, no como ruta productiva. No se reescribieron notices, atención, source Truth, Reality, stores, Review ni Android NotificationManager.

Se ampliaron **dos pruebas negativas dentro de las cuatro pruebas JVM ya existentes**, manteniendo el oráculo XML 4/4: cuando el puntero viejo no coincide no toca el store y deniega, incluso con mismo ID de generación; la lectura legítima con pin válido funciona; al revocar la secuencia durante `readCanonical` el resultado es `null` sin filtrar datos. La cobertura de `SENSITIVE`, prioridad, `SEEN` inalterado y límite 100 continúa.

### CI exacta por SHA y XML inspeccionado (todos SUCCESS)

1. Guard protocolo [42125866548](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42125866548), **SUCCESS**, SHA exacto `07f57cc8`. Preflight desde padre READY y evidencia B1–B5; no confundir con tests funcionales.
2. Home / Avisos dirigido [42125866248](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42125866248), **SUCCESS**, logs/XML exactos: `S3_AVISOS_XML_PASS 4/4`, `S3_HOME_NOTICE_XML_PASS 11/11`, `S3_A1_REALITY_ACTIVITY_XML_PASS 6/6`, `S3_G1_SINGLE_PROCESS_MERGED_MANIFEST_PASS 2`; app compile `BUILD SUCCESSFUL`.
3. APK V2 DEV aislado [42125866265](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42125866265), **SUCCESS**: `M5_ASSEMBLE_V2_CORE_DEV_PASS`, package `ar.com.faro.alpha.v2coredev`, launcher único correcto, sin permisos OS añadidos, firma/package gate `PASS`. **NO** representa una activación V2 ni instalación física.
4. V2 core dirigido [42125866295](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42125866295), **SUCCESS**, XML exacto: `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, `G5_REAL_HOST_FOUR_OWNER_XML_PASS 1/1`, `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`, `S3_A1_SCHEMA7_GENERATION_XML_PASS 17/17`, `V2_CORE_M2B_DEV_APP_ISOLATED_PASS 4/4`, `V2_CORE_M1_ISOLATION_PASS 4/4`.

### Riesgos residuales, prohibiciones y siguiente unidad

Esta unidad cubre **fence de puntero en proceso y pruebas sintéticas/CI JVM**. No constituye prueba física, cross-process, ACL de todas las fuentes o certificación de un ciclo real A→B→A sobre G15. `FaroV5ActiveAvisosBindingFactory.create` mantiene puente de compatibilidad no pinneado; la autoridad productiva debe continuar usando `createPinned`. Si surge otra ruta productiva, deberá pasar auditoría propia. La fuente de Avisos no inventa cronología, no auto-marca `SEEN` ni cede autoridad.

**Próximo trabajo S3.A4:** N015 `history.query` federado real con ACL/filtros y binding a Actividad; N031 owner combinado Matter/Reminder/MonthlyPlan y admisión; resúmenes solo consentidos, sin snooze no contratado. Cada unidad nueva requiere B1–B5 y permiso nuevo independiente; **no** reutilizar `READY` de Avisos.

**Estado global:** S2 `CLOSED 8/8` congelado; S3 A1/A2 `CLOSED_AUTOMATED_SCOPE` (2/5 etapas), A3 `REVIEW_ONLY` y 8 Guardados sin aprobar, A4 `IN_PROGRESS`, A5 pendientes X01–X12 y campaña física única. **0/5 entregas finales integradas**. S4 OFF, V2 productiva OFF, Noche/Auto OFF, catálogo oficial 172, sin pagos/APIs pagas, main/V4.4 intactos.

**Disposición del permiso:** volver `FARO_PROTOCOL_WORK_PERMIT.json` a `BLOCKED` en el mismo checkpoint documental de este B7; `last_code_review` debe citar el SHA exacto de este archivo y su sección `## B7`. Actualizar `RUN_STATE.json` y puntero canónico en el mismo commit, validar guard CI, liberar writer 323. No declarar FULL_S3 ni A4 CLOSED.
