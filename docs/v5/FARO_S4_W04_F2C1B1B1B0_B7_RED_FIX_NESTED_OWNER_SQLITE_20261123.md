# FARO V5 — F2C1B1B1B0 · B7 RED_FIX 1 · recursión SQLite en scope Household

Fecha: 2026-11-23. Unidad `S4_W04_F2C1B1B1B0_LEASED_OWNER_REAL_SOURCE_DEV`. Código `ce97438755eabcd0fb868d31673b5df68cfefb5b`.
**CI roja confirmada. No B7 PASS hasta XML verde.**

## B7 — Evidencia roja y diagnóstico
- Preauditoría B1–B5 `docs/v5/FARO_S4_W04_F2C1B1B1B0_LEASED_OWNER_SOURCE_B1_B5_20261123.md`, blob `b95c3a20a64625f368c6eb83e7b30ef1f25a96f3`. Guardia `45191915994` SUCCESS.
- Permiso READY exact4 desde `ee7a740a4792b936ca295f5db72eefe526cbbba8`, guard `45192202241` SUCCESS.
- Primer código `ce97438755eabcd0fb868d31673b5df68cfefb5b`, guard `45193029627` SUCCESS.
- GitHub CI dirigida `45193030044`, job `137756090666`, **FAILURE**. Compilación `:store:generation:compileTestKotlin` completada; `t07`, `t08`, `t09` y `t10`: 4/4 fallaron. JUnit XML archivado artifact `14826568341`, ZIP SHA256 `6323574a6e01d610cdde7bcd4e20832da9c494804f80ae3258a9a799fcf55442`. t07/t08/t09 `Required value was null` al intentar leer; t10 primera afirmación positiva `expected true but was false`. No se obtuvo ninguna lectura positiva.
- Diagnóstico de código (hipótesis fuerte aún no verificada por nuevo CI): F2C0 `readSelected` llama `session.readNeeds { admitted(...) }`; `RealityStore.household10ActiveDevReadForTrustedHost` realiza chequeo del callback **dentro** de `BEGIN DEFERRED TRANSACTION`. `admitted` invoca `Household10DevReadScopeOwner.admitted` → `verifiedRow` → `source` → **otra `session.readNeeds` sobre la misma conexión**. El segundo `BEGIN DEFERRED TRANSACTION` puede fallar por transacción anidada y queda enmascarado por `runCatching`. La lectura de la fixture funciona en F2C0 previa; el rechazo aparece al activar owner policy que revisita el origen.
- El origen debe confirmarse con CI real verde después de una corrección limitada, nunca asegurar PASS ni relativizar 4 fallos.

## RED_FIX exacto y restricción de seguridad
Modificar **una sola ruta ya permitida**: `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10Evidence2DevSelectionReadPort.kt`.
- Nuevo factory OWNER_BACKED pasa callback adicional `sourceReadAdmissionWithoutOwnerRecursion=::selectedByHost`; SOLO se usa en el callback dentro de la transacción de Household.
- En `readSelected`, verificar ambos grants exactos ANTES de `session.readNeeds`, mantener `lease/host/puntero/foreground/consent` durante el `BEGIN DEFERRED` con callback sin reentrada, y verificar **nuevamente ambas políticas** después de salir de transacción y después del Evidence snapshot. No devolver datos si se revoca cualquiera entre las verificaciones. El F2C0 antiguo conserva su callback original `admitted`, intacto para código ya certificado.
- Se preserva SINGLE lease y NO nueva base ni datos sintéticos en host. No tocar RealityStore/propiedad de transacción SQL ni los dos owners de F2C1B1B1A. No reejecutar t01–t06. CI dirigida debería correr solo t07–t10 y exigir XML 4/4 sin error/skip.
- En B7 revisar si callbacks pueden mutar epoch durante la transacción, si el permiso resucita tras ABA y si la ruta antigua cambia por accidente. Si CI falla, STOP y nueva evidencia RED_FIX.

La autorización Android F2C1B1B1B1 sigue sin implementar; no declarar V2 productiva habilitada, red/Apply/pagos/stock/G15 activos.
