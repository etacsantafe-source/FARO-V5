# FARO V5 — S4 W01 B3B1B1B1B1B1A foreground SQL reconciliation B7 PASS scoped
Fecha Argentina: 2026-11-17. Unidad `S4_W01_B3B1B1B1B1B1A_FOREGROUND_SQL_RECONCILIATION_DEV`. Sólo código DEV **DORMANT**.

## B1 — Evidencia/permiso anterior exacto
Preauditoría B1–B5 `docs/v5/FARO_S4_W01_B3B1B1B1B1B1_FOREGROUND_RECONCILIATION_B1_B5_20261117.md`, guard `44131877296` SUCCESS. B5 independiente `docs/v5/FARO_S4_W01_B3B1B1B1B1B1A_FOREGROUND_RECONCILIATION_INDEPENDENT_B5_READY_20261117.md` blob `ac930b0ca0271f382d0329d88b769e48498877e3`, guard `44132100151` SUCCESS; READY permiso exact4 `b3e6ec06f3737eb11974bb5e8c9bd6846be17e97` y guard `44132275169` SUCCESS; parent checkpoint `c7bc4c462a5019e1bfc4ce901b7801ed6043d123` guard `44132329782` SUCCESS. Preflight remoto independiente B1–B5 hashes y SHAs PASS. No repetir B3B1B1B1B1A SQL revoker ni B3B1B1B1B1B0 READ biometric lifecycle.

## B2 — Diff funcional verificado
Commit funcional único `5ed88bdf6abb26fd97eca2216dac4ef71372db89`, mensaje incluye `FARO-UNIT:S4_W01_B3B1B1B1B1B1A_FOREGROUND_SQL_RECONCILIATION_DEV`. GitHub compare vs parent confirma EXACT4:
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadForegroundReconciliation.kt` nuevo.
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadForegroundLifecycleAdapter.kt` nuevo.
3. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceReadForegroundReconciliationTest.kt` nuevo.
4. `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1a-foreground-reconcile.yml` nuevo.
NO cambia `RealityStore`, `RealityFinance9Acl`, schemas, `AndroidManifest`, provider, Compose, shipping CompositionRoot, W2, V2 productiva, Apply, receipts, S2/S3/FBS2v4 o V4.4/main.

## B3 — Reconciliación DEV sin notificación ficticia
Reconciler mantiene serie monotónica, foreground, cleared y closed. Al ON_PAUSE/STOP invalida el estado antes de CLEAR; al ON_RESUME invalida y ejecuta CLEAR antes de marcar el nuevo intento apto para observación. `observedAfterResumeForDevOnly()` crea un NUEVO `S4W01B3B1B1B0ObservedReadFence` por cada llamada, que efectúa 2 observaciones SQL/ACL existentes; descarta resultados si hubo stop/notice/close concurrente. Un CLEAR fallido cierra permanentemente la instancia; no devuelve DTO.
Adapter `LifecycleEventObserver` en main responde ON_RESUME/ON_PAUSE/ON_STOP/ON_DESTROY y desregistra al cerrar, sin tocar pantalla ni autorizaciones. `onExternalSignal` es hook FUTURO y NO es ContentObserver real.
No existe lease ni cache de saldos: los DTO son informativos, y el host READ humano/activo debe ser verificado de nuevo por un owner futuro antes de render. Finanza no está montada en UI productiva.

## B4 — CI probada con XML y diff
- Guardia `44132877719` completed SUCCESS para commit funcional, con preflight y 14 autotests de protocolo.
- Workflow dirigido `44132878065`, job `134214437981`, mismo SHA `5ed88bdf6abb26fd97eca2216dac4ef71372db89`, COMPLETED SUCCESS.
- Parent exacto preflight `FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_B3B1B1B1B1B1A_FOREGROUND_SQL_RECONCILIATION_DEV c7bc4c462a5019e1bfc4ce901b7801ed6043d123`.
- Gradle `:app:testDebugUnitTest` 8/8 JUnit nuevos por XML exacto (names 8; failures/errors/skipped 0), `:app:compileV2CoreDevKotlin` BUILD SUCCESSFUL. Logs reportaron BUILD SUCCESSFUL in 3m41s y in 32s.
- Verificador de manifest sin provider, product CompositionRoot sin mount, adapter con callbacks de Lifecycle, mensajes:
  `W01_B3B1B1B1B1B1A_FOREGROUND_SQL_RECONCILE_8_OF_8_JVM_PASS`
  `W01_B3B1B1B1B1B1A_ANDROID_DEV_COMPILE_PASS_NO_SHIPPING_MOUNT`
  `LOST_EXTERNAL_SQL_NOTICE_SYNTHETIC_NOT_ANDROID_IPC_CERTIFICATION`.
- Artefacto XML run `44132878065`: `faro-v5-w01-b3b1b1b1b1b1a-foreground-junit`, ID `14350832103`, 795 bytes ZIP; no atribuir inspección ZIP independiente.
- Para mismo SHA: de 5 workflows totales, tres completos SUCCESS, dos pendientes durante auditoría; no declarar estado global 5/5.

## B5 — Auditoría final, alcance y riesgos
R01 no read previo a RESUME; R02 clear antes de fresh fence SQL por read; R03 PAUSE/STOP no DTO hasta RESUME; R04 revocación remota durante STOP SIN aviso sintetizada, al RESUME nuevo fresh fence retorna null; R05 ABA pointer fail closed; R06 globalPolicyRevision drift fail; R07 evento intercalado mientras se lee descarta DTO; R08 excepción CLEAR cierra owner sin resurrect. PASS 8/8 sólo dentro del ámbito JVM.
NO PASS ni afirmación: pruebas dos procesos Android, notificación `ContentResolver.notifyChange`, provider real, garantía que todos los dueños SQL anuncian, BiometricPrompt G15, eliminación de saldo en pantalla en tiempo real, human read authorization, cierre W1. Es posible un COMMIT de revocación después del segundo SELECT; un DTO nunca autoriza render. `v2CoreDev/AndroidManifest.xml` blob `97a23a12983f1a6e9ac973a4b4948142d477c439` tiene `<provider tools:node="removeAll" />`: agregar provider IPC requiere permiso exacto de otra unidad + revisión para conservar aislamiento. No saltar esta regla a la fuerza. Las señales Android son pistas, no verdad SQL; al RESUME se reconsulta por política duradera.
No regresión certificada del UI shipping; nunca se modificó.

## B6
IMPLEMENTED `5ed88bdf6abb26fd97eca2216dac4ef71372db89`; writer epoch1121 RELEASED antes de CI; 8 JUnit y Android compile SUCCESS.

## B7
**PASS_SCOPED_DEV_FOREGROUND_SQL_RECONCILIATION_8_JVM_ANDROID_COMPILE**. Consumir READY a BLOCKED con `last_code_review`, actualizar checkpoint y liberar writer. Próximo `B3B1B1B1B1B1B`: publisher DEV privado de cambios post-commit (manifest/proveedor/redirección correctos), scope B1–B5 nuevo, no autorizado aún. B3B2 UI/G15 todavía NOT_RUN. W2 STOP, V2 productiva OFF.
