# FARO V5 — S3.A2 G3 búsqueda de contenido canónico · verificado 04/11/2026

## Evidencia nueva exacta y no duplicada

- Rama funcional: `work/s3-night-20261102`; código real: `b82cda31feec33578d54affc2038e0169e3fbd39`. Baseline anterior: `247de9a3ea356383602d71acf54751da046b7c76`.
- GitHub Actions run **#41834770502** **SUCCESS**, **exacto código b82cda31**. Job `app-home-notice` SUCCESS; `BUILD SUCCESSFUL in 3m 4s`; XML verificado en LOG de salida (no solo impresiones del script):
  - `S3_HOME_NOTICE_XML_PASS 9/9`
  - `S3_WORKSPACE_SEARCH_XML_PASS 5/5`
  - `S3_SEARCH_BRIDGE_XML_PASS 5/5`
  - `S3_AVISOS_XML_PASS 4/4`
  - **23/23 pruebas dirigidas**, 0 fallas; **NO** certificación Android end-to-end.
- Trabajo previo que se conserva SIN repetir: Avisos ACTIVE Auxiliary + UI, `a72b7234` run #41828757767 21/21; UI Avisos `a4327d77` run #41828553243 50/50; A2 idempotencia emisión + invalidaciones run #41827910601 17/17.
- Escritor único mediante CAS GitHub, epoch 119 liberado después del código; sesión documental epoch 120 debe quedar RELEASED. CLOUD queue STAGED activation_authorized=false; Auto y Noche OFF.

## Cambio funcional concreto

1. `ConversationWorkspaceStore.kt`: nuevo `WorkspaceSearchText` y lectura canónica `readSearchableConversationText()` bajo mutex SQLite. Solo texto de USER/ACCEPTED y FARO/COMPLETED; excluye borradores, PROCESSING, PARTIAL, FAILED, OFFLINE. Scans acotados a 16000 caracteres máximo por lectura y retornan `complete=false` ante truncamiento. No esquema/backup/restore modificado.
2. `FaroV5WorkspaceSearchReadSource.kt`: añade contenido autorizado de mensajes al documento de conversación. Límite 4096 caracteres por conversación, 64000 acumulados, hasta 500 conversaciones; conserva comprobaciones de generación/ACL/revisión antes y después. Truncamientos generan cobertura PARTIAL, NO falso «no encontrado».
3. `FaroV5WorkspaceSearchReadSourceTest.kt`: prueba de palabra encontrada únicamente en mensaje real persistido con MatchField.BODY; prueba de mensaje largo que devuelve PARTIAL tanto sin coincidencias como con título.
4. `.github/workflows/faro-v5-s3-home-notice-directed.yml`: verificador ajustado a nueva batería 5/5 para Workspace, preserva las otras tres suites. GitHub compiló y verificó el XML.

## Investigación y seguridad

- Se comparó READ_ONLY V4.4 `app-v4/src/main/kotlin/ar/com/faro/v4/conversation/FaroV4GlobalSearch.kt`: fuente canónica por dominio, tolerancia de términos y cobertura. No se portó su persistencia ni se añadió segunda autoridad.
- **Bloqueo G1 no resuelto:** el host shipping de V1 carece del quinto Workspace owner y del emisor ACL real independiente con epoch observable de revocación/regrant; los tests aportan ACL falsa solo como fixture. `FaroV5BootstrapActivity` mantiene Search `null`. V2 de app permanece desactivada. No presentar esta implementación como búsqueda activada.
- **Queda G3 incompleto:** este bloque cubre contenido canónico dentro del resultado de conversación, **no** un buscador in-chat con navegación al mensaje exacto, ni paginado exhaustivo. La cobertura PARTIAL es deliberada y honesta.
- **G2 pendiente:** wirear `FaroV5SearchReadPort` solo al owner activo autorizado con señales de ACL, generación y recuperación. En V1 sin Workspace debe seguir indisponible.
- **G4 pendiente:** propietarios reales de Finanzas/Objetivos/Asuntos y federación de sus lecturas, sin falsos resultados `COMPLETE`.
- **G5 pendiente:** CI integral X01–X12, campaña física única al finalizar S3. Pruebas físicas **NOT_RUN**.
- S2 CLOSED 8/8 congelado; S3 abierto; S4/V2/Auto/Noche OFF; operaciones 172 oficiales, Guardados +8 candidatas no autorizadas. Sin cambios en main/V4.4 ni APIs pagas.
- Tiempo efectivo humano de desarrollo NO_MEDIDO. Tiempo de CI ~3m 4s de build, no inferir productividad.

## Siguiente acción dentro del híbrido

Prioridad única **S3.A2**: G1 descubrir/empezar autoridad ACL auténtica observable del Workspace y separación entre ACL, generation pointer y readiness; no agregar un contador ACL falso. En paralelo solo trabajo directamente útil para cerrar A2 (navegación in-chat canónica de G3 o pruebas adversariales pertinentes) y regresar a G1/G2; luego G4. Al final de cada sesión: exact SHA + run + XML + checkpoint + writer RELEASED. Handoff automático con solo `Continuar FARO V5`, termómetro cualitativo.
