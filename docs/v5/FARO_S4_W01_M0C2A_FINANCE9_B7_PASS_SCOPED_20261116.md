# FARO V5 — W01 M0C2A · B7 PASS acotado: lectura Finance9 desde generación STAGED sellada
Fecha AR: 2026-11-16. Unidad: S4_W01_M0C2A_FINANCE9_SEALED_CAPTURE. Code HEAD 3fa8d9fcc4070f69b7f0d482a2a3c0fd35f11fd1. **Veredicto: B7_PASS_SCOPED_FINANCE9_STAGE_READ_ONLY**, nunca backup integral/export/restore ni apertura productiva.

## B1
Diff del commit exacto 3fa8d9fcc4070f69b7f0d482a2a3c0fd35f11fd1: sólo cuatro rutas del permiso READY anterior:
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9SealedReadPort.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9SealedSnapshotCapture.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9SealedCaptureTest.kt
- .github/workflows/faro-v5-s4-w01-m0c2a-finance9-capture-directed.yml
Parent 98ce527f5276782357e87782c1e7da37336362cf READY exact4, doc B1-B5 `docs/v5/FARO_S4_W01_M0C2A_FINANCE9_SEALED_CAPTURE_B1_B5_20261116.md` blob `dd97d5662cdc7500f3fc45fe767ebd0042df0fd9`. Código y permiso commits distintos; FARO-UNIT exacto.

## B2
Reutiliza migrador M0B y codec F9S1 M0C1, no repite suites 7/7 ni 6/6 anteriores. RealityStore 7/8, cinco almacenes antiguos, FBS2v1–v3, G4 y Planning8 intactos. No importa SQLite runtime desde :store:generation.

## B3
Lector SQLite readonly immutable+NOFOLLOW, sólo OCHO tablas de datos Finance9, secuencia de puntero y descriptor controlados, sin política ni grants, sin exponer export ni mutaciones. Autenticidad del archivo final requiere FAROBAK2 en futuro M0C2B. https://www.sqlite.org/pragma.html ; https://www.sqlite.org/wal.html.

## B4
CI dirigida GitHub Actions run **43965425488**, job **133643488920**, `completed/success` en SHA 3fa8d9fcc4070f69b7f0d482a2a3c0fd35f11fd1. Log `BUILD SUCCESSFUL in 3m 23s` y `S4_W01_M0C2A_FINANCE9_SEALED_CAPTURE_XML_PASS 7/7`. Verificador XML constata exactamente 7 tests, 0 failures/errors/skips y nombres esperados. Artefacto JUnit **14276821059** disponible, no expirado. Guardia siete bloques **43965425327** `completed/success` SHA exacto. Diff inspeccionado, exactamente cuatro archivos.
Siete pruebas observadas: lectura ocho tablas; presupuesto/deuda reales excluyendo política; opening desconocido NULL; ACTIVE rechazado; descriptor/sidecar rechazados; pointer ABA después de leer rechaza retorno; schema desconocido/columna adicional rechazados.

## B5
No certificado: copia consistente de cinco stores, FBS2v4 cifrado con captura integrada, restauración Finance9 stage, reset de ACL, exclusividad entre procesos, Keystore/Android, proceso muerte, G15, frontend, W01-F. `MaterialWriteQuiescencePort` en los tests es fake in-process. No inferir resultados productivos. M0C2B recibe requisitos nuevos y su B1-B7 separado. W02 STOP; main/S2/S3/V4.4/productive V2 intactos.

## B6
Código M0C2A PASS scoped, permiso CONSUMED / BLOCKED. No repetir certificación sin delta real.

## B7
Condición final posterior a este commit documental: guardia SUCCESS sobre SHA documental, writer RELEASED epoch994 con last_checkpoint=HEAD, permiso BLOCKED con last_code_review=3fa8d9fcc4070f69b7f0d482a2a3c0fd35f11fd1 result PASS. Si no, veredicto queda pendiente.
