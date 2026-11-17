# FARO V5 — W01 B3B1B1B1A · Política de lectura exclusiva de Finance9 (B1–B5)
Fecha AR: 2026-11-17. HEAD auditado `2a2d4c100b44adf732d2a529b7ca5806d36d01d1`. Rama `work/s3-night-20261102`. **DOC_ONLY — SIN READY/SIN CÓDIGO.**
El padre B3B1B1B1 requiere autenticación Android, issuer de READ y revocación interproceso. Esta subunidad A solamente construirá un emisor **SQL STAGED9 aislado** y tipado, SIN autenticar Android, consentir usuario, emitir Apply grant, ni montar UI.

## B1 — Evidencia concreta del HEAD
- B3B1B1B0 doble observación B7 PASS acotado en `2a2d4c100b44adf732d2a529b7ca5806d36d01d1`, código `3216747249fec29f79ce6ba35b3ba0b6c793282f`, 8/8 JVM, 5/5 Actions SUCCESS. No repetir.
- B3B1B0 `RealityStore.finance9ObservedSourceReadForDevOnly()`: consulta `RealityFinance9ReadPolicyObservationPort.OPERATION_ID=`finance.snapshot.read``, `OBJECT_SCOPE=`finance:all`` en la misma transacción WAL que el opening/ledger; niega si policy ausente/revocado/vencido, epoch distinto o schema !=9. Su request trae principal/epoch/hora forjables por caller; NO certifica usuario.
- `RealityFinance9Acl.issueAfterTrustedStageAdmission(key,issued,expires)`: emite una FILA durable `finance_acl_policy` con `policy_epoch` monotónico en BEGIN IMMEDIATE. No inserta grant. `RealityFinance9Acl.revoke(key)` incrementa epoch y marca revocado en BEGIN IMMEDIATE. El emisor existente `RealityStore.finance9TrustedStageGrantIssuerForDevOnly()` agrega un `finance_apply_grant` para acciones Apply: **no usarlo para emitir derechos de lectura**.
- `S4W01FinanceAuthHost` utiliza BiometricPrompt.CryptoObject+AndroidKeyStore, pero sólo su API M1C1B1C emite grant de Apply luego del Review APPROVED, no consentimiento `finance.snapshot.read`. `FaroV5AndroidSandboxPrincipal` valida UID y owner, sin acreditar identidad humana.
- `FileActiveGenerationPointer.addChangeListener` y Home invalidations son **intra-proceso**, y `FaroV5FinanceReadPort` no está montado en shipping. La solución no puede habilitar pantalla.

## B2 — Comparación V4.4 y reutilización
Reusar `RealityFinance9Acl` y schema9 sin DDL, `RealityStore` stage9 owner y `withMaterialAdmission`, DTO de read-policy, observación B3B1B0 y reconciliación B3B1B1B0. Mantener invariantes del núcleo V4.4 (AtomicApply/receipts, recuperación, idempotencia) sin copiar permisos o valores ni cambiar V4.4, main, S2/S3, FBS2v4, Apply ni product V2. La lectura es local, no mueve dinero.

## B3 — Alternativas, investigación y selección
A (SELECCIONADA): emisor exclusivo `finance.snapshot.read`+`finance:all` que sólo hace `RealityFinance9Acl.issueAfterTrustedStageAdmission` bajo dueño STAGED9, TTL corto <=30s, counter SQL monotónico. DTO de solicitud no tiene campos arbitrarios operationId/scope; no se permite emitir grants Apply ni receipts. Ningún acceso UI aunque el emisor devuelva epoch.
B (DESCARTADA): reutilizar `RealityFinance9TrustedStageGrantIssuerPort` de Apply con operationId read, porque crea `finance_apply_grant` y mezcla autorización de modificación con lectura.
C (DESCARTADA): fabricar `principalHash` desde texto de Chat, un ID de Intent, check de firma como boolean, o `admitted()=true`. El issuer puro nunca autentica Android. Futuro B3B1B1B1B debe enlazar callback auténtico CryptoObject y principal UID/Keystore, y revocación entre procesos.
Referencias: SQLite https://www.sqlite.org/isolation.html (instantánea + transacciones), Android CryptoObject https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject (auth-per-use) y Android ContentResolver https://developer.android.com/reference/android/content/ContentResolver (notificaciones entre procesos requieren contrato real; no reemplazan ACL). No API paga ni red.

## B4 — Partición y plan de ejecución
**B3B1B1B1A**: SQL policy READ STAGED9 bounded. Exact cuatro rutas futuras, aún NO READY:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyIssuerPort.kt` NEW. `Finance9TrustedReadPolicyIssueRequest` (principalHash hex64, generationId no vacía, pointerSequence>0, issuedAtMs>=0, expires>issued, diff<=30000), `Finance9ReadPolicyIssueRecord` (principal/identity/epoch/expiry; NO auth ticket), fun interface issuer `issueAfterExternallyVerifiedReadAdmission` typed. OPERATION_ID y OBJECT_SCOPE son FIXED de `RealityFinance9ReadPolicyObservationPort`, imposible sustituir con Apply.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` EXTEND con fábrica `finance9TrustedReadPolicyIssuerForDevOnly()` únicamente STAGED9. `withMaterialAdmission { synchronized(lock) { ... } }`, `checkOpen`, `financeStage9 && planningStage8 && schemaVersion == 9`, `activeTransactionId == null`, llama `RealityFinance9Acl.issueAfterTrustedStageAdmission` con key FIXED READ; no invoca método de grant, Apply/receipt ni modifica Ledger. Return null ante cerrado/corrupto/owner ocupado.
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyIssuerTest.kt` NEW. 8 JUnit SQLite reales: schema7/8 deny; policy READ emitida sin Apply grant; reader B3B1B0 la observa; foreign scope/principal/sequence deny; expiry boundary y TTL >30s; renew epoch+ABA old deny; revoke externo old deny; cerrado/side effects/ausencia de receipts.
4. `.github/workflows/faro-v5-s4-w01-b3b1b1b1a-read-issuer-directed.yml` NEW, :store:reality:test dirigido + 2 regresiones afectadas B3B1A/B3B1B0 + validación XML 8 nuevas/8+8 existentes; chequeo no wiring shipping/no apply grants; guard protocolo.
**B3B1B1B1B** luego: Android host autenticado con consentimiento de READ, no write; política notificada y borrado/invalidation interproceso REAL, RESUME reconciliado; B3B2 G15 posterior. No mezclar unidades.

## B5 — Revisión adversarial inicial / CRITERIO DE STOP
R01 issuer llamado desde Kotlin es falsificable por app UID: no transforma el DTO externo en autenticación humana; B3B1B1B1A sólo DEV, no usar en CompositionRoot, Chat, Surface ni Activity.
R02 no se deben insertar registros `finance_apply_grant`, receipts ni otras mutaciones fuera de `finance_acl_policy`/counter; `finance.snapshot.read` exclusivo.
R03 epochs monotónicos y TTL <=30s; sin reloj Android trusted el caller puede suministrar hora arbitraria y debe documentarse, B3B1B1B se encargará de vincular clock/tokens reales.
R04 emisores sobre schema7/8/cerrado o active Apply => fail-closed; interrupción/rollback SQL no retorna epoch utilizable.
R05 un emisor de READ sin una revocación notificable no garantiza que la UI despeje saldos al ser revocado en otro proceso. No montar UI hasta diseño e instrumentación B3B1B1B1B + campaña G15.
R06 `withMaterialAdmission` conserva exclusividad y BEGIN IMMEDIATE del dueño ACL; nunca usar BEGIN IMMEDIATE para dibujar UI.
R07 pruebas de SQLite auténticas prueban persistencia/ACL, NO AndroidKeyStore ni persona; G15 NOT_RUN.
R08 no repetir B3B0/B3B1A/B3B1B0 certificaciones, ni tocar W2/V2 productiva/main/S2/S3.
**B5 de esta fase:** apto PARA UNA AUDITORÍA ADVERSARIAL INDEPENDIENTE DEL SUBSISTEMA READ ISSUER. B3B1B1B1B real host + cross-process sigue **STOP/NOT READY**, y B3B1B1B1A código continúa BLOCKED hasta B5 independiente y permiso READY.

## B6
NOT_RUN: ningún código en este checkpoint documental.
## B7
PENDING_FINAL_AUDIT. Requiere código exact cuatro rutas, XML, guard, checkpoint y writer RELEASED.
