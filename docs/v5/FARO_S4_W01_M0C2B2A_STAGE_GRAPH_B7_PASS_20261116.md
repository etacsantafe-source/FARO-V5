# FARO V5 — W01 M0C2B2A · B7 PASS scoped: Finance9 STAGED five-store graph
Fecha AR: 2026-11-16. Unidad S4_W01_M0C2B2A_FINANCE9_STAGE_GRAPH_ADMISSION. Código `e587da2e609a67780ba2ce6302627436a05fc132`. **Veredicto B7_PASS_SCOPED_STAGED9_GRAPH_ONLY**, sin restore FBS2v4 completo ni montaje Android productivo.
## B1
GitHub diff exacto cuatro rutas: `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`, `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`, `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9StageGraphAdmissionTest.kt`, `.github/workflows/faro-v5-s4-w01-m0c2b2a-finance9-graph-directed.yml`. Parent READY `0b5af99edc564feca8ab793386d44839dc2838c2`, preaudit B1-B5 `docs/v5/FARO_S4_W01_M0C2B2A_FINANCE9_STAGE_GRAPH_B1_B5_20261116.md` blob `5be11de02f705066c9d1fc50e5e87a787bd899b2`. Permiso/code separados, exact four paths, FARO-UNIT.
## B2
RealityStore default sigue schema7, Planning8 se mantiene opt-in; sólo `financeStage9=true` con `planningStage8=true` abre un schema9 YA existente, jamás migra ACTIVE. Nuevo `openValidatedV2Finance9Stage(staged,expectedActive)` comprueba descriptor, cinco stores, marker, READ_ONLY shape Finance9, pointer ID+sequence antes y después, y cierra graph ante stale. FBS2 legado/G4/Planning8 conservados sin crear permisos.
## B3
Se reutilizan los owners de cinco stores y `RealityFinance9SealedReadPort` para columnas y FKs exactas. Referencias SQLite `https://www.sqlite.org/pragma.html` y `https://www.sqlite.org/wal.html`. No datos bancarios reales ni APIs pagas, Chat V2 OFF.
## B4
CI dirigida GitHub Actions **43982348068**, job **133701718863**, completed/SUCCESS, `BUILD SUCCESSFUL in 2m 41s`, `S4_W01_M0C2B2A_FINANCE9_STAGED_GRAPH_PASS 6/6`; verificador XML tests=6,failures=0,errors=0,skipped=0, seis nombres exactos. Artefacto `faro-v5-w01-m0c2b2a-stage9-graph-junit` id **14284350632** existe y no expiró. Guardia obligatoria **43982347978** SUCCESS mismo SHA.
Por ser cambio compartido de RealityStore/GenerationGraph se cotejaron también **19/19 GitHub Actions de la SHA**, todas completed/SUCCESS, incluidos S3 Reality8/recibos/Planning, módulo V2 y Android build. Ningún RED. No reejecutar esas suites sin delta funcional.
## B5
Pruebas nuevas: apertura stage9 con 5 owners y G4/Planning paired; rechazo opener7/8 sobre9; schema8 no se autoconvierte; descriptor/directorio falso; ACTIVE y pointer stale/ABA; sidecar y columna Finance añadida rechazan antes de RW. **No** certificado: restauración FBS2v4 de los cinco owners, recibos archive replay, Finance9 ACL interproceso, Android/G15 físico, F02–F06 UI. Un graph stage9 interno no es grant ni Apply host. W02 STOP.
## B6
Código de admisión STAGED9 verificado. Permiso M0C2B2A consumido BLOCKED. Próximo M0C2B2B integra captura y replay de los cinco stores+Finance9 bajo archivo autenticado, nuevo B1–B5/READY/CI/B7.
## B7
Efectivo sólo tras publicar este B7 en commit DOC_ONLY, guard SUCCESS SHA exacto, writer RELEASED epoch1002 last_checkpoint==HEAD y permiso BLOCKED last_code_review=e587da2e609a67780ba2ce6302627436a05fc132 result PASS. Si falla, pendiente.
