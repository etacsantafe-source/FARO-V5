# FARO V5 — Noche A V1.3 · turno 2 (30/10/2026 23:35 Argentina)

- Repositorio PRIVADO: `etacsantafe-source/FARO-V5`; rama única `work/cierre-noche-v13-20261030`.
- Estado de partida: HEAD `a1bb192fbaddaaf1c458245f85067cb2f63c11b6`, writer NONE epoch19; Noche B anterior COMPLETED, A actual IN_PROGRESS. Writer adquirido por CAS epoch20; lease desde 02:36:25Z hasta 03:00:25Z (máximo 24 min).
- HEAD de código al cierre: `6159b5072f6f85477687916add67d0d6884242ad`.
- Commits funcionales: `ac909b2` (SQLite NOFOLLOW/alias Android + tests), `71fe878` (OS Delivery permission denied + CI dirigida), `832f907` (host real IA con consentimiento/Keystore, debug-only), `6159b50` (reproyección durable desde Workspace sin re-Apply).
- Archivos: `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/{StagedSqlitePreflight,ActiveSqlitePreflight}.kt`, `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraphV2T2Test.kt`, `v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5F1QuarantinedComposeInstrumentationTest.kt`, `v5/app/src/debug/kotlin/ar/com/faro/v5/app/FaroV5F1ConsentedQuarantineHost.kt`, `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5QuarantinedReviewedApplyDeliveryPort.kt`, `.github/workflows/faro-v5-v13-f1-functional.yml`.
- CI anterior: #41158828666 SHA 90b87c9 FAILURE Android XML 2/2 failed SQLite code14, JVM 2/2 PASS; #41159082904 SHA 5b0d1e2 FAILURE Android XML 2/2 same code14, JVM Gradle dependency resolution transient. No Android PASS.
- CI nueva: #41160899873 SHA 71fe878 IN_PROGRESS, tres jobs; #41160976772 SHA 832f907 CANCELLED por sustitución automática de pending en GitHub concurrency (NO cancelación manual); #41161068591 SHA 6159b50 PENDING. No cancelar ni atribuir PASS; comprobar XML terminal en próximo turno.
- Hipótesis SQLite: `SQLITE_OPEN_NOFOLLOW` no tolera un alias de directorio padre como `/data/user/0`; se canonicalizó el directorio, manteniendo la defensa de symlink en archivo final y `immutable=1` solo staged.
- Riesgo CI: grupo de concurrencia por rama provoca reemplazo automático de pending al acumular pushes. No generar otra Actions mientras se verifican las pendientes; ajustar estrategia en siguiente turno.
- S2.A6: **3/8 CERTIFIED**, criterios 4 F1, 5 F2, 6 F3, 7 F4 abiertos; 8 T4 físico NOT_RUN. V2 OFF, S3/S4 no abiertos. Opción A de F3 preservada.
- Duración observada desde adquisición writer: 5.5 minutos. No se simularon 30 minutos ni se atribuyeron minutos facturables de Actions.
- Próxima acción: verificar XML exactos #41160899873 y #41161068591; si ROJO Android code14, revisar URI/canonicalización con nueva hipótesis; después completar F1 foreground/background Delivery y E2E IA consentida sin V2 cutover.
- Cierre: writer NONE epoch20, liberar y verificar por CAS. Esta línea sólo es válida si el commit de cierre y lectura posterior confirman NONE.
