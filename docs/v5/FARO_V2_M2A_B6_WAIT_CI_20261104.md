# FARO V5 — M2A recuperación de primer arranque: checkpoint después de B6 (04/11/2026)

**Estado:** `B6 CODE_COMMITTED; B7 WAIT_CI`. No inventar PASS ni declarar `V2_DEV_CORE_READY`.

- Rama `work/s3-night-20261102`. HEAD código `59a6a46a09677840aa883933ed57a0697cc6ef7d`; diff exclusivamente `FaroV5V2CoreDevAuthority.kt` (DEV) y `FaroV5V2CoreDevAuthorityTest.kt` (test DEV), commit con `FARO-UNIT:M2A_FIRST_START_RECOVERY`.
- Investigación/compare/auditoría B1–B5: `docs/v5/FARO_V2_M2A_RECOVERY_B1_B5_20261104.md`, commit `78fab316a2f57af702124b314ee08c0da2a8dd01`, blob `7a1d2223128a7054ec55337430f0e0333973197a`. Permiso previo por commit documental separado `2a4b1682eb7bfebeb50c09f1d3c8a8dcc6978aee`.
- Guardia del commit funcional [#41888732289](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41888732289): **SUCCESS**, 14 tests de protocolo, `FARO_PROTOCOL_GATE_PASS 3 functional units`, `FARO_PROTOCOL_PREFLIGHT_PASS M2A_FIRST_START_RECOVERY 2a4b1682`.
- CI dirigida [#41888732803](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41888732803): **RUNNING / NO CERTIFIED** al registrar. M2 JVM espera XML exacto 4/4 (los cuatro métodos existentes ahora incluyen matriz de crash), T4 debug 7/7 y manifest M1/M2B 4/4. Regresión S3 [#41888732548](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41888732548): **RUNNING / NO CERTIFIED**.
- M2A implementación: PREPARING vacío o metadata de V2 explícita -> iniciar/reanudar stage pre-SQLite; STAGED con cinco stores ya íntegros -> validar sin reinicializar; VALIDATED con puntero V1 -> recuperación por coordinador probado; SQLite parcial, stage incompleto o journal ajeno -> detener sin borrar. `V2` productiva no activada.
- **B7 pendiente:** comprobar CI con SHA+log+JUnit XML, revisar negativos de puntero inesperado y symlink, y registrar PASS o RED_FIX. Si rojo, no abrir otra unidad ni cambiar política; corregir dentro de M2A y repetir solo CI afectada. Emitir nueva evidencia documental y actualizar permiso B7/estado, no mezclar archivo funcional y control.
- Restricciones: S2 CLOSED 8/8; S3 ≈40% desarrollado pero 0/5 cerrado; S4 OFF; Auto/Noche OFF; CLOUD STAGED, catálogo 172; V4.4/main intactos; sin API paga; no prueba física antes de campaña consolidada.
- Writer CAS adquirido epoch 156 para este checkpoint; **liberar al concluir** y revalidar HEAD/cola para cualquier escritura futura.
