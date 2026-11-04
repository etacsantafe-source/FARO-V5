# FARO V5 — M2.B / M3 inicial: launcher exclusivo de Workspace local (04/11/2026)

**Scope:** build type `v2CoreDev` EXCLUSIVO. Production V2 OFF. No shipping root, no ModelGateway/IA, no Apply, no OS delivery, no Search ACL grant.

## De M2.A a un host Android real

- `FaroV5V2CoreDevApplication` es una `Application` deliberadamente vacía. Sustituye en manifest DEV a `FaroV5Application`: no ejecuta `scheduleStartupRecovery`, no crea `FaroV5CompositionRoot`, no registra trabajadores ni configura Groq.
- Manifest DEV elimina del paquete aislado los launchers habituales `FaroV5BootstrapActivity`, F1 debug, T4 SAF, Settings, fixtures/provider de certificación y `androidx.startup.InitializationProvider`. Sólo queda `FaroV5V2CoreDevActivity` como Activity propia `MAIN/LAUNCHER` exportada. `allowBackup=false`, sin INTERNET y UID distinto de V1 habitual.
- El host niega inicio fuera del paquete `ar.com.faro.alpha.v2coredev`, niega builds no-debug y rechaza una V2 productiva activada. Inicio requiere **click explícito**.
- Únicamente usa `noBackupFilesDir/faro-v2-core-dev-root`; `FaroV5V2CoreDevAuthority` monta generación V2 activa con journal/fence y reanuda Workspace real, sin abrir la carpeta `faro-v5-storage` de la app habitual.
- Cuando abre, garantiza una conversación local seleccionada y monta `FaroV5SurfaceHost` solamente con Workspace read, timeline, selection y composer. `reviewPort=null`, `applyRequestPort=null`, `assistantTurnPort=null`, `searchReadPort=null`, Home/Avisos externos y onOpenAiSettings también null.
- CI dirigida exige manifest dev: clase de `Application` exacta, UNA Activity, launcher exclusivo, Startup Provider ausente, red OFF y M1 4/4; compila código Activity y dirige pruebas M2.A + M3 4/4 (incluyendo mensaje USER ACCEPTED persistido sin respuesta FARO sintética). No afirmar Android físico ni capacidad de IA real.

## Límites visibles

Esto habilita **Chat local de desarrollo**, no FARO IA ni integración productiva. Se mantiene ausencia de Search y ACL independiente; aplicar planes, consultar Groq y producir notificaciones siguen bloqueados. El código de pruebas físico y las configuraciones históricas no se borran, sólo se excluyen de este paquete.

**Próximo gate:** CI exacta para paquete dev y Workspace; explorar test de envío de mensaje en M3, restart, fragmentos, crash recovery, generación stale y denegaciones de salida; campaña física única después de consolidar. M2.B permanece **PENDIENTE CI** hasta resultado registrado.

S2 CLOSED 8/8; S3 desarrollo ≈40%/cierre formal 0 de 5; S4 OFF; V2 productiva OFF, Auto/Noche OFF, catálogo 172.

## M3 — prueba incremental del Composer local

Cuarto test en `FaroV5V2CoreDevAuthorityTest`: crea conversación en generación V2 activa, guarda draft usando token y revisión canónicos, envía `ConversationDraftSendRequest` a Workspace bajo gate, verifica únicamente mensaje `USER/ACCEPTED` sin fabricar respuesta FARO y comprueba supervivencia al reinicio. La CI exige **4/4 XML** (3 M2A +1 M3), **7/7 T4 debug preservadas**, M1 4/4 y host M2B 4/4. Ninguna prueba sustituye campaña Android física.
