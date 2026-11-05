# FARO V5 — S3.A2-G1 · GATE CLOSED (single-process host) / B7 final (04/11/2026 AR)

## B7 AUDITORÍA FINAL — G1 CLOSED EN SU FRONTERA SOPORTADA

**Gate:** `S3.A2-G1`, autoridad real de lectura Workspace Search para la **arquitectura Android de un usuario y un proceso**. **Unidad funcional:** `S3_A2_G1_POLICY_RESTORE_READMISSION`.
- Base anterior certificada sin repetir: grant/revoke `9257e91fb5530955bdb1ade9fa2a9e8450611ca1` con B7 PASS local.
- **B1–B5 final:** `docs/s3-night/S3_A2_G1_FINAL_POLICY_RESTORE_READMISSION_B1_B5_20261104.md`, commit `ad90b8101b3c86ce8f61eb033f7834fd7f2fb267`.
- **Permiso final:** `39fde93dfc34294bf447a0717aa258a53147d1d2`, preflight `#41955175339 PASS`.
- **Código G1 de recuperación:** `dc3eee9de857a00b07a808c3ed19f6b60acff3d6`, B7 inicial RED_FIX por error de compilación **solo en test JVM**.
- **B1–B5 RED_FIX:** `docs/s3-night/S3_A2_G1_FINAL_JVM_SUSPEND_REDFIX_B1_B5_20261104.md`; nuevo permiso de una única ruta `b6a78ce56a9b78ae3fc71193a771b16eab7b0738`, preflight `#41956848427 PASS`.
- **HEAD funcional final y B7 PASS:** `f5f57a04da3e494bd03f517fccc42f9ab360b09d`: test file-backed de reinicio en `runBlocking`, sin cambios de producción adicionales.

### Evidencias CI exactas — HEAD funcional final `f5f57a04`
1. GitHub Actions **S3 Home notice directed `#41957095077` SUCCESS**. `BUILD SUCCESSFUL`, XML exactos sin fallos, errores ni skips: Home **9/9**, Workspace Search **7/7**, Bridge **5/5**, Avisos **4/4**, Chat pointer A→B→A **6/6**, active host pointer **1/1**, **Workspace Search host grant 10/10**; total **42/42**. Los cuatro tests nuevos son readmisión tras recovery real, stale pointer sin regrant, dos instancias de puntero A→B→A y reinicio con SQLite Workspace file-backed.
2. En **el mismo run `#41957095077`**, se verificaron **dos manifests debug fusionados**, ambos sin atributos `android:process` en Application/componentes, y `android:allowBackup=false`: `S3_G1_SINGLE_PROCESS_MERGED_MANIFEST_PASS 2`. Si un componente declara otro proceso en futuras compilaciones, la CI falla y este alcance de G1 ya no aplica.
3. **Protocol guard `#41957095087` SUCCESS**, `FARO_PROTOCOL_GATE_PASS 17 functional units validated`, `FARO_PROTOCOL_PREFLIGHT_PASS S3_A2_G1_POLICY_RESTORE_READMISSION b6a78ce...`.
4. Evidencia **no repetida** de compilación del mismo código productivo: M5 isolated V2 DEV APK `#41955987191` **SUCCESS** sobre `dc3eee9d` (antes del cambio posterior exclusivo al test), package `ar.com.faro.alpha.v2coredev`, launcher aislado, firma APK v2, permisos OS prohibidos ausentes, SHA256 `7cf7fb918cdc661a44ac39b57e5b934f1d89fc589e3c7c12696bffc7adcd7732`. **No hubo instalación física.**
5. **Límite honesto:** la corrida V2 core `#41955987005` sobre `dc3eee9d` fue **FAILURE** en la compilación del nuevo test suspend fuera de corrutina; `:app:compileV2CoreDevKotlin` había compilado, pero NO afirmar que el workflow V2 core completo terminó verde. El commit test-only `f5f57a04` activa CI S3 y guard, no el workflow de núcleo V2 (su filtro no incluye `src/test/**`). G1 se cierra para Debug/host single-process con XML 42/42 y el APK productivo aislado previo; el próximo gate de integración G2 deberá verificar otra vez la variante correspondiente. Sin certificación productiva V2.

### Pruebas de autoridad y ausencia de reanimación
- `FaroV5ActiveWorkspaceSearchGrantOwner` es el **owner de grant** de sesión activa, no un write token Workspace, un booleano de UI ni un estado de Flow. `epochOrNull` requiere admisión, puntero y readiness válidos; revocación + regrant producen siempre un sello nuevo (contador de sesión y UUID). La concesión vieja no se rehidrata.
- `FaroV5StartupReadinessGate` es el emisor auténtico de not-ready; listener del host revoca aunque ninguna UI recolecte invalidaciones. El `CompositionRoot` concede nuevamente *solo después* de `completeStartupRecovery()` / `completeMaintenanceOperation()` con LOCAL_READY y puntero durable completo sin cambio. Dos eventos ready seguidos no renuevan una concesión todavía activa.
- `DeliveryGraph.matchesActivePointer` compara `ActiveGenerationPointer(sequence,generation)` antes de reutilizar caché. Un salto A→B→A con mismo generationId pero nueva sequence obliga a cerrar el grafo viejo y reabrir host validado; los puertos viejos quedan revocados.
- La restauración interna usa generación staged y switch durable; backup Workspace no guarda el ACL efímero. El test SQLite file-backed demuestra que la conversación sobrevive al reinicio y que el grant NO: al reabrir, la autoridad empieza en null hasta nueva concesión explícita y nueva identidad.
- Dos instancias independientes de `FileActiveGenerationPointer` sobre la misma raíz deniegan resultados viejos tras A→B→A, incluso sin listeners ni Flow collectors. Esto demuestra revalidación de lectura durable **entre instancias del mismo proceso**, NO IPC multiproceso ni ACL multiusuario.
- G1 no incorpora otro store, migración, permisos Android, índice con snippets privados, FBS2 ni API paga. Search sigue conectado **solo internamente** al DeliveryGraph condicionado por V2 OFF. La app habitual mantiene `FaroV5BootstrapActivity` Search `null`.

### Límite y política verificable
La aplicación actualmente compone un único `FaroV5Application` / `FaroV5CompositionRoot` por proceso; el manifest fusionado no declara proceso separado. Android documenta que componentes sin atributo `android:process` comparten el proceso de la aplicación: https://developer.android.com/guide/components/processes-and-threads. El modelo soportado por este gate es **local-first, usuario único, un proceso**; no afirma que un evento de otro proceso llegue ni soporta cuentas/sincronización ACL entre varios dispositivos. Agregar multiproceso, identidad remota o compartir UID **requiere una nueva certificación** (no queda escondido como pendiente indefinido de G1).

No se probó Android físico: por decisión S3 se ejecuta **una única campaña física al final**, G5. Tampoco se habilita V2 productiva por este cierre.

### Estado de gates y continuidad
- **S3.A2 G1 = CLOSED**, scope explícito single-process/host JVM + manifiesto; todo B1–B7 satisfecho según los límites y CI registrados.
- **S3.A2 = 1/5 gates CLOSED**: G2 conexión UI autorizado OPEN, G3 búsqueda real contenido/idioma/restore PARTIAL (aprovechar pruebas previas), G4 federación otros owners OPEN, G5 integración y campaña física OPEN. **No** marcar G2–G5 verdes por G1.
- **S3 = 0/5 subetapas A1–A5 CLOSED** y aproximadamente 40% de desarrollo estimado, no confundir con A2 1/5. **S2 CLOSED 8/8**. S4, V2 productiva, Auto, Noche OFF.
- **Próximo trabajo:** iniciar S3.A2-G2 (puerto Search del host genuinamente autorizado) usando código ya instalado y este gate; mantener V1 sin Workspace UNAVAILABLE. Antes de nuevas escrituras funcionales completar otra unidad B1–B5, permiso READY, preflight y writer CAS. No repetir G1, ni pruebas antiguas sin causa.

**Dictamen:** `B7_PASS_AND_G1_CLOSED_SINGLE_PROCESS_HOST`, sin ampliación de plataforma ni activación productiva.
