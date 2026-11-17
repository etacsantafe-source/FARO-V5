# FARO V5 — W01 M1A · Finance9 durable ACL, stage9 SQL solamente · B1–B5

**Fecha local:** 16/11/2026, snapshot de trabajo `ebb8ae17414c7ce248730e53f5d43aa71891fdad`. **Unidad:** `S4_W01_M1A_FINANCE9_DURABLE_ACL_STAGE_SQL`. Fase DOC_ONLY, código NOT_RUN, permiso previo M0C2B2B BLOCKED consumido, writer anterior RELEASED epoch1006, guard de cierre M0C2B2B `43997013271` SUCCESS. **W2 STOP.**

## B1 — Recuperación de evidencia real
- `RealityFinance9Schema.kt` instala **schema9 STAGED-only** con `finance_acl_state(next_policy_epoch,policy_revision)`, fila inicial `(1,1,0)`, `finance_acl_policy` (principal_hash/generation_id/pointer_sequence/operation_id/object_scope/epoch/revoked/expires_at_ms) y `finance_apply_grant` (fingerprint/intent_key/receipt_id, FK de policy y receipt), pero NO existe `RealityFinance9Acl.kt` ni un protocolo ejecutable de issue/revoke/consume/commit.
- `S4W01B2FinanceObjectGrantOwner.kt` contiene `synchronized` y nonce en RAM que **no excluyen segundos procesos**; `S4W01B2C0FinanceDevApplyHost.kt` usa política DEV inyectada, explícitamente no autentica humanos ni garantiza revoke antes de COMMIT.
- `FaroV5AndroidSandboxPrincipal.kt` sólo acredita UID/sandbox OS como evidencia técnica; NO identifica a un humano, ni consentimiento, ni da permisos Finance.
- `RealityStore.run` usa BEGIN IMMEDIATE/COMMIT y durable receipts, pero todavía no revalida `finance_acl_policy` en la misma transacción productiva. Exponer Finance Apply aquí violaría el contrato.
- M0C2B2B FBS2v4 **B7 PASS scoped** `b67a06c87a66551dd05b03afe0058a5e1a7e11fc`, CI 10/10, 6 regresiones SUCCESS; Finance ACL/grants excluidos del backup. Reutilizar, NO repetir.
- Documentos fuente: `FARO_S4_W01_ARCH02_ARCH06_MONOTONIC_EPOCH_PATH_FIX_20261116.md`; `FARO_S4_W01_B2_C1_SCHEMA_AND_REVOCATION_COMPATIBILITY_MATRIX_20261116.md`; `FARO_S4_W01_ARCH03_DDL_ARCH06_EXACT_PATHS_20261116.md`; matriz W01.

## B2 — Reuso vs V4.4 y propiedad
- NO añadir nuevo contrato catálogo ni esquema7/8; conservar `finance_acl_state` versionada en **un único Reality9 STAGED** y receipts PREPARED/COMMITTED. V4.4 no contiene ACL Finance durable: no trasplantar grant visual o permisos Chat.
- Partir M1, sin adelantar autenticación humana, en: **M1A** política SQL9 stage-only con epoch durable y revocación transaccional; **M1B** principal local Android real, Keystore/consentimiento verificable, instrumentation y G15; **M1C** host autorizado al COMMIT con tests **dos procesos reales** y acoplamiento al mismo Reality Apply. Cada subunidad recibe su propio B1–B7 y READY exacto. NO afirmar M1 global por PASS M1A.
- M1A expone internamente sólo `policyIssueAfterExternalTrustedAdmission`, `revoke` y `checkCurrent` con una conexión SQLite preadmitida por owner; no puente a UI/Chat/App/Apply shipping. La prueba JVM no equivale a autoridad de un usuario.

## B3 — Fuentes públicas y opciones consideradas
- SQLite WAL (https://www.sqlite.org/wal.html), Isolation (https://www.sqlite.org/isolation.html) y Transaction (https://www.sqlite.org/lang_transaction.html): **ADOPTAR** BEGIN IMMEDIATE para serializar políticas y monotonía en Reality; las lecturas WAL son snapshots y un monitor de RAM no detiene otro proceso. No usar dos archivos SQLite para un COMMIT atómico.
- Android security (https://developer.android.com/agents/skills/security/android-permissions-security/skill): **ADAPTAR en M1B/C** verificación OS de proceso/caller en el contexto confiable, nunca nombre de paquete provisto por IA/Intent. Android Keystore + consentimiento real debe diseñarse y comprobarse en Android, no fingirse en un test JVM.
- Alternativas RECHAZADAS: counters en RAM/reutilizables al reinicio, fecha reloj como epoch, grant Workspace APPROVED como permiso, token de IA, autorización solo antes de SQL, exportar ACL por FBS2v4, activar V2 productiva o añadir APIs pagas.

## B4 — Alcance y permiso futuro M1A independiente
Paths exactos candidatos, **no autorizados todavía** hasta guard documental y READY en otro commit:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Acl.kt` (NEW, sólo SQL9 staged).
2. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9AclStage9Test.kt` (NEW, JVM SQLite bundled real).
3. `.github/workflows/faro-v5-s4-w01-m1a-finance9-acl-stage-directed.yml` (NEW, JVM JUnit XML exacto).
A1 schema9, FK y `finance_acl_state` EXACT antes de mutación; A2 issue asigna epoch global monotónico y policy_revision, rechaza overflow; A3 revoke cambia flag/epoch, NO DELETE; A4 revoke→regrant del mismo scope no recicla epoch; A5 dos conexiones de una misma DB intercaladas ven orden serializable y nunca false success; A6 falta policy, principal/generación/pointer/scope ajenos y expiración fail-closed; A7 fallo inyectado antes de COMMIT hace rollback sin saltos ni grants. `M1B/M1C` exigen separadamente dos procesos reales + Android.
No permisos de código para `RealityStore.kt`, `app` o Android host en M1A; si se necesita ruta adicional, STOP y nuevo B1–B5.

## B5 — Riesgos adversariales
- Guard `finance_acl_state`: nunca resembrar `(1,1,0)` tras abrir; no borrar ACL policy al revocar ni consumir grant fuera de la misma SQLite; `Math.addExact`/LONG overflow → STOP. Mantener `policy_revision` sin wrap.
- Principal_hash 64 hex y scope/cuenta/operation/generation deben venir de trusted external host. M1A acepta datos tipados desde harness de prueba, no fabrica un principal real ni verifica huella biométrica.
- Dos conexiones **no** equivalen a dos OS procesos. M1C debe poner revoke y el último check/pago del mismo `BEGIN IMMEDIATE` con interleaving externo real. M1A sólo certifica gramática SQL, monotonía y lectura del segundo connection.
- Backup/restauración anterior excluye policy/grants; una nueva generación jamás conserva authority. M1A no activa generation, no modifica active pointer ni shipping, no imprime credenciales ni crea dinero real.
- Si falla A1–A7, **B7 BLOCKED**, preferir RED_FIX bounded sin relajar tests; no mezclar W01-B/C/D/E/F ni W02.

## B6
NOT_RUN. Emitir guard documental en el HEAD de este plan y, si SUCCESS, permiso READY M1A exact-3 en commit separado, adquirir writer CAS nuevo y hacer sólo código M1A.

## B7
PENDING. Verificar JUnit XML A1–A7, diff exact3, guard sobre SHA funcional, y writer RELEASED. M1B/C y las operaciones W01 restantes quedan OPEN/STOP de producto.
