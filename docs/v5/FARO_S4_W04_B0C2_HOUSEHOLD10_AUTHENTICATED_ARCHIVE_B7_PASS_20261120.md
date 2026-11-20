# FARO V5 — W04 B0C2 · B7 independiente de archivo FBS2v6 autenticado
Fecha Argentina 2026-11-20. Unidad S4_W04_B0C2_HOUSEHOLD10_AUTHENTICATED_ARCHIVE_DEV. Código B6: 768c56824d04b051d323988aa2129461d8d16dd6. Permiso READY del padre 54e1824cc17a464d6a9bdab0f215ff62a2f5447d. Auditoría exclusivamente del código/CI del alcance indicado, NO cierre B0 integral.

## B1 — Prueba exacta
- Workflow W04-B0C2 run 44668249894 SUCCESS, job 136062735251, SHA exacto 768c56824d04b051d323988aa2129461d8d16dd6.
- Logs verificados: FARO_PROTOCOL_PREFLIGHT_PASS para cada una de 4 rutas en el padre 54e1824..., Gradle BUILD SUCCESSFUL y verificación explícita del XML JUnit por ElementTree: tests=5, failures=0, errors=0, skipped=0. Log marca W04_B0C2_HOUSEHOLD10_FBS2_XML_PASS 5/5.
- Artefacto XML registrado en GitHub Actions ID 14598807349, digest anunciado sha256:96cf008f4dad75f6c6199e03023ea6689601cf0930dbbd6b7da17569dd5c1000, tamaño 699 bytes, no expirado. El contenido ZIP no se descargó/inspeccionó independientemente en esta auditoría; el XML fue analizado por el paso CI y sus logs comprobados.
- Guardia obligatoria del código run 44668249799 SUCCESS. Regresión V2 core M1/M2 run 44668249957 SUCCESS (no se repite).
- Commit code diff exactamente 4 rutas auditadas: FaroV5Household10Fbs2Codec.kt, FaroV5FiveStorePortableBackupCrypto.kt, FaroV5Household10Fbs2CodecTest.kt y workflow faro-v5-s4-w04-b0c2-household10-fbs2.yml. No mezcla permiso y código.

## B2 — Reutilización y límites de producto
- FBS2v6 lleva FBS2v5 base con Finance9 y receipts Auxiliary4, más Household10 H10S obligatoria. Los métodos sealWithHousehold/openWithHousehold reutilizan KDF/AES-GCM y la cabecera autenticada sin duplicar primitivas.
- No modifica finance, Matter, Objective, Workspace, SQLite de usuario, portadores Android, pointer, recepción física ni pagos.
- Este B7 certifica exclusivamente serialización + autenticación IN_MEMORY en JVM; NO captura SQL, restauración SQLite, cutover/reopen, grants, host o G15.

## B3 — Revisión adversarial del delta
- Test preservesFinanceAndReceiptDataAlongsideHousehold verifica revisión de receipts y fila de budget Finance en payload.
- roundTripsAnExplicitEmptyHouseholdSection exige sección vacía explícita sin perder revisión.
- rejectsMissingSectionsCorruptDigestsAndIncorrectLengths cubre longitudes, digest, truncamiento y trailing bytes.
- readersKeepVFiveAndVSixSeparateInPlaintextAndAuthenticatedArchives garantiza separación de formatos legacy/v6.
- authenticatedHouseholdRejectsWrongPassphraseAndTampering verifica AEAD fail-closed.
- Código examinado: límites size con Long, digests SHA256 estructurales, inputs versionados, arrays temporales limpiados. No claim de datos SQLite reales ni recuperación de crash.

## B4 — Checkpoint y próxima dependencia
Se consume READY B0C2 con último código auditado e informe B7 PASS_SCOPED_FBS2V6_JVM_5_5. Siguiente W04-B0C3A: leer/restaurar filas Household sobre STAGED Reality10; luego B0C3B respaldo integral, D1 preflight, D2 activación/recovery. B1–B5 de estas unidades ya están preparados documentalmente, pero ninguno obtiene READY por herencia. Cada uno exige nuevo permiso y guardia, y verificación de ruta vigente.
La guardia documental del commit que incluía preparaciones B0C3/B0D falló en la verificación de READY porque B0C2 aún no tenía B7; no fue fallo de código. Este cierre corrige la disposición que faltaba para futuros preflight. Revalidar nueva guardia con el SHA real después de publicar.

## B5 — Auditoría de cierre
B7 RESULT: PASS_SCOPED_W04_B0C2_AUTHENTICATED_FBS2V6_JVM_5_OF_5, sujeto a confirmación de publicación/guardia del commit de cierre. B0C3A/B0C3B/B0D siguen NOT_RUN y B0 global OPEN. Ningún test Android/Moto G15. No regenerar tests históricos ni activar V2 productiva, Auto/Noche o endpoints pagos.
## B7 — Disposición
PASS para el alcance real JVM/format/crypto verificado; la cobertura de captura/restauración real está EXCLUIDA y se exige en B0C3. Conservar el SHA exacto y la evidencia de CI. Writer debe quedar RELEASED; ninguna autoridad funcional adicional.
