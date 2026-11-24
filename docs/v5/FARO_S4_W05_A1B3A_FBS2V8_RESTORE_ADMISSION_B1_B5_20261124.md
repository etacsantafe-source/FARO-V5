# FARO V5 · W05-A1B3A · Admisión autenticada de restauración FBS2v8 · B1–B5

Fecha 2026-11-24, rama `work/s3-night-20261102`. HEAD de entrada `0e9c4595fe8b5f99e7386745b1c11d66fc7780ce`. Writer inicial RELEASED epoch 1830. Unidad `S4_W05_A1B3A_AUTHENTICATED_RESTORE_ADMISSION_DEV`. No modificar V2 productiva, Auto/Noche, pagos ni stock real.

## B1 — Evidencia verificable y objetivo acotado

A1B0, A1B1, A1B2A y A1B2B con B7 PASS; NO repetir. A1B2A FBS2v8/FAROBAK2 AES-GCM tiene CI 10/10 `45430216765`; A1B2B SQL11 STAGED read/restore 10/10 `45433033990`; checkpoint final A1B2B `0e9c4595fe8b5f99e7386745b1c11d66fc7780ce`, guard `45434185264` SUCCESS. A1B2B soporte SQL11 no constituye restauración global de un archivo de cinco stores.

Revisión de código (blobs sobre HEAD):
- `FaroV5Household11Fbs2Codec.kt` `5c66681a0ee2b85fe498a174857aff007a930528`: codec FBS2v8 contiene Household10 v6/v7 tipado + H11R; no autoridad de restauración.
- `FaroV5FiveStorePortableBackupCrypto.kt`: FAROBAK2 AEAD tiene `openWithHouseholdReceipts11`, solo prueba criptográfica y codec; no pin de generación.
- `FaroV5GenerationStoreGraph.kt` `86c33a82901583091e41c759eb79908fae860fd0`: `requireFinance9ReceiptArchiveStage` y `requireFinance9ReceiptEvidence2ArchiveStage` admiten fuente de restauración **STAGED9** con esquema Auxiliary4 (y perfil Evidence2 independiente).
- `FaroV5Household10ArchiveCoordinator.kt` `b80251088566fe58a4f0027bb489acbfb3073409` ya provee patrón exitoso de autenticación anticipada, manifest source/target distinto y lease exacto; NO aceptar su `restoreAuthenticated` para FBS2v8.
- `FaroV5Household10DevCutoverRuntime.kt` `803ad8b94e4dd30df6b0776b4c9f2a47e3563a4f` demuestra cutover journaled/rollback propio Household10, pero no admite schema11.
- Gap: falta un **preflight de restauración v8 que vincule cripto+tipo Evidence1/2+origen declarado+destino STAGED9 canónico+puntero exacto+host exclusivo** antes del primer efecto de restauración.

## B2 — Reutilización y contraste

Reutilizar `FileActiveGenerationPointer` ID+sequence, `FileStorageGenerationLayout.requireStaged`, `StagedSqlitePreflight`, `FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage`, `MaterialWriteQuiescencePort` y el API interno FAROBAK2. Patrón HOUSEHOLD10 no se cambia ni se vuelve a probar globalmente. Comparación V4.4: SmartCompra no estableció contrato de admission FBS2v8; no trasplantar código de V4.4 ni abrir stock.

## B3 — Investigación de alternativas

Referencias primarias: https://www.sqlite.org/uri.html para modo inmutable únicamente en bases realmente selladas; https://sqlite.org/wal.html para sidecars/checkpoint; https://csrc.nist.gov/pubs/sp/800/38/d/final para AES-GCM con AAD/etiqueta. Las condiciones criptográficas ya son del codec certificado, no duplicar crypto/KDF.

A) **Elegida:** preflight interno de solo lectura que primero controla tamaño y destino físico, después autentica y parsea exclusivamente FBS2v8, verifica manifest source != target y perfil Evidence1/2, vuelve a comprobar mismo pointer y mismo stage. No crea identidad ni cambia archivos; devuelve metadatos **no-capability**.
B) **Descartada:** usar restore/cutover Household10 existente con v8: acepta schema10/v6/v7, no recepciones SQL11. Sería pérdida silenciosa.
C) **Descartada:** inferir target válido por nombre y permitir reusar passphrase o snapshot decodificado como autorización de Apply: vulnera autenticidad/identity y lease.

## B4 — Rutas exactas y arquitectura elegida

Solamente tres rutas nuevas, sin mutar codecs/RESTORE certificados:
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8RestoreAdmission.kt`: `inspect` read-only protegido por MaterialWriteQuiescence y host closed DEV; verifica target STAGED9 exacto, cinco SQLite WAL sellados sin symlink/hardlink/sidecar, FilePointer ID+sequence, evidencia comercial y archivo FAROBAK2 v8. Devuelve `Accepted` con SHA256 del ciphertext, source/target IDs, cantidad/revisión H11R y perfil Evidence; nunca bytes, grants o ejecución.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8RestoreAdmissionTest.kt`: 8 pruebas JUnit5 dirigidas (aceptación, parcialidad, auth/tag, versión vieja, conflicto source-target, esquema/preflight, alias/sidecar, lease/ABA/no mutation).
3. `.github/workflows/faro-v5-s4-w05-a1b3a-restore-admission.yml`: gate READY exact3 (parent), Gradle JVM JUnit5, XML 8/8 real, artefacto original.

Se admite solo STAGED9 como punto inicial del restore. El subcaso reanudación de target 10/11 exige pinning de archivo + operación y pasa a la siguiente unidad (no confundir con preflight inicial). Los target Evidence2 deben superar el preflight SQL schema2 de la ruta especializada; no downgrade a schema1. Ninguna mutación sobre `root`, `pointer`, `staged` o journal en A1B3A.

## B5 — Auditoría adversarial previa

Pruebas planeadas:
T01: target auténtico STAGED9, ciphertext v8, SHA y manifest/source ID exactos, pointer no cambia, cinco stores iguales.
T02: dos recepciones parciales sin saldo ficticio, revised-empty preserva revision.
T03: passphrase falsa, tag tampered y header alterado rechazados antes de efectos.
T04: v5/v6/v7 FAROBAK2 no son v8, fail closed.
T05: sourceGenerationId==targetGenerationId, no se autoriza restore in-place.
T06: target SQL10/11 o Auxiliary3/Evidence incompatible no es stage9 apto para restauración inicial.
T07: enlaces físicos, sidecars, archivos ajenos, no WAL son rechazados por stage contract.
T08: revoked closed DEV host + ABA pointer ID+sequence tras autenticación fallan.
La aceptación exige GC y control inicial/final de cinco archivos para no confundir lectura con escritura; no editor/Android, no pagos ni Apps V2.
No claims de completo FBS2v8 restore: **A1B3B** hará replay autenticado y pin durable de identidad contra stage; **A1B3C** cutover/reopen/crash-recovery, y **A1C** Apply/stock real después de certificación separada.

Permiso inicial BLOCKED, publicar preauditoría, verificar guardia documental SUCCESS, recién después emitir READY exact3 y programar. Cualquier test RED exige B7 RED_FIX de la misma unidad antes de otro commit funcional.
