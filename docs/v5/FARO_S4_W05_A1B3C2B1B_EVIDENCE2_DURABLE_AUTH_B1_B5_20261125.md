# FARO V5 — W05-A1B3C2B1B · PRE-CUTOVER Evidence2 SQL11 durable authorization (B1–B5)
Fecha local 2026-11-24/25; repo etacsantafe-source/FARO-V5, rama work/s3-night-20261102.
Unidad: `S4_W05_A1B3C2B1B_EVIDENCE2_SQL11_DURABLE_PRECUTOVER_AUTH_DEV`.

## B1 — Estado y evidencia vigente (no repetir)
HEAD entrante `febae84607b69d4444e5d41364de5431f092516e` (C2B1A B7 PASS scoped), guardia documental final `45520311355` SUCCESS, writer RELEASED época 1919. C2B1A código `5c080ef54d0dda5dedb005c8ef1397daaa293dfa`, CI `45519526104` SUCCESS XML **20/20**, guardia `45519525935` SUCCESS. C2B0 read-only schema SQL11 Evidence2 XML14/14 PASS y C2A FBS2v8 restore Evidence2 SQL11 STAGED XML16/16 PASS certificados, no repetir implementación.
Gap: aún no existe una autoridad durable PRE-CUTOVER que registre una solicitud Evidence2 con un ticket independiente y journal PREPARING→STAGED→VALIDATED, resumible tras crash y sin seleccionar el puntero. La clase `FaroV5Household11DevCutoverAuthorization` (C1A) está restringida a Evidence1 schema1 y ticket v1; usarla para Evidence2 comprometería aislamiento versionado. Se habilita exclusivamente DEV con host cerrado y confirmación humana foreground. No autoriza corte, Apply ni stock real.

## B2 — Reutilización, límites V4.4
Reutilizar sin modificar `FaroV5Household11DevCutoverAuthorization.kt` (formato durable ticket + checksum, fsync archivo/directorio, cierre/lease, journal CAS PREPARING/STAGED/VALIDATED, recovery READ_ONLY, inyección de crashes) y sus ocho tests de regresión Evidence1; reusar `FaroV5Household11Evidence2SemanticPreflight` (C2B1A) que compara semántica typed ABSENT/UNKNOWN/KNOWN contra FBS2v8 autenticado; `SealedCommercialEvidence2SnapshotReader` (solo lectura), `FaroV5Household11Evidence2SemanticPreflightTest` para fixtures; `FileRestoreArchiveIdentity` y pointer CAS.
La auditoría V4.4 P02/P03 es antecedente de durability/rollback, no certifica el nuevo ticket, términos ni producto. No tocar V4.4, `main`, C1A/C1B/C1C, C2A/C2B0/C2B1A.

## B3 — Alternativas y decisión de seguridad
A ADOPTAR: nuevo owner `FaroV5Household11Evidence2DevCutoverAuthorization` con `household11-evidence2-dev-cutover-v2.bin` y `FARO_H11_SQL11_EVIDENCE2_PRE_CUTOVER_AUTHORIZATION_V2`, no reutilizar ticket v1. Escribir con atomic move, SHA256 readback, fsync archivo/directorio, lock exclusivo de root. Confirmar foreground antes de lectura y justo antes de publicar; ejecutar semántica C2B1A dos veces, antes/después de seam; rechazar cambio completo del pointer, host, certificado, archive/pin o stage.
B ADOPTAR: en reentrada con ticket existente, exigir operationId original **idéntico**, hash del ciphertext original, autenticidad AES-GCM FBS2v8 schema2 y snapshot comercial tipado completo vs SQLite STAGED sellada, además de los hashes de cinco-store y revisions H10/H11R; no confiar en ticket SHA256 como autorización externa; solo identidad local durable bajo root lock. Recovery READ_ONLY sin la clave mantiene estado/cinco fingerprints+schema2+typed fingerprint internos; **no** valida nuevamente fuente FBS2v8 sin archivo, y nunca avanza journal.
C RECHAZAR: reciclar ticket de C1A v1 o aceptar dos tickets simultáneos; produciría perfil ambiguo y podría compartir rutas de activación no certificadas.
D RECHAZAR: autorizar a partir de C2B0 (solo schema) sin C2B1A semántica; puede aceptar una promoción alterada y fingerprint actualizado.
E APLAZADO: consumo del ticket por cutover C2C, pointer CAS, replay fence, ACTIVE WAL read/reopen y Android. Requiere protocolo separado, no usar C1B.
Referencias SQLite WAL https://sqlite.org/wal.html, atomic durability APIs ya presentes en C1A, Android Room no ejecuta migración aquí. Sin dependencia ni API pagada.

## B4 — Scope exacto y B6 proyectado
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2DevCutoverAuthorization.kt`: clase DEV aislada con ticket v2, foreground confirm2, semantic FBS2v8 C2B1A, journal CAS, recoverReadOnly. Rechazar ticket v1 presente; devolver BLOCKED al conflicto, nunca seleccionar generación ni fence. Revalidación en resume.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2DevCutoverAuthorizationTest.kt`: fixture Evidence2 REAL FBS2v8 SQL11 y 8 pruebas de éxito, reentrada, crashes, tamper, lock/host, pointer ABA y separación v1/v2.
3. `.github/workflows/faro-v5-s4-w05-a1b3c2b1b-durable-authorization.yml`: gate preflight exact3 HEAD^ READY, JDK21 SDK37, ocho nuevas + ocho C1A y seis C2B1A de regresión = **22/22 JUnit XML**, cero failures/errors/skips, upload original y guard.
No permisos de archivos adicionales ni modificaciones al owner viejo, schemas, API Android, agenda, finanzas, pagos o producción.

## B5 — Pruebas adversariales y STOP
T01 Positive Evidence2 FBS2v8 SQL11 staged, snapshot tipado exacto; foreground 2 confirmaciones, ticket VERSION V2 durable + journal VALIDATED, cinco stores y pointer intactos, materialWritesAccepted=false, sin fence.
T02 Idempotencia bajo misma identidad y bytes y recoveryReadOnly; reautorizar operación distinta o ciphertext re-sellado → BLOCKED, el ticket permanece inalterado.
T03 Crash tras TICKET, PREPARING, STAGED → observe READ_ONLY, resume con mismo AES-GCM autenticado y foreground, llegar a VALIDATED sin cambiar pointer.
T04 Host revocado, operación/journal ajenos, puntero ABA, ticket v1 presente → BLOCKED; jamás downgrade a schema1 ni aceptar un ticket inválido.
T05 Variación comercial typed con fingerprint recalculado, ABSENT/UNKNOWN cambiado, nonce y contraseña incorrecta → BLOCKED antes de nueva autoridad; fingerprint de cinco stores recheck en recovery.
T06 Confirm foreground negado una de dos veces → BLOCKED sin publicar ticket/journal; no facilitar stock ni otros dominios.
T07 Ejecutar tests exactos nuevos **8/8**, regresión C1A Evidence1 **8/8**, C2B1A typed **6/6**, XML total **22/22**; guard obligatoria exact SHA SUCCESS y B7 solo entonces.
T08 V2 productiva, stock real, Apply, pagos, Auto/Noche OFF y sin campaña física. A1 GLOBAL OPEN.

**B1–B5 AUDITED, B6 NO EJECUTADO Y B7 PENDING** hasta guard documental y permiso exact3 READY comprobado.
