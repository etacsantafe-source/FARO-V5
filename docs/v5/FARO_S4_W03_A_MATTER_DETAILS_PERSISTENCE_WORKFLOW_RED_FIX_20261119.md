# FARO V5 — W03-A · RED_FIX workflow con preflight exacto

Fecha: 2026-11-19. Unidad: `S4_W03_A_MATTER_DETAILS_PERSISTENCE`. HEAD auditado `2a8cb086cbe850f9706e3bf488c30e98868a0f01`. **B7 del SHA previo RED_FIX (NO PASS).** Alcance del próximo B6: únicamente `.github/workflows/faro-v5-s4-w03-a-matter-persistence.yml`.

## B1
- `AGENTS.md`, protocolo obligatorio siete bloques, puntero, permiso vigente y el preflight de `scripts/quality/faro-protocol-gate.py` recuperados. CODE `2a8cb086cbe850f9706e3bf488c30e98868a0f01`: cambio test-only de W03-A. Guardia `44535644065` **SUCCESS**. Writer global RELEASED epoch 1289 antes de esta preauditoría.
- La CI W03-A [44535644245](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44535644245) **FAILURE** en el paso `Exact-route B1-B5 preflight against code commit parent`. Log: `FARO_PROTOCOL_GATE_STOP: STOP: file outside preaudited unit: v5/owner/matters/src/main/kotlin/ar/com/faro/v5/owner/matters/api/MatterModel.kt`. El permiso padre RED_FIX incluía SOLO `MatterModelTest.kt`, ruta exacta cambiada por `2a8cb`.
- Esta ejecución NO llegó a `:owner:matters:test`, SQLite ni G4. XML de esta ejecución no existe; no atribuirle éxito ni error de lógica. La CI original `44534211526` había encontrado test invertido en línea107, corregido por `2a8cb`.

## B2
- Mecanismo correcto del gate V5 (`faro-protocol-gate.py`): comprueba las rutas FUNCIONALES EFECTIVAMENTE MODIFICADAS por cada commit contra el permiso exacto de su padre. En modo preflight, usa `--head HEAD^`, `--unit`, `--path` para los paths reales. Este workflow W03-A introdujo un loop **estático de las ocho rutas iniciales**, útil para el primer commit pero incompatible con RED_FIX acotado.
- V4.4 seguimiento y W02 Objetivos no justifican ensanchar permisos: conservar semántica append-only y requisitos W03-A; no copiar runtimes ni reabrir W01/W02.

## B3
- `git diff --name-only --no-renames HEAD^ HEAD` documentado en https://git-scm.com/docs/git-diff. **ADAPTAR** extracción local exacta del delta funcional para cada ejecución; `actions/checkout@v4` actual ya usa `fetch-depth: 0`.
- Alternativa A: derivar rutas del diff de este commit y llamar preflight por cada ruta modificada, **ELEGIR**; no hay nuevas dependencias, red ni coste adicional. Alternativa B: volver a poner permiso READY de ocho rutas aunque solo cambió una, **DESCARTAR** por ampliación indebida. Alternativa C: quitar el preflight y confiar en resultado verde sin control, **DESCARTAR**.
- La guardia de siete bloques continúa auditará el historial y cada commit, independientemente del workflow dirigido.

## B4
- Cambiar exclusivamente `.github/workflows/faro-v5-s4-w03-a-matter-persistence.yml`: lectura bash `mapfile -t CHANGED < <(git diff --name-only --no-renames HEAD^ HEAD)`; rechazar conjunto vacío; pasar SOLO rutas funcionales `v5/*|scripts/v5/*|.github/workflows/*` a `python3 scripts/quality/faro-protocol-gate.py --mode preflight --head HEAD^ --unit "$UNIT" --path "$ROUTE"`; exigir al menos una ruta funcional. No reemplazar el gate global ni las suites JVM/XML.
- El nuevo commit modifica **solo workflow**; permiso `READY` limitado a esa ruta; su `last_code_review` debe marcar `2a8cb` RED_FIX con evidencia `## B7` de este documento. Mensaje funcional incluye línea independiente `FARO-UNIT:S4_W03_A_MATTER_DETAILS_PERSISTENCE`.
- Al cambiar el propio workflow, el disparador `on push.paths` que incluye la ruta del workflow ejecutará la CI dirigida otra vez. Mantener `if-no-files-found:error` para no simular XML.

## B5
- Un archivo realmente modificado fuera del permiso debe hacer STOP. El loop no preflight rutas ajenas/no modificadas. Empty diff / solo documentación no se acepta como código funcional en esta unidad. Permisos de diferente unit, RED_FIX de otro unit, B1–B5 blob inválido, o commit mixed con control también fallan por gate. Las rutas nuevas deben pasar la guardia exacta.
- Volver a ejecutar íntegra W03-A: modelo 6/6, RealityMatterDetails 3/3, FaroV5MatterDetailsBackup 2/2, G4 Objective y FBS2 y Finance9 compartidos, verificar XML y hash de artefacto. No transformar FAILED/NOT_RUN en PASS.
- V2 productiva/Auto/Noche OFF, sin G15, sin APIs pagas, sin main/V4.4/W01/W02 escritos. Writer CAS y RELEASED mientras CI corre.

## B7
- El último código `2a8cb086cbe850f9706e3bf488c30e98868a0f01` (solo corrección de test) tiene guardia SUCCESS `44535644065` pero CI dirigida FAILURE `44535644245`, por alcance erróneo del preflight estático. Es **RED_FIX** de la misma unidad, no B7 PASS, JVM NOT_RUN en este SHA. Precisa workflow RED_FIX acotado, CI exacta y B7 independiente.
