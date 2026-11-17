# FARO S4 W01 B3B1B1B1B1B0 — Independiente B5 + permiso exact4 READ host lifecycle DEV
Fecha Argentina 2026-11-17. Base preaudit `3d08b29c17270964930d49054b92aa9d19d18576`, guard `44128012958` SUCCESS, documento `docs/v5/FARO_S4_W01_B3B1B1B1B1B_ANDROID_INVALIDATION_LIFECYCLE_B1_B5_20261117.md` blob `c77611ee95cbca36d2c02242f6cd972dfd6af5f9`. ESTE documento es DOC_ONLY. No cambiar códigos ni proveer permiso de lectura productiva.

## B1 — Pruebas de estado real
`S4W01FinanceReadAuthHost.kt` blob `048177044305c53639d7fb98945e39865ebf64fc` ya dispone de `close()` que cancela `BiometricPrompt`, cierra gate y sandbox, y resuelve callback máximo una vez; pero no observa `ON_STOP` y no hace limpieza automática de consentimiento pendiente al abandonar foreground. Esta unidad implementa **solamente** el cierre por lifecycle, sin UI ni datos financieros. `FaroV5FinanceReadSurface` blob `f166cde78466c6935a25423877b484d72dfd277d` sigue DORMANT sin readPort productivo. No hay publisher Android de SQLite.
El padre SQL READ revoke `80e46887d8b46f7030fbbe4fc9c5bc93a93cb6c9` es B7 PASS scoped 24/24 XML en `44125813960`, guard final `44126871792` SUCCESS. No repetirlo.

## B2 — Reutilización y alcance
USAR el `close()` idempotente del host; conectar `LifecycleEventObserver` `ON_STOP` y `ON_DESTROY` para cancelar prompt pendiente. Un seam Kotlin puro `S4W01FinanceReadSessionLifecycle` mantiene el cierre **irreversible** y atómico; un resume nunca reabre. Un observer Android solamente traduce eventos y un test JVM verifica la política; la compilación de variante DEV comprueba el wiring Android.
NO tocar `FaroV5FinanceReadSurface.kt`, `FaroV5CompositionRoot.java`, manifest, schema, SQLite, global writer, V4.4/main/S2/S3, App V2 productiva, Work/Noche ni Apply. No certificar paridad de UI V4.4 donde no existe feature equivalente.

## B3 — Opciones y fuentes
A: agregar observer al host DEV existente + gate puro one-shot; **SELECCIONAR/ADAPTAR** (mínimo código y pruebas). B: implementar cancelación `ON_STOP` dentro de `FaroV5FinanceReadSurface` mediante `LifecycleStartEffect` ahora; **RECHAZAR** porque no hay readPort/host productivo montado y adelantaría UI no autorizada. C: `ContentObserver` como reemplazo de cleanup; **RECHAZAR** porque notificaciones no son lifecycle ni cubren escritura SQLite cruda. Documentación primaria: https://developer.android.com/topic/libraries/architecture/lifecycle/ y https://developer.android.com/reference/android/content/ContentResolver y https://developer.android.com/reference/android/database/ContentObserver. SQLite WAL https://www.sqlite.org/isolation.html .
Seam sin nuevas bibliotecas externas ni API paga.

## B4 — EXACT4, límite de permiso
Unit `S4_W01_B3B1B1B1B1B0_DEV_READ_HOST_LIFECYCLE_ABORT`:
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadSessionLifecycle.kt` NUEVO: estado booleano irreversible de cancelación, callback de terminalización exact once, consulta `isOpen` que nunca reviva por `ON_RESUME`, cierre explícito silencioso.
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadAuthHost.kt` EXISTENTE: registrar `LifecycleEventObserver` y desregistrar siempre al acabar; `ON_STOP` y `ON_DESTROY` llaman al cierre del host; control de main thread para objetos Android; no cambiar firma del issuer ni consent.
3. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceReadSessionLifecycleTest.kt` NUEVO: 8 casos Kotlin JVM de contrato one-shot y carrera, sin credencial humana o G15.
4. `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b0-lifecycle-directed.yml` NUEVO: preflight en `HEAD^`, JUnit 8 XML, `:app:compileV2CoreDevKotlin`, static check que el host usa ON_STOP/ON_DESTROY y nada monta shipping. Scope nunca incluye manifest/provider.

## B5 — Fallos adversariales, criterios binarios
- **R01** STOP invoca terminador una vez y envenena sesión; **R02** DESTROY idéntico; **R03** cierre explícito no ejecuta callback de STOP; **R04** STOP+DESTROY+STOP no duplica; **R05** RESUME no reactiva; **R06** carreras concurrentes STOP/DESTROY devuelven un solo callback; **R07** excepción de terminador mantiene estado cerrado; **R08** close antes de STOP y luego STOP no disparan ni resucitan autorización.
- En host, `close()` ante STOP limpia gate/callback/sandbox y cancela prompt. Antes de `authenticate`/aceptar respuesta, reread foreground y `isOpen`; un callback retrasado post-STOP no debe emitir policy. Desregistrar observer para no retener Activity. Todas operaciones lifecycle/biometric en hilo principal; un close externo a main se debe entregar al main con estado cerrado inmediato.
- **NO** representar este gate como una lease de render, cronómetro de caducidad ni invalidación de SQLite. No afirmar que saldos previamente mostrados se limpian; B3B1B1B1B1B1 publisher+readPort y B3B2 G15 siguen separados. Una cuenta revocada tras último SELECT aún puede verse: no solucionado.
- Las ocho pruebas JVM nunca equivalen a hardware Android. El workflow de compile DEV no autoriza producto.

**DICTAMEN INDEPENDIENTE:** `PASS_SCOPED_PRECODE_ONLY` para exactamente cuatro rutas de cierre de prompt host READ DEV; sujeto a guard y preflight. NO READY para publisher, manifest, UI Compose, integración Finance, pagos o V2 productiva.

## B6
`READY_TO_IMPLEMENT` SÓLO tras commit documental independiente de `FARO_PROTOCOL_WORK_PERMIT.json` con B1–B5 verificados por este blob, guard CI verde y exact four paths; luego preflight de HEAD^ con un writer CAS nuevo, y B6. Este documento no ejecuta código.

## B7
`PENDING_FINAL_AUDIT`: comprobar diff exact4, guard exact SHA, prueba 8/8 XML, compile DEV Android, callback cleanup fail closed, writer RELEASED, checkpoint; de lo contrario RED_FIX de la misma unidad.
