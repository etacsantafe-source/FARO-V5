# FARO V5 — S3.A4 política de lectura local por instalación (CORE) · B1–B5
Fecha AR: 2026-11-08. Rama: `work/s3-night-20261102`. Base auditada: `1c2bed55ca9872ed1dbedb67f2e6568d45b92690`.
Unidad propuesta: `S3_A4_LOCAL_INSTALL_READ_POLICY_CORE`; solo dos nuevos archivos Kotlin (clase y prueba); NO montaje, NO V2 activa, NO A4 CLOSED.
La decisión de producto está limitada a **una instalación Android local, un perfil de app, datos privados de esa instalación, sin cuentas multiusuario ni sincronización**. Un UID no identifica a una persona. Toda lectura necesita además acción explícita de primer plano desde host confiable; este core no prueba ni suministra esa acción (se audita en la futura unidad de montaje).

## B1 — Evidencia exacta y aceptación acotada
- HEAD inicial `1c2bed55`, closure gate `S3_A4_CLOSURE_GATE_READ_ONLY_20261108.md`: N015 Surface 68/68 y Real SQLite Host 28/28 ya PASS; N031 Reality8 planning host 18/18 y Matter source 6/6 previos; ausencia de proveedor real de ACL y montaje, V2 productiva OFF.
- `FaroV5AndroidSandboxPrincipal.kt` verifica Application package/UID, no isolated/SDK sandbox, pointer secuencial A→B→A, host epoch y nonce por proceso; **evidencia técnica, no identidad humana**. B7 anterior 14+ negativas; no reimplementarlo.
- `N015TrustedHistoryReadPolicyPort` y `Planning8TrustedDueReadPolicyPort` ya requieren epoch independiente por dominio y rechazán null. Los lectores y Surface no forman parte del diff.
- Meta binaria CORE: (1) ninguna autorización sin acción local explícita y estado en primer plano; (2) cinco dominios independientes RealityReceipt, WorkspaceMessage, MatterDue, ReminderOccurrence, MonthlyPlan; (3) revocar dominio cambia/invalida epoch, no restaura grants por process/lifecycle/pointer; (4) missing facts, otra instalación, ABA, background y cierre: DENY, no cero; (5) NO escritura ni egress, NO prueba de policy productiva hasta montaje real.
- Los ensayos existentes conservan su SHA; solo pruebas nuevas atribuibles a la unidad; G15 y S3.A5 NO ejecutados.

## B2 — Comparación y activos
- V5 reutiliza `FaroV5AndroidSandboxPrincipal`, `FileActiveGenerationPointer`, políticas-read por dominio de N015/N031 y `FaroV5HomeReadCoordinator`, no crea un sexto owner, no reutiliza Search/Apply grant.
- V4.4 se mantiene **estrictamente solo lectura**. Para esta unidad de autorización Android no hay contrato V4.4 equivalente certificado en la evidencia actual; adoptar credencial legada rompería la V5 limpia. Los reportes comparativos históricos no constituyen autorización.
- Contra shipping: `FaroV5CompositionRoot` solo monta Avisos y Home parcial; no monta N015 real host ni N031 triple fuente y V2_ACTIVE_ENABLED sigue falso. Por eso este core por sí solo NO desbloquea esos flujos.

## B3 — Investigación externa de patrones (sin dependencias ni APIs pagas)
- Android sandbox https://source.android.com/docs/security/app-sandbox : **ADAPTAR** verificación del proceso/UID para aislamiento técnico; no lo promocionar a usuario autenticado.
- OWASP Authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html : **USAR** deny-by-default y revalidar permiso por recurso/fuente, incluso después de una lectura.
- OWASP Authorization Patterns https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Patterns_Cheat_Sheet.html : **ADAPTAR** decisión por dominio independiente, ejecución de enforcement en cada lector; no añadir motor externo de ACL.
- Android offline first https://developer.android.com/topic/architecture/data-layer/offline-first : **USAR** SQLite canónico local, no red para obtener una lectura ya autorizada.
- Alternativa descartada A: package/UID, pointer y estado READY usados como autorización implícita permanente (sin consentimiento, imposible revocar).
- Alternativa descartada B: token global para history/query/Apply/Search (BOLA y escalación transversal de autoridad).
- Alternativa elegida: core de grants transitorios por fuente, sesionados y revocables, con entrada de acción de primer plano verificada en la FUTURA CompositionRoot/Activity. Sin librerías nuevas ni egress.

## B4 — Unificación DELTA ONLY
- Crear `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5LocalInstallReadAuthority.kt`: fachada interna de solo lectura sobre el **probe Android existente**, fuente de estado host+puntero y entrada de acción de primer plano inyectada por futuro host; grant independiente con nonce no persistente por dominio; puertos N015 y Planning8 + Matter, invalidations.
- Crear `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5LocalInstallReadAuthorityT2Test.kt` con hechos sandbox simulados para JVM. Fixtures jamás prueban Application real, ni autorización humana, ni shipping.
- No cambiar `FaroV5CompositionRoot`, Bootstrap, Surface, SQLite, esquema, manifiesto, permisos, workflows, contratos de Apply/Undo ni contador de Inicio con esta unidad. El montaje será posterior a B7 y nuevo B1–B5.

## B5 — Auditoría previa adversarial / STOP
- La instancia **latch** fallos de host, ABA y sandbox: no revive al volver a A ni al primer plano; la sesión nueva obtiene nonce distinto. Pointer completo = ID + sequence, nunca solo ID.
- Cada solicitud de epoch compara principal técnico por sesión, dominio exacto, pinned pointer y estado vigente; cualquier error/exception DENY. Revocación emite invalidation para el observador, pero la invalidation NO sustituye la reválida antes/después de SQL. Se prohíbe autorizar desde worker, texto/modelo o intento de búsqueda; el futuro host debe verificar evento real.
- La interfaz `grantAfterTrustedForegroundReadAction` es una capacidad interna, no prueba por sí misma que una persona hizo clic. Si el host la invoca automáticamente, B5 de montaje debe STOP. Sin ruta de montaje, ningún dato se vuelve accesible.
- Negativas nuevas T2: no click; principal incorrecto; dominio no autorizado; revocación parcial; regrant con epoch diferente; background; host loss/recovery; pointer A→B→A; sandbox facts invalidados; close/restart; invalidation; no unión implícita N031.
- **READY TO IMPLEMENT solo CORE** con los dos paths anteriores y pruebas dirigidas. Estado productivo deliberadamente BLOCKED hasta probar Application-backed root+trusted UI gesture y Android. N031 total global sigue UNKNOWN si faltan fuentes/recurrencias.
