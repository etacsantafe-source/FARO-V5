# FARO V5 — S3.A2-G2 · GATE CLOSED / B7 final (05/11/2026 AR)

**Unidad:** `S3_A2_G2_AUTHORIZED_HOST_UI_WIRING`. **Alcance:** conectar el puerto local Workspace Search al shell normal sólo si el **host activo ya certificado** tiene recuperación completada, estado LOCAL_READY y grant genuino G1 vigente; **no** habilitar V2 productiva. **B1–B5:** `docs/s3-night/S3_A2_G2_HOST_SEARCH_UI_WIRING_B1_B5_20261104.md` blob `dd223087c1811a861da0179e6a8a2fbdd8f099ed`, permiso `82858c3dd39d7502ea97f370583ee55094b5524b`, guard preflight `#41958962242 SUCCESS`.

## B7 AUDITORÍA FINAL — G2 CERRADO EN SU FRONTERA SOPORTADA

### Código y delta
- Commit funcional único `6ea5f223df720e0aeec0bda36a1ef671746dbd01` (6 rutas exactas): nueva `FaroV5SearchHostPortAdmission.kt`, `FaroV5CompositionRoot.java`, `FaroV5Application.java`, `FaroV5BootstrapActivity.java`, `FaroV5SearchHostPortAdmissionTest.kt`, workflow `.github/workflows/faro-v5-s3-home-notice-directed.yml`.
- El puente no crea grants ni modifica almacenamiento: la clase admission valida `recoveryComplete && localReady && V2_ACTIVE_ENABLED && port!=null && owner!=null && owner.epochOrNull()!=null`; el `CompositionRoot` se niega ANTES de abrir deliveryGraph si flag/recovery/readiness niegan. El shell recibe `readActiveSearchPort()` en lugar de `null`. `FaroV5HostCertification.V2_ACTIVE_ENABLED=false` se mantiene inalterado: **el shell V1 habitual sigue sin Search de Workspace V2**.
- El owner G1 y `WorkspaceSearchSurfaceAdapter` ya existentes continúan validando grant, generación durable y publicación de resultados en cada read/open. Cualquier cambio de autoridad revoca hits antiguos; la UI no puede concederse acceso por mostrar un resultado. App DEV aislada conserva su bridge independiente, sin conexión al storage de app normal.

### Evidencia CI exacta del código `6ea5f223`
1. **Protocolo obligatorio `#41959201264 SUCCESS`**, preflight permiso anterior `#41958962242 SUCCESS`.
2. **S3 Home notice directed `#41959201283 SUCCESS`**: `BUILD SUCCESSFUL`; JUnit XML exactos sin skips, errores ni failures: Home 9/9, Workspace Search 7/7, Bridge 5/5, Avisos 4/4, Chat pointer 6/6, active host ABA 1/1, owner grant G1 10/10, **G2 admission 10/10**, total **52/52 PASS**. Manifest fusionado single-process y backup boundary **2 PASS**.
3. **V2 Core M1 M2 directed `#41959201284 SUCCESS`**: 3 compilaciones BUILD SUCCESSFUL; `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`; `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`; `V2_CORE_M2B_DEV_APP_ISOLATED_PASS 4/4`; `V2_CORE_M1_ISOLATION_PASS 4/4`. Esta prueba confirma la variante de desarrollo aislada, NO una V2 productiva activa.
4. **M5 isolated DEV APK `#41959201293 SUCCESS`**: `M5_ASSEMBLE_V2_CORE_DEV_PASS`, paquete `ar.com.faro.alpha.v2coredev`, launcher exclusivo `ar.com.faro.v5.app.FaroV5V2CoreDevActivity`, sin permisos OS prohibidos, firma APK validada y SHA256 binario `bdf539e5d4d75a8625335dc12597af6469a8e2c28a4d3301b7410d0a6d83abb0`. Artefacto Actions ID `13384612230`, sólo paquete DEV aislado. **No instalado ni probado físicamente**.

### Adversariales y límites
- El nuevo gate comprueba V2 OFF, startup recovery inconclusa, LOCAL_READY false, puerto/owner ausentes, sesión sin grant, grant revocado, cierre y pérdida de admisión sin revivir el ACL viejo. Identidad de grant nueva tras regrant. Probado por 10/10 G2 más owner 10/10 G1.
- El componente `readActiveSearchPort` es un puente de presentación, no una certificación de que la flag V2 puede activarse. El owner conserva autoridad canónica, no la UI, no el índice, ni la selección del chat. Toda lectura posterior es verificada en G1.
- La compilación APK no sustituye el único lote físico previsto para G5. Sin permisos Android adicionales, secretos, nuevo motor/índice, pagos, Apply/Undo nuevo, cambio V4.4, main ni expansión Guardados. El alcance es Android local single-user single-process; no ACL multiproceso/multiusuario, sync, nuevos owners federados ni Search UI operativo en V1.
- Riesgo residual no atribuible a G2: navegación exacta por mensaje/contenido e idioma G3 (PARTIAL), federación G4 (OPEN), accesibilidad/rotación/reinicio físico e integración global G5 (OPEN). El `V2_ACTIVE_ENABLED` productivo no cambia; su activación requiere certificación y decisión separadas.

### Dictamen y progreso
- **`S3.A2-G2 = CLOSED_B7_PASS_AUTHORIZED_HOST_UI_WIRING`** dentro del entorno soportado. No reabrir G1 ni G2 por inercia. Código vivo 6ea5f223; HEAD documental posterior no implica nueva prueba funcional.
- **S3.A2 = 2/5 gates CLOSED (40%)**: G1 CLOSED, G2 CLOSED, G3 PARTIAL, G4 OPEN, G5 OPEN.
- **S3 = 0/5 subetapas A1–A5 formalmente CLOSED**; progreso orientativo de implementación ≈40% histórico (conservador, no equivaler a 2/5 de A2). **S2 = CLOSED 8/8 (100%).** S4, V2 productiva, Auto y Noche OFF.
- **Siguiente:** continuar sólo G3 aprovechando búsqueda canónica y coincidencias ya testeadas; no repetir G1/G2. Requiere nueva unidad B1–B5, permiso READY específico, preflight y writer CAS antes de código.

**Resultado B7:** `PASS_G2_CLOSED_WITH_PRODUCTIVE_V2_OFF_AND_NO_PHYSICAL`. Bloquear permiso G2, registrar puntero durable y liberar escritor CAS.
