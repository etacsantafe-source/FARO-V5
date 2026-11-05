# FARO V5 · S3.A2 G4/FINANCE B7 · Auditoría final de fuente canónica aislada

Fecha 05/11/2026 AR. Unidad específica `S3_A2_G4_FINANCE_SOURCE_UNMOUNTED`, código `302b92c90949d3883c7ecd2713bb4ae3f5a2c4ee`. **NO es cierre G4 global**. B1–B5 previos: `docs/s3-night/S3_A2_G4_FINANCE_SEARCH_SOURCE_B1_B5_20261105.md`, blob `4472efe0cb9bd344748171181b124b85568fc59b`. Permiso READY en padre `9185cd1b3990667064cbd03c43b186e8ff982356`, solo 3 rutas preauditadas. G1/G2/G3 CLOSED y sin cambios.

## B7 AUDITORÍA FINAL

### Diff funcional inspeccionado
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5FinanceSearchReadSource.kt`: `FinanceSnapshotReadApi` de Reality como autoridad canónica, `SearchCanonicalReadPort` sin índice, generación/ACL/admission por inyección obligatoria y revisión antes/después de lectura; fuente limitada a 500, PARTIAL en truncación; namespace `reality.finance.entry`; identificadores opacos SHA256, conceptos/categorías de asientos. Nunca se exponen montos, cuentas o entry IDs crudos.
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5FinanceSearchReadSourceTest.kt`: 4 tests nuevos sobre lectura de snapshot de modelo real con fixtures, revocación, rotación de generación, recovery, cambio de revisión, owner cerrado y 501 entradas/truncación. Los fixtures NO demuestran ACL OS real ni instalación.
- `.github/workflows/faro-v5-s3-home-notice-directed.yml`: agrega ejecución y JUnit XML exacto 4/4, sin quitar pruebas preexistentes. Solo esas tres rutas en commit `302b92c90949d3883c7ecd2713bb4ae3f5a2c4ee`, mensaje `FARO-UNIT:S3_A2_G4_FINANCE_SOURCE_UNMOUNTED`. No ha modificado permisos productivos, Compose, S4, catálogos, main, V4.4, ni cinco owners.

### GitHub Actions exactas, mismo SHA
- Protocolo obligatorio [#41965480987](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41965480987): SUCCESS.
- [#41965481039](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41965481039) Home/Search/Finance: SUCCESS, BUILD SUCCESSFUL y XML JUnit exacto: Home 9/9, Workspace Search 7/7, nueva Finance Search **4/4**, Search Bridge 7/7, Avisos 4/4, Chat pointer 6/6, Host ABA 1/1, Search grant 10/10, G2 admission 10/10 = **58/58**. `S3_G1_SINGLE_PROCESS_MERGED_MANIFEST_PASS 2`. G3 2/2 forma parte del Bridge 7/7, NO sumarlo doble.
- [#41965481015](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41965481015) Core aislado: SUCCESS, 3 BUILD SUCCESSFUL, `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`, manifest M1 4/4 y M2B 4/4.
- APK de desarrollo [#41965481032](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41965481032): **SUCCESS #41965481032** al redactar esta auditoría. La APK no es prueba física, ni condición para conectar esta fuente a producción; no inventar certificación.
- Prueba Android física G5: NOT_RUN. No tests de navegación a asiento financiero ni de propietario ACL real o regrant independiente.

### Investigación comparada V4.4 adicional (solo lectura; NO alterar B1–B5 aprobado)
- Referencia V4.4 commit `44f1e6eb77102a71955b4fb113582d209d94ac69`: `app-v4/src/main/kotlin/ar/com/faro/v4/objectives/FaroV4ObjectivesGateway.kt` tiene `readForSearch()` que omite capacidad financiera cruzada; `owner/objectives/src/main/kotlin/ar/com/faro/v3/owner/objectives/ObjectiveRepository.kt` tiene lectura de portfolio y validación de DAG, con una implementación in-memory que **no puede adoptarse como owner durable V5**.
- Asuntos V4.4: `app/src/main/java/ar/com/faro/app/modules/matters/MatterRepository.java` expone `all()`; `FaroMatter.java` tiene título/detalle/fecha/estado; `MatterService.java` notifica cambios pero NO constituye por sí solo ACL/gen durable de V5. ADAPTAR contrato de lectura y lifecycle; DESCARTAR importar persistencia/autoridad V4.4 sin rediseño.
- Estas referencias permiten no reinventar campos y filtros, pero V5 **aún carece** de propietarios Objetivos/Asuntos auténticos; no se habilitan fuentes ficticias.

### Disposición y límites de cierre
**B7 PASS, solo `S3_A2_G4_FINANCE_SOURCE_UNMOUNTED`**: JVM y Gradle dirigido verificados, código de lectura aislado duradero. Fuente Finance **NO MONTADA** en aplicación, no existe Search host Finance owner ACL reconocido, no se anuncia cobertura completa global.
**G4 GLOBAL OPEN/BLOCKED**: faltan propietarios canónicos autorizados y navegación de Objetivos y Asuntos, además del binding financiero seguro por host. La federación genérica ya existía; no rehacerla. G5 pendiente de única campaña física, V2 productiva OFF.
S2 cerrado 8/8 = 100%. S3 desarrollo ≈40% heurístico (35–45%) / S3 formal 0/5 = 0%; A2 G1/G2/G3 3/5 = 60%, G4/G5 NO cerrados. S4, Auto y Noche OFF, CLOUD STAGED, catálogo oficial 172.

**NEXT**: B1–B5 por owner duradero de Objetivos y Asuntos adaptando modelos V4.4 con límites V5, y por host Finance ACL/navigation real; no emitir permiso G4 global hasta evidencia, no inventar PASS.
