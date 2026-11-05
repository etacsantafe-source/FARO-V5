# FARO V5 S3.A2/G4 — propietarios Objetivos y Asuntos: contratos aislados, B1–B5 (05/11/2026)

**Unidad acotada:** `S3_A2_G4_OWNER_CONTRACT_FOUNDATION`. Rama: `work/s3-night-20261102`; HEAD de revisión: `63bb02e8575e82700644a411c95ce699753c7973`. Este documento **NO** certifica fuentes persistentes ni G4 global; solo aprueba, si el preflight coincide, contratos tipados nuevos y sus tests. S2 8/8, G1–G3 CLOSED; no se repiten.

## B1 EVIDENCIA
- Árbol de código revisado (1326 rutas, completo): `v5/owner/` contiene `finance` y `profile`, NO Objetivos/Asuntos. `v5/store/reality/RealityStore.kt` es autoridad SQLite para Finance/Profile y opera sobre esquema 5; cambiarlo sin migración/backups/semántica viola durabilidad.
- `v5/control/search/FederatedCanonicalSearchCoordinator.java` ya admite fuentes explícitas con cobertura por dueño, relectura de stamp y degradación; NO repetir federador.
- `v5/app/FaroV5FinanceSearchReadSource.kt` es B7 PASS SOLO COMO FUENTE NO MONTADA. No abre Finance, Objetivos o Asuntos. `MatterAttentionReadProjection` de N031 es una proyección, no un dueño de Asuntos.
- Se verificó writer RELEASED epoch 238 antes de preauditar. `FARO_PROTOCOL_WORK_PERMIT.json` continúa BLOCKED tras Finance B7. G4 permanece OPEN.

## B2 COMPARACIÓN
- V4.4 `FaroV4ObjectivesGateway.readForSearch()` y `MatterRepository.all()` (referencias verificadas en `S3_A2_G4_FINANCE_SOURCE_B7_20261105.md`) aportan semántica de título, detalle, estado/fecha y cartera. NO importar el repositorio in-memory `ObjectiveRepository` ni el `MatterService` como autoridad V5, ni copiar permisos de Chat.
- V5 debe declarar IDs propios, revisiones y snapshots legibles sin crear por accidente una sexta autoridad física ni desplazar el writer/Atomic Apply único.

## B3 INVESTIGACIÓN DE ALTERNATIVAS
- Android Database Migrations (documentación oficial): https://developer.android.com/training/data-storage/room/migrating-db-versions — migrar todos los caminos y probar backups; el fallback destructivo pierde datos. ADAPTAR exigencia de migración y ensayos cuando se implemente persistencia, NO lanzar migración en esta unidad.
- Nextcloud Unified Search https://docs.nextcloud.com/server/stable/developer_manual/digging_deeper/search.html — fuentes explícitas con cobertura. ADAPTAR federación por dueño; descartar backend remoto, permisos cruzados, paquetes/licencias.
- Opción A: contratos puros en módulos JVM por dominio + pruebas de validación, SIN instancia/fuente registrada. ELEGIDA como paso acotado previo a schema+receipts.
- Opción B: inventar snapshots en memoria y marcarlos como persistencia real: DESCARTADA, falsa disponibilidad tras reinicio/restore.
- Opción C: reusar Workspace/conversation IDs para objetivos o asuntos: DESCARTADA por separación de autoridad y navegación.
- Sin APIs pagas, sin dependencias externas nuevas; código original y `faro.jvm-library` existente.

## B4 UNIFICACIÓN Y PATH-SET EXACTO
- `v5/settings.gradle.kts`: declarar `:owner:objectives`, `:owner:matters`.
- `v5/owner/objectives/build.gradle.kts` y `v5/owner/matters/build.gradle.kts`: mismo plugin JVM de Finance; solo dependencia de contracts + JUnit de test.
- `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectiveModel.kt`: ObjectiveId/title/detail/status; snapshot con revisión no negativa e IDs únicos; `ObjectiveSnapshotReadApi` **interfaz sin dueño instalado**.
- `v5/owner/matters/src/main/kotlin/ar/com/faro/v5/owner/matters/api/MatterModel.kt`: MatterId/title/detail/status/dueAt opcional/updatedAt; snapshot revisionado e IDs únicos; `MatterSnapshotReadApi` **interfaz sin dueño instalado**.
- Dos tests JVM de estos contratos en sus rutas `src/test` y `.github/workflows/faro-v5-g4-owner-contracts-directed.yml` para correr Gradle/test exacto sobre la rama.
- Prohibido modificar `RealityStore`, schema version, semantic backup, permisos, composición host/UI, código G1–G3, Finance PASS, catálogo 172, main/V4.4, S4 formal y V2 productiva. B6 será fundación de contratos, nunca owner real hasta que exista persistencia, restauración y ACL.

## B5 AUDITORÍA ADVERSARIAL
- Test IDs/títulos vacíos o excesivos; IDs duplicados; revisiones negativas, fechas negativas; invariantes de snapshot; ausencia de side effects en lectura de modelos.
- Rechazar cobertura COMPLETE y registro en federador desde estas interfaces solas. Contratos sin implementación no prueban persistencia, permisos, navegación ni G4. Migración Reality esquema 5→6, receipts, backups y restauración son **otra unidad que exige B1–B5 propio**.
- Riesgos abiertos: perder datos si se migran tablas sin backup; torn reads al cambiar owner/ACL, ABA tras restore, exposición de conceptos/títulos privados. Falta test físico G5. Dictamen: **READY_BOUNDED_CONTRACT_FOUNDATION_ONLY**; G4 GLOBAL BLOCKED hasta owner durable real de Objetivos/Asuntos + host Finanzas + federación tipada/navegación segura.
