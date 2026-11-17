# FARO V5 W01 B3B1B1B1B — Trusted Android READ host and cross-process revocation B1–B5
Fecha AR: 2026-11-17. HEAD de base 82c35c49734cd210eb2b1a7e31ab674750f09f36. Modo DOC_ONLY, sin READY ni B6.
El B7 del issuer SQL B3B1B1B1A sigue bloqueado por la CI histórica 44110607195, con 22/23 SUCCESS sobre code 8a4d79ca3a27e5d312345df0a1c50503749487e0. Esta preauditoría no sustituye su cierre ni repite certificaciones.

## B1 — Evidencia observada
- RealityFinance9TrustedReadPolicyIssuerPort.kt y RealityStore.finance9TrustedReadPolicyIssuerForDevOnly emiten exclusivamente policy SQL finance.snapshot.read/finance:all, TTL hasta 30s, sin Apply grants. La request es construible por tests: no firma biométrica, no origen UID autenticado.
- S4W01FinanceAuthHost.kt contiene BiometricPrompt.CryptoObject ECDSA AndroidKeyStore auth-per-use, pero el camino M1C1B1C existente obliga Review APPROVED y emite un grant para Apply, no READ.
- FaroV5AndroidSandboxPrincipal.kt comprueba Process.myUid, Application.uid, sessionNonce, generationId+pointerSequence y hostEpoch, sin afirmar identidad humana.
- RealityFinance9ObservedSourceReadPort.kt y B3B1B1B0ObservedReadFence.kt comparan policy y source en snapshots SQL, pero una revocación posterior al segundo SELECT puede hacerlos obsoletos.
- FaroV5FinanceReadSurface.kt exige invalidations Flow<Unit>, borra value=null en respuesta al flujo; no recibe por sí mismo revocaciones remotas. FileActiveGenerationPointer.addChangeListener y FaroV5HomeReadinessInvalidations operan sólo dentro de proceso.
- v5/app/src/main/AndroidManifest.xml NO contiene provider propio para señales de ACL Finance9. InitializationProvider de androidx.startup NO es un canal de revocación.

## B2 — Reutilización sin contaminar
Mantener el writer único, recuperación, receipts y ApplyPlan del núcleo V4.4, pero NO copiar ni ampliar permisos Apply para Finance READ. Se reutilizan el hash de principal derivado de la llave publica Keystore y UID reales, el full pointer+sequence, host epoch, el policy issuer SQL DEV, WAL snapshot, y el port Compose DORMANT. No tocar V4.4, main, S2/S3, FBS2v4, W2, ni shipping.

## B3 — Selección técnica y referencias oficiales
A — SELECCIONAR un desafio READ exclusivamente separado de Apply: dominio versionado, nonce aleatorio, operation finance.snapshot.read, scope finance:all, appId+UID del Application real, full pointer generationId+sequence, hostEpoch, expiración y texto humano “Consultar mis cuentas y saldos”. Firma ECDSA en callback real de BiometricPrompt.CryptoObject, usando alias Keystore exclusivo READ para evitar aceptación cruzada de firmas Apply. Comprobar full fence y firma antes y después del SQL issuer; respuesta no persistida y sin grant Apply.
B — RECHAZAR reuso de requestTrustedStageGrantAfterForegroundReview con un falso ApplyPlan/fingerprint: no equivale a consentimiento para leer y podría crear finance_apply_grant.
C — INVESTIGAR canal remoto ContentResolver.notifyChange y ContentObserver sobre ContentProvider app-private no exportado, pero solo si todas las rutas reales de revoke, restore, switch y rotacion emiten notificaciones después de commit. Evento no concede permiso ni garantiza entrega; al RESUME es obligatorio revalidar SQL/pointer/epoch.
D — RECHAZAR FileObserver solo, callbackFlow solo, polling periódico con balances que permanezcan visibles, o un isAuthorized boolean sintético.
Refs: https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject ; https://developer.android.com/reference/android/content/ContentResolver ; https://developer.android.com/reference/android/database/ContentObserver ; https://developer.android.com/topic/libraries/architecture/views/coroutines-views ; https://www.sqlite.org/isolation.html .

## B4 — Secuencia mínima sin READY prematuro
B3B1B1B1B0 — host auth READ DEV aislado. Candidatos nuevos: S4W01FinanceReadConsentScope.kt, S4W01FinanceReadAuthHost.kt, S4W01FinanceReadAuthPolicyTest.kt y workflow dirigido. Firmar desafio específico READ con Keystore/per-use, verificar UID de Application, hostEpoch y full pointer, consentimiento foreground. Llamar issuer B3B1B1B1A SOLO dentro de callback auténtico, luego descartar challenge; no Activity productiva, no Surface/CompositionRoot/Chat. Test JVM policy es sintético, compilar Android DEV; hardware G15 pendiente. Requiere B5 independiente, READY propio y gate.
B3B1B1B1B1 — canal real de revocación, host lifecycle y limpiado UI. Auditar TODAS las rutas SQL issue/revoke, restores/pointer switches, demostrar publisher tras commit y registrar ContentProvider/ContentObserver solo si hace falta; reconciliar en RESUME y al iniciar cada lectura, borrar saldos al background, cierre o evento. Pruebas de varias instancias/procesos y G15 antes de certificar.
B3B2 — integración host y pantalla DEV aislada, después de B7 de estas subunidades y prueba hardware autorizada; V2 productiva OFF.

## B5 — Auditoría adversarial preliminar
- Los DTO de READ policy y hash de llave/UID son falsificables en tests y no prueban presencia humana; sin BioPrompt real y binding no hay autenticación.
- Firmas Apply anteriores NO autorizan READ: separar dominio/alias, denegar replay, wrong scope, UID aislado, key invalidada, falta de foreground y epoch ABA.
- Dos lecturas SQL y las notificaciones no eliminan el TOCTOU de revoke tras última SELECT. ContentObserver y FileObserver son señales, no autoridad ni lease de render cero-latencia. No prometer saldo siempre actualizado si un writer remoto puede cambiarlo sin que el proceso lo sepa.
- Una ruta de revoke/cutover sin publisher impide certificar el port FinanceReadPort. Limpieza en STOP/background y revalidación al RESUME, incluso si no hay evento recibido.
- Pruebas JVM sintéticas NO certifican Android biométrico. B3B2/G15 NO RUN. No hay pagos reales, no API paga.
- No alterar trabajo previamente certificado, W2 STOP, V2 productiva OFF.
DICTAMEN B5: PASS DOCUMENTAL PARA ANALIZAR EL HOST DEV EN SUBUNIDAD B3B1B1B1B0. NO READY, NO PERMISO DE CODIGO hasta que cierre B3B1B1B1A B7 y se efectúe B5 independiente exacto.

## B6
NOT_RUN. No se edita código funcional ni rutas de manifest.
## B7
PENDING. No certificar consentimiento, revocación ni pantalla shipping.
