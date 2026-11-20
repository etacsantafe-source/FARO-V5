# FARO V5 — W04-C1b2F · B7 RED_FIX · respaldo legacy FAIL-CLOSED
Fecha: 2026-11-20. Unidad S4_W04_C1B2F_SEMANTIC_BACKUP_CODEC_DEV. B6 inicial 0ba07be503a960ac38eb533296f1e446c5b775b8. Parent READY 8af5efb43478b45ae1f5519e8f2fd9064009f173.

## B7
**RED_FIX, NO PASS**: guard 44822996579 SUCCESS, CI 44822996440 FAILURE. Job 136588379533: :evidence:test compiló, 65 tests, 2 fallaron; :maintenance:test no ejecutada tras fallo. Los dos son:
- CommercialEvidenceStagedTypedBackupTest.legacyV1BackupAndTypedPortCannotCrossVersions (line 158).
- CommercialEvidenceTypedTermsTest.noV2BackupPortabilityOrSemanticMigrationMaySilentlyDropTerms (line 130).
Causa: CommercialEvidenceStore.backup.snapshot() permitió schema2 al usar stagedTypedTermsV2=true y rompió el contrato previo que exige DENY del backup legacy en staging. Los tests C1b1/C1b2E no se modificarán. La regresión compartida Guardados 44822996433 SUCCESS: no repetir Guardados.

**Corrección limitada a la MISMA unidad**: agregar segundo opt-in independiente stagedSemanticBackupV2=false por defecto, sólo permitido cuando stagedTypedTermsV2=true; backup.snapshot y restoreOrVerify(schema2) requieren ambos opt-ins. El puerto stagedTypedBackup de C1b2E sigue válido sin habilitar FBS1. Sólo el fixture de FBS1v4 en SemanticBackupRestoreT2Test.kt habilita explícitamente el segundo flag. Rutas exactas: CommercialEvidence.kt y SemanticBackupRestoreT2Test.kt, incluidas en permiso existente de seis rutas. El graph activo no inyecta flags nuevos, y v1 conserva sus bytes y alcance.

Repetir CI JVM Evidence+Maintenance, verificar JUnit XML exacto de SemanticBackupRestoreT2Test (8/8) y guard SHA; registrar B7 independiente. Ningún resultado PASS declarado por adelantado.
