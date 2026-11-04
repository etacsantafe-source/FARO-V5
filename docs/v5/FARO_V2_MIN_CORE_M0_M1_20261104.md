# FARO V5 — M0/M1: desbloqueo V2 mínimo aislado (04/11/2026)

**Objetivo humano vigente:** integrar V2 mínima de DESARROLLO para recuperar funciones; después retomar cierres S3 y S4. **V2 productiva OFF, Auto/Noche OFF.** Rama exclusiva work/s3-night-20261102. S2 8/8; S3 ≈40% dev /0 de 5 cierres; S4 sin abrir.

## M0 — mapa READ_ONLY comprobado en código

- **Cinco owners:** `FaroV5GenerationStoreGraph.openActiveV2WithSharedGate` y `FaroV5ActiveGenerationGraphOwner.open` ya permiten cinco bases sanas bajo gate compartido; V1 sólo abre cuatro y `workspace=null`. Falta la admisión V2 restringida a host aislado con puntero genuinamente activo, no rehacer stores.
- **Generación y recuperación:** `FileStorageGenerationLayout.stageWorkspaceV2`, `FileActiveGenerationPointer`, `FaroV5PreSwitchHostAdmission`, WAL preflight y coordinator existen. Falta encadenar staging, cambio de autoridad en raíz DEV, journal/replay fence, reinicio y rollback. No publicar `compareAndSet` ad hoc sobre stage incompleto.
- **Chat:** reutilizar `FaroV5ActiveChatReadSession`, `FaroV5ActiveChatSelectionSession` y `FaroV5ActiveChatComposerSession`, verificando generación/readiness/único writer. F1 Chat en staging no equivale a V2 activa.
- **Seguridad:** conservar `FaroV5HostCertification.V2_ACTIVE_ENABLED=false`; IA remota, Apply y dispatch OS denegados por defecto. Workspace write-token NO es ACL de lectura.
- **S3/S4:** Buscar shipping continúa UNAVAILABLE hasta autorización real; no se probó ninguna dependencia S4 imprescindible para abrir Chat Workspace local. No abrir S4 por suposición.

## M1 — unidad de código

- Nuevo build type `v2CoreDev`: paquete `ar.com.faro.alpha.v2coredev` diferente del habitual debug `ar.com.faro.alpha.v5dev`. Android separa UID, datos, SQLite, prefs y Keystore por paquete. Sin traslado de datos antiguos.
- Manifest propio elimina permiso INTERNET y conserva `allowBackup=false`; etiqueta `FARO V2 CORE DEV`. Es aislamiento de build, **no habilita V2 ni ejecuta Apply/IA/OS**.
- CI dirigida comprueba build, paquetes distintos, backup OFF y red OFF. `M1_CI_PENDING` hasta observar run exacto. No afirmar `V2_DEV_CORE_READY` por compilar.

## Próximos bloques, sin volver al bucle

M2: entrada explícita DEV con generación V2 activa de cinco owners, usando coordinator/recovery; no cambiar flag global. M3: Chat local read/select/composer. M4/M5: negativas de IA/Apply/OS, pointer/restart/stale, CI XML exacta. Luego cerrar S3 y después S4. Sin catálogo 172→180, sin APIs pagas, sin afectar V4.4/main. Pruebas físicas en una campaña posterior.
