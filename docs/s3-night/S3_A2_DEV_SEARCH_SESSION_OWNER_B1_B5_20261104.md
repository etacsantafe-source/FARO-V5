# S3.A2 — Unidad DEV_SEARCH_SESSION_OWNER (B1–B5, 04/11/2026)

**HEAD revisado:** `7ba9cbc635eeedc4bccce5072c09a9cd4a638092` · **solo variante `v2CoreDev`** · V2 shipping OFF, Auto/Noche OFF, S2 congelada y S4 cerrada.

## B1 EVIDENCIA
- `FaroV5V2CoreDevAuthority.kt` abre cinco owners mediante journal/fence/puntero durable en directorio exclusivo; produce sesiones Workspace/Composer/Selection, cierra graph al destruir y nunca construye modelo/Apply/OS delivery.
- `FaroV5V2CoreDevActivity.kt` monta `FaroV5SurfaceHost.install` sin `searchReadPort`. La V2 DEV compila con ID `ar.com.faro.alpha.v2coredev`, DEBUG y sin permisos/red/backup; la prueba física de Búsqueda no está corrida.
- `FaroV5WorkspaceSearchReadSource.kt` ya lee títulos y mensajes canónicos, confirma generación/recovery, exige `aclEpoch()` explícito y rehúsa datos si falta. `FaroV5WorkspaceSearchSurfaceAdapter.kt` revalida antes y después de buscar/seleccionar y protege hits previos (SHA anteriores y tests PASS; no repetir).
- `FaroV5HostCertification.V2_ACTIVE_ENABLED=false` impide tratar la autoridad de DEV como autorización productiva.
- `FaroV5V2CoreDevAuthorityTest.kt` incluye 6 casos que cubren bootstrap/stale read/crashes/corrupción; no hay prueba de Search DEV. `.github/workflows/faro-v5-v2-core-directed.yml` ejecuta 6 tests XML exactos y chequea paquete sin permisos.
- BRECHA: solo falta autorizar Search dentro de la sesión real DEV, sin exponer ese permiso fuera de la sesión ni crear ACL productiva ficticia.

## B2 COMPARACIÓN
- V4.4 `app-v4/src/main/kotlin/ar/com/faro/v4/conversation/FaroV4GlobalSearch.kt` ya ofrece búsqueda por dominios, normalización y resultados accionables, pero no usa el formato V5 ni su owner Workspace. V5 ya tiene coordinador, source y adapter; reusar código actual sin importar DB/código V4.4.
- Reutilizar `FaroV5ActiveChatReadSession`, `FaroV5ActiveChatSelectionSession` y el `ready` real del DEV; no abrir store paralelo, no cambiar esquema FBS2 ni replicar buscador.

## B3 INVESTIGACIÓN EXTERNA COMPARADA
- Evidencia externa detallada, decisiones USAR/ADAPTAR/DESCARTAR y enlaces: `docs/s3-night/S3_A2_G1_B3_EXTERNAL_REAL_WORLD_RESEARCH_20261104.md`.
- **OWASP** autorización deny-by-default/recheck por operación (`https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html`) => USAR.
- **Android architecture** single source of truth host/owner, no UI boolean (`https://developer.android.com/topic/architecture`) => USAR.
- **Room multiinstance invalidation** (`https://developer.android.com/reference/androidx/room3/RoomDatabase.Builder`) => DESCARTAR para ACL de DEV: observa tablas y no crea permisos; costaría dependencia y no aplica al permiso efímero.
- **Element X Android bug de cuenta cruzada** (`https://github.com/element-hq/element-x-android/pull/7877`) y reporte navegación de Search (`https://github.com/element-hq/element-x-android/issues/7988`) => ADAPTAR pruebas de sesión cruzada/stale locator, no código.
- **Alternativa A:** nueva tabla persistente ACL Workspace; rechazada para DEV porque exige migración de 5 owners/FBS2 y puede introducir autoridad falsa en backups. **Alternativa B:** grant por sesión DEV con UUID fresco y estado observable; seleccionada exclusivamente para un proceso/host, deny on close, sin restaurar grants. **Alternativa C:** epoch fijo basado en pointer/ready/write token; descartado por ABA y mezcla de autoridad.

## B4 UNIFICACIÓN / DELTA
- Crear `FaroV5V2CoreDevSearchReadAuthority` **solo en `src/v2CoreDev`**, inicial denegada, `grantAfterVerifiedOpen()` solo invocado por host tras journal/fence/puntero, UUID nuevo para cada sesión; `epochOrNull()` requiere `ready` y grant vigente, `close()` revoca de forma irrevocable y emite invalidación.
- Exponer `Flow<Unit>` de invalidaciones observable `StateFlow` con replay tras nueva suscripción; escuchar `FileActiveGenerationPointer.addChangeListener` del mismo root solo mientras la sesión existe. Es notificación, no autoridad; cada consulta revalida puntero/journal/epoch, fallando cerrada en cross-process si puntero cambia.
- Reusar el `graph.workspace` activo (sin abrir otro SQLite) y `FaroV5WorkspaceSearchReadSource` + `FaroV5WorkspaceSearchSurfaceAdapter`. Pasar `searchReadPort` al `FaroV5SurfaceHost.install` exclusivamente en DEV; nadie obtiene un `grant()` por la UI.
- Rutas limitadas: una nueva clase `v2CoreDev`, `FaroV5V2CoreDevAuthority.kt`, `FaroV5V2CoreDevActivity.kt`, test DEV `FaroV5V2CoreDevAuthorityTest.kt`, workflow dirigido `faro-v5-v2-core-directed.yml`.
- Si falla el host, cerrar graph; si termina Activity, revocar antes de cerrar graph. Al reiniciar proceso el grant antiguo no se persiste y se concede uno **nuevo solo tras volver a validar**. No atribuir esto a un owner ACL durable/productivo; G1 shipping sigue OPEN.

## B5 AUDITORÍA PREVIA ADVERSARIAL
- Aceptación mínima: (a) búsqueda real de título/mensaje de Workspace DEV, (b) resultado y selección con el mismo owner, (c) cierre deniega búsqueda/navegación vieja, (d) reabrir crea permiso distinto, (e) switch de puntero deny/unavailable e invalidación; test con datos locales temporales y XML exactos.
- Negativas: no usar un `aclEpoch` fijo; no permiso UI; no reusar grant tras restart/close; no lectura de backups o stages; no cambiar provider/permission de APK, no egress, no Apply, no tocar datos productivos; no presumir multi-process notification: explícitamente **solo mismo proceso**.
- Consulta en vuelo puede descubrir revocación después de seleccionar; el resultado debe ser UNAVAILABLE, no evidencia de rollback. Para certificado shipping necesitará serialización real de ACL+write gate y tests de carrera, fuera del alcance DEV.
- Side effects permitidos: selección del chat por acción explícita del usuario; no side effects externos. Tests deben asegurar aislamiento, recover y IDs existentes. No añadir librerías nuevas ni modificar esquema SQLite.
- **Dictamen B5: READY ONLY FOR ISOLATED DEV SEARCH SESSION**, no G1 productivo; B6 exige permiso separado sobre rutas exactas, CAS writer y CI; B7 independientemente audita XML, SHA y ausencia de permiso/flags nuevos. Si CI no termina verde => WAIT_CI/RED, no PASS.
