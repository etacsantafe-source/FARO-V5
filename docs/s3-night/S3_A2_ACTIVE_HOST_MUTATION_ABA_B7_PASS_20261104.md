# FARO V5 — S3.A2 active-host pointer ABA · B7 auditoría final (04/11/2026)

## B7 AUDITORÍA FINAL
**Unidad:** `S3_A2_ACTIVE_HOST_MUTATION_POINTER_ABA`.
**Código:** `29a295adbfab89dfafbc182e55aa9f0693a2895d`.
**Base aprobada:** preauditoría B1–B5 `docs/s3-night/S3_A2_ACTIVE_HOST_MUTATION_ABA_B1_B5_20261104.md` en `3b249e7c04b81b6a40a6bdf143ea809e6cb5cd9d`; permiso independiente `e573166c6086825fcaa857ce8b1d92074b2c3ce7`.
**Dictamen B7:** **PASS limitado** a invalidar sesiones viejas del host Chat ante cambio durable del puntero A→B→A, para las rutas tocadas. **No cierra S3.A2-G1 ni habilita V2 productiva.**

### Evidencia exacta por SHA y CI
- Guardia obligatorio `#41944487030`: **SUCCESS**, `FARO_PROTOCOL_GATE_PASS` (13 unidades históricas), `FARO_PROTOCOL_PREFLIGHT_PASS S3_A2_ACTIVE_HOST_MUTATION_POINTER_ABA`, 14 casos del guardia PASS.
- CI dirigida S3 Home/Chat `#41944487110`: **SUCCESS**, compilación `BUILD SUCCESSFUL`; XML: `S3_ACTIVE_HOST_ABA_XML_PASS 1/1`, `S3_CHAT_POINTER_ABA_XML_PASS 6/6`, `S3_HOME_NOTICE_XML_PASS 9/9`, `S3_WORKSPACE_SEARCH_XML_PASS 7/7`, `S3_SEARCH_BRIDGE_XML_PASS 5/5`, `S3_AVISOS_XML_PASS 4/4` (**32/32** dirigidas, cero errores/fallos/saltadas).
- CI V2 core `#41944487043`: **SUCCESS**, DEV Search 10/10; T4 debug trasladado 7/7; manifest aislado M1 4/4, DEV M2B 4/4.
- CI APK DEV `#41944487048`: **SUCCESS**, único launcher aislado `ar.com.faro.alpha.v2coredev`, permisos OS prohibidos ausentes, verificación firma v2 PASS, APK SHA256 `1150edfe66f64bae9abbf8caefa239eb8374c2a8e058db59059ada96cf071d71`. **No se instaló físicamente.**
- Writer global CAS liberado epoch **196** antes de la CI, sin mantenerlo ocupado durante las pruebas. La liberación del writer para este B7 se verifica al finalizar el checkpoint.

### Contraste de seguridad y diff
- Solo siete rutas: `FaroV5CompositionRoot.java`, `FaroV5ActiveChatComposerSession.kt`, `FaroV5ActiveChatSelectionSession.kt`, `FaroV5ActiveChatReviewSession.kt`, `FaroV5ActiveReviewedApplySession.kt`, una nueva prueba `FaroV5ActiveHostPointerABATest.kt` y workflow S3 dirigido.
- `CompositionRoot` reutiliza `pinnedChatPointer` y un único `BooleanSupplier` fail-closed; lo conecta también a Composer, Selection, Review y Apply. Los puertos revalidan junto a readiness y generation ID; mutaciones siguen dentro del `SingleProcessMaterialWriteGate`.
- El nuevo test usa `FileActiveGenerationPointer` real, fuerza A→B→A con secuencia nueva y comprueba que selección, lectura/escritura de borrador, consulta/decisión Review y Apply antiguo fallan; Reality permanece sin cambios. Conserva las pruebas históricas en lugar de reejecutar una campaña física.
- No se tocaron cinco stores, schema/FBS2, manifest, app habitual, main, V4.4, permisos Android, catálogos, pagos, egress ni actividad productiva de V2.

### Límites y riesgos residuales
- El oráculo prueba secuencia durable y **una sola instancia/proceso con material gate**. No certifica sincronización interproceso ni atomicidad/rollback entre procesos: una carrera ajena puede requerir fencing transaccional adicional.
- Los constructores de sesiones conservan defaults de compatibilidad con tests/unidades previas; **solo el CompositionRoot activo** entrega la autoridad de puntero completa. No afirmar que cualquier constructor aislado obtiene automáticamente el fence.
- **Pendiente S3.A2-G1:** propietario real shipping de ACL Workspace/Search con identidad scope+grant/revoke, epoch propio, invalidación observable y semántica de reinicio/restore, independiente del puntero y readiness. La autoridad de Buscar en `v2CoreDev` es **solo DEV** y no equivale a ACL productiva.
- S3.A2-G2/G4/G5 y campaña física permanecen abiertos. V2 productiva, Auto, Noche y S4 OFF. S2 CLOSED 8/8 congelada.

### Entrega y próxima unidad
**B7 PASS para el delta y SHA de esta unidad, NO para S3.A2 completo**. Dejar permiso de nueva escritura `BLOCKED` hasta que la próxima unidad reúna B1–B5 y READY independiente. **Siguiente:** inspección enfocada y comparación B1–B5 del verdadero owner ACL del Workspace shipping (no otra auditoría general); descartar reutilizar el grant efímero de DEV como autoridad productiva sin pruebas de cross-instance/revoke/restore.

**Progreso:** S3 ≈40% desarrollo estimado (sin nuevo % artificial), 0/5 subetapas formalmente cerradas, S3.A2 0/5 gates completos. Tiempo manual efectivo: NO_REGISTRADO.
