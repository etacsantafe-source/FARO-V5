# FARO V5 — W03-A · B7 final PASS dirigido DEV

Fecha 2026-11-19. Unidad `S4_W03_A_MATTER_DETAILS_PERSISTENCE`; SHA funcional final `59f74aed5fa7ccc68e35f2ce8a6884fe9f185d5c`. **PASS_JVM_SQLITE_G4_DEV**, no es B7 de W03 global ni Android.

## B1
- Se leyeron `AGENTS.md`, protocolo siete bloques, permiso previo READY exact-route, `FARO_CONTINUITY_POINTER.json`, preauditorías W03-A y RED_FIX. Código inicial `3446cc2dbde6134af3f18cf6f2f28c0c9b272fb8`, test-only RED_FIX `2a8cb086cbe850f9706e3bf488c30e98868a0f01`, workflow-only RED_FIX `59f74aed5fa7ccc68e35f2ce8a6884fe9f185d5c`.
- Se verificó el diff `84a04d0...59f74aed5fa7ccc68e35f2ce8a6884fe9f185d5c`: cambió **solo** `.github/workflows/faro-v5-s4-w03-a-matter-persistence.yml`; el permiso anterior autorizaba únicamente esa ruta. Guardia de código final [44536914665](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44536914665) **SUCCESS**.
- La [CI dirigida W03-A 44536914638](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44536914638) terminó **SUCCESS** en `59f74aed5fa7ccc68e35f2ce8a6884fe9f185d5c`: preflight del delta real PASS, tests Gradle PASS, validación XML PASS, artefacto XML subido.

## B2
- V4.4 follow-up UX/semántica contrastada en B1–B5 sin copiar runtime; W02 ObjectiveDetails reused como patrón de tablas aditivas, historial append-only y wire versionado. No se reabren W01/W02.

## B3
- SQLite aditivo y transaccional dentro del único RealityStore (no base nueva, Room nuevo ni nube); G4 v5 solo si Matter extension presente, v1..v4 legados vigentes. RED_FIX del workflow usa git diff del commit en vez de ampliar permisos. No incorporó dependencias ni API paga.

## B4
- `MatterPriority`, `reviewDate` civil opcional, `MatterHistoryEntry` cronológico y preservación de prefijo, modelos y defaults legacy.
- Tablas lazy `matter_details` / `matter_history` creadas dentro del mismo scope transaccional; read/upsert/restore G4 tipados; el `RealityStore` sigue con esquemas 7/8/Finance9 preexistentes. No se aplicó cambio a Finanzas ni Objetivos.
- El codec G4 v5 preserva Matter extendido a través de backup/restauración FBS2; no recodifica silenciosamente datos sin extensión. Review/Apply humano y pantalla Android corresponden W03-B/E, **NOT_RUN** aquí.

## B5
- Ensayos dirigidos: legacy sin historial ficticio, apertura/reapertura/restore, rollback sin tablas ni revisión residual, fecha bisiesta, prioridad y evento persistentes, timestamp stale, idempotencia, prefijo append-only, codec antiguo/nuevo y secciones Finance9 compartidas. Todos los tests incluidos en la suite ejecutaron con 0 failures.
- El artefacto oficial GitHub Actions `w03-a-matter-persistence-xml` id `14536187864` fue descargado y **leído**: ZIP SHA-256 `d9561fb5b584818fe52c31bb0ccb213cd62830102ca5e332f16c5790ec6f6733`, 7 archivos XML, totales **33 tests / 0 failures / 0 errors / 0 skipped**:
  - MatterModelTest **6/6**.
  - S4W03MatterDetailsTest **3/3**.
  - FaroV5MatterDetailsBackupTest **2/2**.
  - RealityG4DomainRestoreT2Test **5/5**.
  - FaroV5Fbs2G4CodecTest **5/5**.
  - FaroV5Finance9FullArchiveCoordinatorTest **10/10**.
  - FaroV5ObjectiveDetailsBackupTest **2/2**.
- **Advertencia histórica independiente (NO encubierta):** el workflow anterior `FARO V5 G4 owner contracts directed` run `44535643996` falla al validar un contador fijo **4** para MatterModelTest. El XML actual arroja **6, 0, 0, 0** (los 6 pasan); ese workflow heredado exige actualizar su expectativa en otra unidad/permiso si se desea verde global. No es una excepción sobre errores del código W03-A y NO se altera S3 bajo permiso W03-A. Este B7 es dirigido, **no certificación universal de todos los workflows**.

## B6
- Tres commits funcionales del **mismo unit**: implementación 8 rutas, RED_FIX test única ruta y RED_FIX workflow única ruta. Cada commit lleva `FARO-UNIT:S4_W03_A_MATTER_DETAILS_PERSISTENCE` y pasó la guardia en sus SHAs; permisos padre separados y auditados. No hubo escrituras de lógica ajena, S4 global sigue OPEN.

## B7
- **PASS_SCOPED_W03_A_DEV_JVM_33_OF_33** tras verificar head `59f74aed5fa7ccc68e35f2ce8a6884fe9f185d5c`, preflight del parent, guard `44536914665`, JVM+XML `44536914638`, 7 XML/33 tests y artefacto SHA-256.
- Consumir permiso W03-A en `BLOCKED` con `last_code_review=PASS` apuntando al blob de este documento, y puntero W03-A DONE. Nueva W03-B requiere B1–B5 propios y permiso READY distinto. G15/Android S4 W03 NOT_RUN, V2 productiva/Auto/Noche OFF, main/V4.4 inalterados.
- Checkpoint cierre final = SHA del commit DOCUMENTAL que incluye este B7, condicionado a su guardia SUCCESS y writer remoto RELEASED. Antes de esa verificación no declarar FIN.
