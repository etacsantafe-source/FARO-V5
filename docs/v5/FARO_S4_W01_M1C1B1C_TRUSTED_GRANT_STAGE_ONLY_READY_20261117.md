# FARO V5 W01 M1C1B1C — READY ACOTADO DEV/STAGED9; NO HUMAN ROOT OF TRUST PRODUCTIVO
Fecha 2026-11-17 AR. HEAD preauditado `2ed821ff5942c1a351abeab5daec2dc8baa81a46`. Guardia B5 independiente `44071698213` SUCCESS exact SHA. Unidad `S4_W01_M1C1B1C_TRUSTED_LOCAL_GRANT_ADMISSION`.

## B1
Código `S4W01FinanceAuthHost.kt` obtiene prueba ECDSA AndroidKeyStore per-use en callback CryptoObject; evidencia simple es falsificable fuera del host. ACL/grants Reality9 y gate M1C1B1B scoped B7 PASS 19/19 `44061977799` no se reabren. El único delta de integración de autorización será en el callback del host, nunca en Surface/Chat.
## B2
Reutilizar firma M1B, Workspace APPROVED M1C1A, Finance9Acl M1A, RealityStore9 M1C0, grant receipt M1C1B0 y gate M1C1B1B. V4.4 AtomicApply/Undo/recovery intactos. Sin API red ni pagos reales.
## B3
USAR DEV: método host-orchestrated que no recibe DTO de prueba del caller; derivación de principal técnico de llave pública de alias fijo + UID/app; binding de review y generación/época; emisión stage-only por port tipado con SQLite9. DESCARTAR pseudo-presencia de UI como autoridad, public key proporcionada por caller, fallback weak/credential, Compose shipping y grant en backup. Una fake interfaz en tests no prueba CryptoObject físico.
## B4
READY únicamente para siete rutas exactas, ninguna añadida por comodín: 
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceAuthHost.kt`
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01M1C1B1CTrustedGrantAdmission.kt`
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedStageGrantIssuerPort.kt`
4. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
5. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01M1C1B1CTrustedGrantAdmissionTest.kt`
6. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedStageGrantIssuerPortTest.kt`
7. `.github/workflows/faro-v5-s4-w01-m1c1b1c-grant-admission-directed.yml`
Implementar 6 tests JVM pure policy + 4 tests Reality9 SQL stage; CI Android :app:testDebugUnitTest + :store:reality:test y compilar host; verificar XML exact y regresión de compilación S3. Todo código en único commit B6 con marcador FARO-UNIT, sin cambio de permiso en commit funcional.
## B5
Auditoría independiente `docs/v5/FARO_S4_W01_M1C1B1C_TRUSTED_GRANT_INDEPENDENT_B5_20261117.md`, blob `c14983427209974648d445c2c982e4a188f39727`, revisa específicamente spoofing de DTO, Trust boundary del callback Android, UID/pointer ABA, expiración, epoch SQL, colisión UNIQUE, cierre fail-closed. Certificación **sólo DEV/STAGED9**; no afirma root of trust productivo ni G15 físico. El issuer stage store es API para código dentro del propio UID, NO es una barrera contra código comprometido de la app. No montar en Activity/CompositionRoot normal.
## B6
READY_TO_IMPLEMENT al publicar permiso y pasar guard preflight. Aún NOT_RUN.
## B7
PENDING_FINAL_AUDIT: exigir CI + JUnit XML SHA exacto, guard y writer RELEASED; si falla declarar RED_FIX y no repetir tests previos certificados.
