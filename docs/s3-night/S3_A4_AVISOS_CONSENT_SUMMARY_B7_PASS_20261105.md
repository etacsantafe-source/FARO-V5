# FARO V5 — S3.A4 Avisos: resumen local solicitado · B7 auditoría final (05/11/2026)

## B7 AUDITORÍA FINAL INDEPENDIENTE

**Dictamen: PASS_SCOPED** exclusivamente para `S3_A4_AVISOS_CONSENT_SUMMARY` sobre el código `e8ad4f947d473f5ca37bff96787cafc82c300a1a`. **No cierra S3.A4:** el owner federado N015 `history.query` y N031 Matter/Reminder/MonthlyPlan siguen pendientes; no cuenta como integración X01–X12 ni Android físico, ni autoriza V2 productiva, auto-resúmenes o LLM.

### Evidencia de los bloques anteriores y alcance real
- B1–B5 preauditados en `docs/s3-night/S3_A4_AVISOS_CONSENT_SUMMARY_B1_B5_20261105.md`, blob `4225a094008ffd909381fa685dd463738ad08846`, commit documental `727a3254c33f96c52b6e6806b4424e419feaf427`.
- Permiso separado `READY` exacto para **dos rutas** de `v5/surface`, commit `840a11ffe8f4d77a8b68358c4b6e32fa4b182819`; guard [42144899281](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42144899281) SUCCESS. No se reutilizó el permiso previo de Avisos ABA.
- El diff de código `e8ad4f947d473f5ca37bff96787cafc82c300a1a` tiene solamente `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5AvisosSurface.kt` y `v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5AvisosSurfaceContractTest.kt`. Mensaje `FARO-UNIT:S3_A4_AVISOS_CONSENT_SUMMARY`. Sin nuevo contrato, tabla, API, archivo de secretos ni workflow.

### Implementación evaluada
- `FaroV5AvisosSurfacePolicy.summary(page)` produce solo conteos de `REQUIERE_ATENCION` e `INFORMATIVO` de la **página autorizada** y distingue cobertura parcial; jamás cita texto, ID o timestamps de avisos. La selección de avisos sensibles redactados por el owner se preserva.
- El botón «Ver resumen local» existe solo con página presente y requiere toque explícito. `summaryRequested` es estado Compose local `remember(readPort, current, authorizationEpoch)`, no `rememberSaveable`. El epoch aumenta en cada invalidación incluso si las páginas comparan iguales, y el resumen desaparece al invalidar/releer o cambiar el port. No hay auto-resumen, inferencia IA, red, cache durable ni mutación `SEEN`.
- Se añadieron dos tests JUnit dirigidos: combinaciones de prioridad sin filtrar texto/IDs y distinción vacío completo vs parcial. El total de `FaroV5AvisosSurfaceContractTest` pasa de 2 a 4, sin disminuir casos previos.

### CI por SHA exacto — TODOS SUCCESS
1. Protocolo siete bloques [42145119178](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42145119178): **SUCCESS** SHA exacto `e8ad4f947d473f5ca37bff96787cafc82c300a1a`.
2. Superficie [42145119122](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42145119122): **SUCCESS**; logs reales `BUILD SUCCESSFUL`, `S3_IN_CHAT_SEARCH_XML_PASS 7/7` y `S3_SURFACE_XML_PASS 63/63` con 0 failures/errors/skipped. Los dos tests nuevos se incluyen en la suite.
3. Regresión V2 core [42145119257](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42145119257): **SUCCESS**, `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, `G5_REAL_HOST_FOUR_OWNER_XML_PASS 1/1`, `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`, `S3_A1_SCHEMA7_GENERATION_XML_PASS 17/17`, `V2_CORE_M2B_DEV_APP_ISOLATED_PASS 4/4`, `V2_CORE_M1_ISOLATION_PASS 4/4`. Build compiló ambos manifests.

### Límites, decisión y salida
- La prueba demuestra código/contrato UI y build JVM, **no prueba que una persona haya tocado físicamente el botón en Moto G15**, ni privacidad frente a capturas OS, ni cobertura de todas las fuentes. Resumen determinista de una página, **no** resumen con IA ni total histórico global. La UI productiva continúa detrás de las reglas del host existente.
- N015 `history.query` conserva solo `REALITY_ONLY_NOT_FEDERATED`; aún faltan federación cronológica, grants/filtros y binding a Actividad. N031 no tiene tres read owners persistentes con prioridades/due/ACL reales. No reducir «No disponible» a cero ni inventar registros/snooze.
- **A4 sigue IN_PROGRESS.** S2 CLOSED 8/8, S3 A1/A2 2/5 CLOSED_AUTOMATED_SCOPE y 0/5 integradas, A3 Guardados solo 8 candidatas sin aprobación (catálogo 172), A5 X01–X12 y campaña física pendientes. S4, V2 productiva, Noche/Auto OFF, sin APIs pagas ni tocar main/V4.4.
- Después de esta auditoría, volver `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` a **BLOCKED** en el mismo checkpoint documental, `last_code_review.sha=e8ad4f947d473f5ca37bff96787cafc82c300a1a` con referencia exacta a este B7, actualizar `docs/s3-night/RUN_STATE.json` y `docs/v5/FARO_CONTINUITY_POINTER.json`, verificar guard, liberar writer CAS epoch 327. **Ningún B6 nuevo sin B1–B5 y READY nuevos.**
