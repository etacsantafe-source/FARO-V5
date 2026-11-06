# FARO V5 S3.A4 — N031 FBS2 v3 semantic envelope + all restore fences · B1–B5
Fecha 2026-11-06 AR. Baseline `36a77da8cea7ede1dca55ed6e39ba9b00eaacc6c`.
Unidad `S3_A4_N031_FBS2V3_SEMANTIC_ENVELOPE` estrictamente **formato semántico STAGE_ONLY / NOT PERSISTENCE / NO PRODUCT EXPORT OR RESTORE**, sin versionar Reality ni introducir datos sin respaldo. La unidad anterior de modelo/códec B7 `1bf0f03e` + `39c1b2c5` está cerrada y se REUTILIZA.

## B1 — Evidencia / aceptación binaria
- Repository HEAD y writer verificados. S2 8/8 CLOSED, S3 A4 OPEN, V2 productiva/Noche/Auto/S4 OFF, Guardados+8 NO_APPROVED catálogo172, Android G15 físico pendiente.
- `FaroV5FiveStoreSemanticCodec.kt` actualmente FBS2 v1 (FBS1+Workspace, 2 digest) y v2 (agrega G4 Objetivos/Asuntos, 3 digest), total <=16MiB; `FaroV5PlanningDomainSemanticCodec.kt` ya sabe codificar nuevas reglas/ocurrencias y MonthlyPlan en sección PLN1/schema8 <=4MiB pero no está integrado.
- `FaroV5FiveStoreStagedRestore.kt` público rechaza v2 G4 con `UNSUPPORTED_G4_ARCHIVE`; método interno G4 stage-only permite v2 bajo pin AEAD y 5 store. Si v3 estuviera habilitado en decode sin un NUEVO filtro previo, ese G4-stage podría restaurar 5-store y OMItIR totalmente el nuevo Planning owner: **pérdida silenciosa**. Hay que bloquearlo ANTES de pin/SQLite. `ExportPreparation.prepare` actual rechaza G4 pero no Planning explícito.
- Aceptación: v1 y v2 bitstreams estables; v3 requiere SIEMPRE secciones explícitas legacy+workspace+G4+Planning, incluso G4 vacía auténtica, NO permite `null` interpretado como owner vacío; digests, límites y AEAD existentes; nuevo v3 rechazado con motivo específico tanto en restore público como stage G4 interno, antes de efectos; test reintento de archivo v1 tras rechazo v3 demuestra que no quedó archive pin. CI nuevas 6+ G4 5+ v1 7 + stage G4 7+ export fence 4 y guard7 bloques.
- Estado esperado **B7 PASS_SCOPED_FORMAT_AND_FENCES_ONLY**, sin reconocer cobertura de backup o recuperación de Reality schema8.

## B2 — Reutilización V4.4 / V5 y alternativas
- V4.4 FollowUp Rule/Occurrence/MonthlyPlan read-only aporta división por propietario; no copiar base, scheduling Android, ni restauración. V4.4 no tiene FBS2 semántico V5 y no aporta implementación transferible al wire.
- REUTILIZAR FBS2 v1/v2 Byte/Data IO, SHA256 por sección solo anti-corrupción, FAROBAK2 AES-GCM AEAD, y encoder PLN1; NO cambiar bytes, ordering ni read-admission de v1/v2. No tocar RealityStore schema7.
- Alternativa A adjuntar Planning opaco en v2 sin version: DESCARTAR por lectores v2 que ignoran el owner. B crear FBS3 independiente: DESCARTAR complejidad y duplicación de manifest. C FBS2 version3 con cuatro secciones, explicit nonnull G4 + Planning, y público/ interno FENCED: ELEGIR. Solo implementado en scope no productivo. No API paga ni dependencias nuevas.

## B3 — Investigación externa y prueba adversarial
- SQLite online backup https://www.sqlite.org/backup.html : asegura snapshot por base pero NO consistencia entre cinco stores; **REFERENCIA**. Las secciones nuevas no sustituyen la futura única lease/quiescencia y snapshot Reality atómico.
- OWASP Mobile Security https://cheatsheetseries.owasp.org/cheatsheets/Mobile_Application_Security_Cheat_Sheet.html : datos sensibles no deben filtrarse por almacenamiento, backups o logging. **ADAPTAR**: plaintext solo memoria; AEAD externo FAROBAK2, hashes NO autenticación, no imprimir contenido.
- SQLite migration/Android https://developer.android.com/training/data-storage/room/migrating-db-versions : conservar migraciones, NO fallback destructivo. REFERENCIA futura migración manual 5/6/7→8.
- Hipótesis falsables: FBS2 v1/2 exact bytes; v3 tampering digest/replay/magic/version/longitud/trailing fail-closed; G4 null fail-closed al encode v3, Planning null no certifica vacío; stage G4/public no escriben ni pin con v3; ninguna exportación productiva externa; AEAD alterado rechaza. La sección PLN1 limita 4MiB, header v3 tiene 4 digests SHA256 y 4 longitudes y longitud total estricta.

## B4 — Diseño y paths EXACTOS (cinco)
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreSemanticCodec.kt`: agregar `planningDomains: PlanningDomainSectionSnapshot? = null` al set, FBS2 v3 cuando no-null y G4 obligatorio; header v3 = magic/version + 4 lengths + 4 SHA256; 16MiB total y 4MiB cada sección G4/Planning, enc/dec bytes exacto, no alterar v1/v2. No permitir downgrade de v3 a v2/v1 por decoder.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt`: valor `UNSUPPORTED_PLANNING_ARCHIVE` y rechazo antes de source-target check/pin/staged write EN AMBAS rutas `restoreAuthenticated` y `restoreAuthenticatedG4StageOnly`. No implementar restore Planning ni habitar puerto.
3. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreExportPreparation.kt`: check explícito planning null, preservando fence G4 actual.
4. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Fbs2V3PlanningEnvelopeTest.kt`: seis pruebas de version, stable v1/v2, AEAD, digest/length corrupt, malformed/missing owner, stage-public G4-stage both reject before writes and archive pin, previous archive v1 still works. Fixtures nuevas puramente locales no G15.
5. `.github/workflows/faro-v5-s3-n031-fbs2-v3-envelope-directed.yml`: JVM new 6+ G4 v2 5+ legacy v1 7 + G4 staged restore7 + existing export fence4 (confirm XML counts before B7), guard mandatory and V2 core CI indirect por SHA. No secretos/no Android device, SDK host para configurar gradle.
- Los ports de captura FBS2, graph, Reality y PERSISTENCIA NO SE EDITAN en esta unidad. N031 Home/Activity/ACL tampoco. No writer durante CI.

## B5 — Auditoría previa / decisión
- Revisado: fallas de identidad, corrupción accidental, bytes sobrantes, sección no autenticada, secciones ausentes, readers v1/v2, replay de archivo alterado, v3 bajo public/stage G4. **Siempre rechazar v3 antes de archive pin o SQLite**. `FaroV5FiveStorePortableBackupCrypto.open` solo devuelve snapshot AEAD; no autoriza restauración. El hecho de poder codificar v3 NO es captura/restore completo ni permiso para exportarlo a usuario.
- Condición READY **SOLO FBS2 v3 semántico + cinco cercos**, con pruebas adversas en SHA. El STOP antiguo para `Reality schema8 + genuine Reminder/MonthlyPlan owners + FBS2v3 capture/stage restore` CONTINÚA, no puede escribirse un row nuevo bajo los formatos actuales hasta pasar toda esa unidad posterior.
- Prohibiciones: main/V4.4/S2, G15 físico, V2 prod/Noche/Auto/S4, DDL schema8 y nuevos ports de host; no modificar catálogo172, no inferir pagos, no falsos 0 pendientes. Si alguna CI falla, RED_FIX solo de este unit o STOP; B7 independiente, explicit source SHA.
- **B5 READY SCOPED solo la unidad de cinco paths.** Nuevo esquema Reality y BACKUP real siguen STOP.
