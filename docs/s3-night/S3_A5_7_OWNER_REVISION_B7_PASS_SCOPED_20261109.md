# FARO V5 — S3.A5.7 X07/X11 B7 PASS_SCOPED — Reality8 owner revision fence

Fecha: 2026-11-09 (AR). Unidad exacta S3_A5_7_OWNER_REVISION_FENCE. **PASS_SCOPED 4/4; S3.A5 GLOBAL ABIERTO.**

## B7 — Auditoría independiente, evidencia exacta
- B1–B5 revisados y delimitados antes de código: docs/s3-night/S3_A5_X01_X12_B1_B5_20261109.md (blob 5644c270a8d5c8b03bee41017de6a56d3624c904); permiso READY en padre 8d9030179ac1bd0ddb44e9d803b1f0c1c3824b02.
- Commit funcional https://github.com/etacsantafe-source/FARO-V5/commit/87b1c2ed8d31b5385249c312bbbe7ec358838a30 (SHA 87b1c2ed8d31b5385249c312bbbe7ec358838a30), dos NUEVOS archivos exclusivamente: src/testV2CoreDev/.../FaroV5S3A57OwnerRevisionAcceptanceTest.kt y workflow nuevo faro-v5-s3-a5-7-owner-revision-acceptance-directed.yml. No cambios de producto, stores, Reality8, Apply, permisos, manifest, S2, S4 o catálogo oficial (172).
- CI nueva https://github.com/etacsantafe-source/FARO-V5/actions/runs/42558390722, job 128969143014, COMPLETED SUCCESS, **SHA exacto 87b1c2ed**, gradle :app:compileV2CoreDevKotlin y :app:testV2CoreDevUnitTest con único filtro FaroV5S3A57OwnerRevisionAcceptanceTest, BUILD SUCCESSFUL in 3m 54s.
- Verificador JUnit XML en el job: exactamente tests=4,failures=0,errors=0,skipped=0; línea del log **S3_A5_OWNER_REVISION_REALITY8_OWNER_REVISION_XML_PASS 4/4**. No se usa éxito de build como sustituto de XML.
- Guard B1–B7 https://github.com/etacsantafe-source/FARO-V5/actions/runs/42558390742, COMPLETED SUCCESS en el mismo SHA. No re-ejecutar las seis subunidades A5 previas 33/33.

## Aserciones nuevas cubiertas
1. X07 Objective: edit canónico REALITY8 con puntero sin cambios invalida hit publicado; la consulta nueva permite confirmar detalle.
2. X11 Matter: un destino CONFIRMED no se puede consumir si el owner cambia de revisión antes de take.
3. X07 Finance: nuevo gasto canónico invalida por revisión incluso un hit previamente emitido sobre un gasto no cambiado; no se filtran importes ni IDs de cuenta privados en resumen.
4. X11 aislamiento entre owners: una mutación de Matter no provoca rechazo falso de hit Objective canónico aún vigente.
Fixture: cinco bases SQLite reales creadas bajo @TempDir, transacciones con owner Reality8 y puerto host DEV real, no mocks ni Moto G15.

## Límites, reservas y siguiente
- Este B7 no certifica toda X01–X12 ni navegación UI, accesibilidad/tamaño/pause-resume, entrega FARO/IA, revisión/Apply, receipts ni ejecución G15. Los tests 33/33 se conservan certificados EN SU ALCANCE y no se repitieron.
- X04/X05 dependen de A3 Guardados / 8 candidatos explícitamente NO APROBADOS; no habilitar A3, no cambiar 172. X03 y X09/X10 implican puertos de razonamiento/revisión no conectados en actividad V2 DEV, no pueden marcarse PASS.
- S3.A5 permanece ABIERTO; G15 será UNA campaña física SOLO AL FINAL; V2 productiva, S4, Auto, Noche OFF; writer a liberar epoch606.
- Próximo objetivo automatizable independiente: prueba Android emulada NEW de lifecycle/semántica de UI aislada (X12, sin teléfono físico) si B1–B5 nuevo y permiso READY. No fingir que Android emulado certifica G15.

**Dictamen**: S3_A5_7_OWNER_REVISION_FENCE = DONE_VERIFIED_PASS_SCOPED_4_OF_4. S3_A5_GLOBAL = OPEN_X01_X12. 
