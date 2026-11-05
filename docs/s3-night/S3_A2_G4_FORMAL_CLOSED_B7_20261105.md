# FARO V5 — S3.A2 G4 cierre formal B7 — 05/11/2026

## B7 AUDITORÍA FINAL INDEPENDIENTE — G4 CLOSED
**Unidad final:** G4 — fuentes reales de búsqueda Workspace, Finanzas, Objetivos y Asuntos con navegación tipada DEV aislada. **Rama:** `work/s3-night-20261102`. **Commit de última corrección:** `b45c42c769ec41ad90d9aa7b17b6d843c1910c24`. **Decisión: CLOSED_B7_AUTOMATED**. G5 (campaña física única) permanece pendiente; S3-A2 aún no se cierra (4/5 gates).

### Entradas verificadas, sin repetir pruebas de código no modificado
- Persistencia real de Objetivos y Asuntos en RealityStore v6, migración aditiva v5→v6, rollback/reinicio/idempotencia/preflight y bloqueo de backup legacy cuando hay filas G4: commit `0fab63a04de055e3479b33c6d38e1072646f42e3`; [Reality CI #42011878467](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42011878467) **SUCCESS, XML 21/21**.
- Cuatro fuentes canónicas con ACL por propietario, generación, relectura y navegación tipada `READ_ONLY` en host aislado: commits `05de1c6d22deed15ab5094ce4af5c3e091ffee4d` y `40ceb0c818ac76ebe0feee8f20224e1256d51809`; [G4 CI #42016712209](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42016712209) **SUCCESS, XML 27/27**, con regresión Reality 21/21 en ese mismo SHA.
- El CI de lifecycle introducido en `a49b5cea` reveló error de expectativa en test `durablePointerSwitchRevokesDevSearchAndEmitsLiveInvalidation`: [#42018187304](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42018187304) 9/10 FAIL. Se demostró que el host G4 usa cuatro fuentes `MutableStateFlow` con replay inicial, mientras la prueba heredada hacía `take(2)`. No se modificó runtime.
- B1–B5 preauditoría y permiso exactos en `docs/s3-night/S3_A2_G4_V2_CORE_INVALIDATION_REDFIX_B1_B5_20261105.md`, commit `e3960eb5597b80a066e66a36a689853c49cc4786`. Test-only RED_FIX `b45c42c769ec41ad90d9aa7b17b6d843c1910c24` cambia **un único archivo**: espera cuatro emisiones iniciales, después una quinta por cambio de puntero con timeout; conserva `Search UNAVAILABLE` y `old hit UNAVAILABLE`, y prueba ABA sin suscriptor intacta.

### Verificación final SHA exacto
- [V2 Core #42040913961](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42040913961): **SUCCESS**, HEAD `b45c42c769ec41ad90d9aa7b17b6d843c1910c24`. El log ejecutado, excluyendo ecos de código de GitHub Actions, imprime `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`, `V2_CORE_M2B_DEV_APP_ISOLATED_PASS 4/4` y `V2_CORE_M1_ISOLATION_PASS 4/4`; compilación y tests Gradle terminan SUCCESS.
- [Home notice #42040913988](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42040913988): **SUCCESS** SHA exacto. [Guard obligatorio #42040913870](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42040913870): **SUCCESS** SHA exacto. Guard del permiso padre `e3960eb`: #42040691198 SUCCESS.
- `git diff` del último código: únicamente `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthorityTest.kt`, dos imports y cambios en una prueba. G4 owners/código de navegación/almacenamiento no se tocan respecto de sus SHA previamente aprobados.
- Protocolos B1–B7 completos para esta corrección; writer Git CAS epoch 263 adquirido sólo para registrar este cierre y debe quedar **RELEASED**. Cola CLOUD STAGED, FARO Noche/Auto OFF, producción V2 OFF. No activar otra fase.

### Límites del dictamen
- **G4 CLOSED corresponde al alcance automatizado y DEV aislado**: no certifica instalación Android física, UI real en teléfono ni lanzamiento productivo. La campaña física se reserva a **G5**.
- El archivo portable FBS1/FBS2 legacy no transporta nuevas filas Objetivos/Asuntos; los backups legacy relevantes siguen **fail-closed**, sin migración/restauración portable G4 completa. No etiquetar como backup restaurable.
- S2 CLOSED 8/8. S3 cierre formal **0/5**, S3-A2 G1–G4 **4/5** (80% de sus gates); G5 pendiente. Estimación de desarrollo S3 ~40% histórica/heurística hasta nueva evaluación, no confundir con cierre formal.
- **Próximo paso:** G5, exclusivamente campaña física al final según protocolo, sin abrir S4 ni activar V2 productiva por este cierre.

**Dictamen B7:** G4 **CLOSED_B7_AUTOMATED**, evidencia remota reproducible por SHA/run/XML. No duplicar CI PASS.
