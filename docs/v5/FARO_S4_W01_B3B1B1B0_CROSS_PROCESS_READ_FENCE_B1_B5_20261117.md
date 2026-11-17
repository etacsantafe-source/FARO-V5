# FARO V5 — S4 W01 B3B1B1B0 · preauditoría B1–B5, fence de lectura Finance9
Fecha Argentina: 2026-11-17. Rama `work/s3-night-20261102`; HEAD base `3c77192790bd7bad486ed1da99fbc405221acfcf`.
Unidad candidata: `S4_W01_B3B1B1B0_FINANCE9_STAGED_READ_SESSION_FENCE`.
Modo: DOCUMENTAL, no READY; B6 NO EJECUTADO. W2 STOP, V2 productiva OFF.

## B1 — Evidencia y brecha exacta
- B3B1B1A mapper inerte `S4W01B3B1Finance9UiMapper.kt` B7 PASS scoped, commit `78761e59d19b21fac9ec77beca71c736635a6e27`; CI `44091802896` SUCCESS 8/8 JUnit, 5/5 Actions; cierre `3c77192790bd7bad486ed1da99fbc405221acfcf` guard `44093331972` SUCCESS; writer RELEASED epoch1082. No duplicar pruebas.
- B3B1B0 Reality9 `finance9ObservedSourceReadForDevOnly` B7 PASS scoped: `RealityFinance9ObservedSourceRead` permite observar policy + fuente en una sola transacción SQLite WAL bajo schema9 STAGED; NO autentica identidad, UID ni vigencia después del snapshot. `RealityFinance9ReadPolicyObservationRequest` acepta `principalHash`, `generationId`, `pointerSequence`, `expectedPolicyEpoch` y tiempo suministrados por caller.
- `FileActiveGenerationPointer.read()` contiene tanto generation ID como sequence; `addChangeListener` sólo notifica escrituras del mismo proceso. `FaroV5HomeReadinessInvalidations.from` sólo cubre readiness/aux/pointer local, nunca revocación SQL interproceso de Finance.
- `FaroV5AndroidSandboxPrincipal` y `S4W01FinanceAuthHost` disponen de mecanismos Android de sandbox, Keystore y consentimiento en DEV, pero la última certificación M1C1B1C es sólo de grant para Apply, no `finance.snapshot.read`. No hay issuer de consentimiento de LECTURA ni emisión READ verificada en Android; el principal de prueba del SQLite observer es falsificable.
- `FaroV5FinanceReadPort` requiere `invalidations: Flow<Unit>` y `readFinance()`, pero B3A está DORMANT y no vinculada a Activity/CompositionRoot.
Brecha concreta: hace falta el host autenticado/read policy issuer, una comprobación de vigencia contra revocación entre procesos y un mecanismo de invalidaciones con revalidación durable. Un simple mapper o callback `admitted()=true` no sirve.

## B2 — Reutilización y comparación V4.4
Reutilizar B3B1B0 exact-snapshot ACL/source, B3B1B1A mapper, M1B sandbox+Keystore, M1A ACL epoch monotónico, full `ActiveGenerationPointer`, host epoch/foreground owner y política de borrado de UI B3A. Mantener invariantes canónicos V4.4: un único escritor, receipts, startup recovery, ningún re-Apply. No copiar credenciales o consentimientos históricos de V4.4. No editar schema7/8, FBS2v4, catálogo180, S2, S3, main, Android product root, ni Finance Apply.

## B3 — Investigación externa con selección
- SQLite `https://www.sqlite.org/isolation.html`: ADOPTAR snapshot consistente de la misma transacción, pero una lectura posterior a COMMIT/ROLLBACK no equivale a un lease indefinido; revocación después del snapshot no puede detectarse sin consulta nueva.
- SQLite snapshot `https://sqlite.org/c3ref/snapshot.html`: REFERENCIA, no usar versión histórica de WAL para revalidar permisos actuales.
- Android FileObserver `https://developer.android.com/reference/android/os/FileObserver`: ADAPTAR solamente como señal de modificación, nunca como autoridad de emisión o garantía de entrega; su callback puede retrasarse o perderse al cerrar/reiniciar.
- Android ContentResolver notifyChange / ContentObserver `https://developer.android.com/reference/android/content/ContentResolver`: alternativa para notificación entre procesos, sólo si un owner de escritura real notifica a TODAS las rutas de revoke/cutover/restauración; no existe ese contrato actualmente.
- Lifecycle `https://developer.android.com/guide/components/activities/activity-lifecycle`: ADOPTAR limpieza en STOP y revalidación al RESUME, no asumir que la pantalla sigue autorizada tras ir al fondo.
- DESCARTAR: FileObserver sin reread, eventos `Flow` falsos, `admitted()=true`, polling UI con saldo visible entre verificaciones, usar grant `finance.account.create` como permiso `finance.snapshot.read`, tratar `CONFIRMED_IN_SQL` como firma humana. Sin APIs externas ni dependencia nueva.

## B4 — División mínima sin fingir autorización
**B3B1B1B0 (candidato aislado):** port puramente STAGED/DEV que ejecuta DOS lecturas compuestas completas por `RealityFinance9ObservedSourceReadPort` con el mismo request exacto y compara igualdad de policy epoch, revisión global ACL y opening (cuentas, importes y sourceRevision), revalidando full pointer y host epoch entre lecturas y antes de retornar. Detecta revoke/cutover ABA ocurrido ENTRE ambas observaciones. La fuente de principal/epoch sería inyectable para tests, NO válida como root of trust. El resultado seguiría siendo `RealityFinance9ObservedSourceRead?` para desarrollo, nunca `FaroV5FinanceReadPort`, ni un grant para UI. Sin escritura ni cache persistente.
Rutas candidatas exact3 (aún NO autorizadas):
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B3B1B1B0ObservedReadFence.kt` NUEVO, internal; sin Android UI ni Activity.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B3B1B1B0ObservedReadFenceTest.kt` NUEVO, mínimo ocho tests reales de política de doble snapshot con fuentes sintéticas.
3. `.github/workflows/faro-v5-s4-w01-b3b1b1b0-fence-directed.yml` NUEVO, JUnit exact + compilación variante aislada DEV y ausencia de shipping wiring.
**B3B1B1B1 (posterior):** host de Android con principal técnico de Keystore/UID obtenido exclusivamente por Application/host real, issuer de `finance.snapshot.read` independiente del grant de Apply, pin de generación+secuencia y host epoch; evento de revocación de todos los procesos con reconocimiento de misses al RESUME, y B3A UI `Flow` que limpia cuenta al invalidar. Requiere su propia preauditoría, B5 independiente, permiso y pruebas G15 cuando proceda.
**B3B2**: montaje Android aislado, pruebas hardware reales, nunca shipping anticipado.

## B5 — Auditoría adversarial y límites (NO READY)
- O01 ausencia policy/scope o epochs inválidos => null; no financiar permiso por datos fuente.
- O02 revocación después de primer snapshot => segunda lectura nueva debe denegar.
- O03 ABA de pointer con mismo generationId pero distinta sequence => denegar; no basta comparar nombre.
- O04 host suspendido/cerrado/epoch cambiado antes, durante o después => denegar.
- O05 discrepancia de `globalPolicyRevision`, opening sourceRevision o cuentas => denegar, no mezclar lecturas.
- O06 excepción del port, reloj inválido, expiración y null => denegar; no retener cuentas.
- O07 dos observaciones iguales sólo prueban estado en dos instantes; **NO** prueban que un writer remoto no revoque después del segundo snapshot. No otorgar READY a Surface. Ningún test sintético certificará firma/biometría.
- O08 asegurar cero side effects, cero Apply/grant, cero manifest/Activity/CompositionRoot/Chat/Navigation, ninguna API paga y W2 STOP.
Contradicción explícita resuelta: DOUBLE READ + revisión de pointer es un **fence acotado contra carreras observadas**, no un protocolo de revocación cero-latencia. La garantía dura de no mostrar saldo revocado requiere diseñar B3B1B1B1 y evaluar una fuente de señal de autoridad interproceso. No es legítimo afirmar que FileObserver o polling elimina por completo esa ventana.
Veredicto de la preauditoría: **PASS DOCUMENTAL PARA AUDITORÍA INDEPENDIENTE B3B1B1B0 EXCLUSIVAMENTE**. B3B1B1B1 y B3B2 PRODUCTIVOS STOP. Código aún prohibido hasta B5 independiente, READY exact3 y preflight.

## B6
NOT_RUN. Sin permiso funcional. Ningún caso nuevo ejecutado en esta etapa.
## B7
PENDING. Debe confirmar SHA, diff exact3, XML real y writer RELEASED; no extender PASS de B3B1B1A/B3B1B0.
