# FARO V5 — G5 prueba integrada de host real B7 PASS — 05/11/2026

## B7 AUDITORÍA FINAL INDEPENDIENTE
**Unidad:** `S3_A2_G5_REAL_ACTIVE_OWNER_DIRECTED`. **Dictamen:** `DONE_VERIFIED_AUTOMATED_JVM_ONLY`, **NO** G5 físico ni S3.A2 CLOSED. SHA exacto de código `5fc626303cb53106e85b0a4a8176b3ef59dce920`.

### B1–B5 y causa de RED_FIX
- Base `docs/s3-night/S3_A2_G5_REAL_ACTIVE_FOUR_OWNER_B1_B5_20261105.md` con alcance test-only, revisión de V4.4/V5, alternativas, transacción owner y seguridad.
- Primer commit `d1a9c68bab7d99e93c1aae60c022a40c4955848f` [CI #42061296590](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42061296590) FAILURE antes de tests por `StorageGenerationId` importado de paquete incorrecto. B7 `RED_FIX` y nueva auditoría exacta en `docs/s3-night/S3_A2_G5_ACTIVE_OWNER_COMPILE_REDFIX_20261105.md`.
- Corrección de una sola línea `5fc626303cb53106e85b0a4a8176b3ef59dce920`: usar `ar.com.faro.v5.maintenance.StorageGenerationId`. Guard obligatorio código [#42062273760](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42062273760) SUCCESS. No se alteró código shipping, V2 productiva, G4 owners, kernels ni stores.

### B6 CI exacta (verificada en log ejecutado)
- [V2 Core run #42062273554](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42062273554) `status=completed, conclusion=success`, SHA `5fc626303cb53106e85b0a4a8176b3ef59dce920`. Job `v2-core-isolation` SUCCESS.
- `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10` histórico conservado.
- `G5_REAL_HOST_FOUR_OWNER_XML_PASS 1/1` **nuevo**, verificado en XML de nueva clase en el mismo Gradle invocation, no sustituye los 10.
- `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`, `V2_CORE_M2B_DEV_APP_ISOLATED_PASS 4/4` y `V2_CORE_M1_ISOLATION_PASS 4/4`.
- El nuevo test crea generación de cinco stores y verifica journal/puntero mediante host DEV, cierra, siembra SOLO en directorio temporal JVM a través de `RealityStore.run` y puertos oficiales Finance, Objective y Matter; reabre host V2, busca tres namespaces, navega a detalle read-only sin monto ni IDs, revalida ausencia de resultados, rechaza resultado viejo tras cierre y recupera los tres al reabrir. NINGÚN SQLite de usuario ni dispositivo fue alterado.

### Límites B7 que no se deben borrar
- La prueba es **JVM con host real y @TempDir**. No es emulador Android, ni ADB físico, ni positivo en Moto G15 para los tres propietarios.
- El Motorola ya demostró apertura y recuperación de Chat, Workspace Buscar + navegación, búsqueda negativa y tras reinicio, separación UID y permisos; `docs/s3-night/S3_A2_G5_ANDROID_PHYSICAL_PARTIAL_20261105.md`. Reality v6 tiene tres tablas vacías en la copia observada. **No extrapolar** 1/1 de JVM a UI móvil.
- Falta evidencia Android con documentos reales de Finanzas/Objetivos/Asuntos y detalle tipado y negativos de ACL/egress/IPC que no se hayan demostrado en teléfono; preparar fixtures DEV oficiales / emulador si se decide, no SQL manual.
- Legacy FBS1/FBS2 no lleva filas G4 Objetivos/Asuntos; respaldo `run-as tar` no sustituye backup portable. V2 shipping OFF, S4 OFF, Auto/Noche OFF, S2 8/8 frozen; no paid APIs, no catalog expansion.
- **Estado G5: OPEN_PARTIAL_PHYSICAL_PLUS_VERIFIED_HOST_1_1. S3.A2: 4/5 (80 %); S3 formal 0/5, desarrollo histórico aprox. 40 %.**

**B7 final:** PASS de la UNIDAD automatizada, no de G5 físico. No reabrir G1–G4 ni repetir XML; registrar checkpoint y liberar writer.
