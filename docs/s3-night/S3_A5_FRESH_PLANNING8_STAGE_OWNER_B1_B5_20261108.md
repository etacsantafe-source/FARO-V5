# FARO V5 — S3.A5 preparación de candidato Reality8 fresco e inactivo — B1–B5
Fecha 2026-11-08 AR. Base `8756d011c015894948ece2c2f8f567cb0b5ba95e`. Writer epoch589; unidad `S3_A5_FRESH_PLANNING8_STAGED_OWNER`. V2 shipping OFF, A3 candidato catálogo172 sin aprobar.

## B1
- A5 primera subunidad `S3_A5_ACTIVE8_CERTIFIED_HOST_OPEN` 6/6 real SQLite, M5 firmado y guard B7 PASS, sin cambio de puntero; prueba que grafo Reality8 **ya activo** puede abrirse a host certificado. Falta ruta de producción acotada para generar una Reality8 nueva bajo formato STAGED antes de una transición autorizada.
- `FaroV5WorkspaceV2StageOwner.stageFreshAndVerify` prepara cinco bases nuevas Reality7 de forma canónica con descriptor y marker. `FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8` (interno de mismo módulo) valida/migra SOLO STAGED inactivo a Reality8; N033C2A 17/17 ya lo usó en fixtures. NO repetir internals: exponer un método de alto nivel explícito `stageFreshPlanning8AndVerify` que los componga, siempre sin tocar pointer activo ni datos existentes.
- Nueva unidad NO hace cutover/restore/replay, no proporciona una Activity ni autoriza escritura humana. Error deja STAGED en cuarentena; no reintenta ni borra automáticamente una generación parcial.

## B2
- Reutilizar `stageFreshAndVerify` y `openValidatedV2PlanningStage8`, sin copiar cinco constructores ni migraciones. El nuevo método toma el puntero **completo** previo y verifica que sea idéntico antes y después del upgrade staged. Evita carrera de ida/vuelta por ABA incluso si generation ID retorna.
- V4.4 comparada solo conceptualmente, ningún dato o API V4.4 en V5. Persistencia ya probada por N033C2A 17/17 y FBS2v3; la novedad aquí es un OWNER de STAGED de alto nivel, usable luego por el DEV lifecycle.

## B3
- SQLite https://www.sqlite.org/wal.html, https://www.sqlite.org/isolation.html : abrir/cerrar todos los stores en generación STAGED inactiva y verificar preflight; **ADAPTAR** flujo ya existente, no inventar doble conexión ACTIVE.
- Android Room migrations https://developer.android.com/training/data-storage/room/migrating-db-versions : `fallbackToDestructiveMigration` elimina permanentemente datos; **DESCARTADO** incluso si bases V5 actuales son de test. Ningún borrado implícito.
- Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first : origen canónico local, generación nueva aislada. **ADAPTAR** pero sin incorporar Room o API de red.
- Proyecto real Google Now in Android https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md : patrón repositorio/fuente de verdad, solo **REFERENCIA**, no copiar código ni dependencia ni costo/licencia.
- OWASP Authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html : preparar bases no concede ACL/read grants ni autorización de cutover. No llamar este método desde Chat/modelo/automación.
- Alternativa A adoptada: wrapper fresh-only en dueño de STAGED. B rechazado: migrar `RealityStore(planningStage8=true)` directamente sobre V2 ACTIVE7. C rechazado: borrar/reinicializar V2 DEV sin permiso. D rechazado: promover puntero aquí sin journal+coordinator.

## B4
- SOLO tres rutas funcionales:
  1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5WorkspaceV2StageOwner.kt`: `@Synchronized fun stageFreshPlanning8AndVerify(descriptor)`, comprobación exacta puntero antes y después, `stageFreshAndVerify` + `openValidatedV2PlanningStage8`, close y health, retorna `StagedStorageGeneration`. No cambia el método existente.
  2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5WorkspaceV2Planning8StageOwnerT2Test.kt`: 5 nuevos casos SQLite reales: candidato limpio Reality8 con cinco owners, conservación de ACTIVE7 existente, rechazo de target activo, rechazo de target ya staged, cambio de puntero durante stage antes de upgrade fail-closed.
  3. `.github/workflows/faro-v5-s3-a5-planning8-fresh-stage-directed.yml`: `:store:generation:test --tests ...Planning8StageOwnerT2Test` y XML 5/5, guard seven-block.
- Unit tests usan `@TempDir`; nunca se monta ruta /data/user/0 ni se crea paquete Android. No reejecutar C2A ni C1 salvo regresión atribuible al nuevo diff.

## B5
- `stageFreshPlanning8AndVerify` sólo sobre ID nuevo en directorio canonico, sin symlink o aceptar archivos residuales; los checks existentes `stageWorkspaceV2` y `openValidatedV2PlanningStage8` son la autoridad. No abrir un pointer ACTIVE apuntando al mismo target ni migrarlo desde 7.
- Exigir pointer completo igual ANTES del upgrade y al retornar; si otro proceso publica un nuevo pointer en cualquier momento se niega el stage y se deja quarantined. No hay rollback directo porque no hubo switch; no limpiar staged partial.
- Pruebas deben demostrar que ni bytes de Reality7 activa ni pointer se alteran por un stage8 fresco; verificar health y schema8 sobre cinco owners reales.
- No cambiar GraphOwner, V2CoreDevAuthority, Manifest, Workspace, Surface, RealityStore, Review/Apply/Undo, protocolos S2, S4, V4.4/main, Android permisos, catálogo172, shipping V2. A5 restante: usar candidate para cutover seguro y host/ACL en DEV, luego campaña física X01–X12. 
- B1–B5 preparados antes del B6, sujeto a permiso READY exacto y B7 independiente. Resultado esperado de ESTA unidad: preparación STAGED8 PASS, nunca activación o conteo global de Pendientes.
