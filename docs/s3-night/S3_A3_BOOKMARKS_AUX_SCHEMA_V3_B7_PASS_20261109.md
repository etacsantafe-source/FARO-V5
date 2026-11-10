# S3.A3 — Auxiliary schema v3: B7 PASS_SCOPED
Fecha AR: 2026-11-09. Código `1b9fa3e8f51d8705678716cee19ce213b3bca29b`; rama `work/s3-night-20261102`.

## B7 — Certificación independiente de migración
- GitHub Actions [42726963284](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42726963284), job `129534935659`: **completed/success**, `BUILD SUCCESSFUL`, XML `AuxiliaryBookmarkSchemaV3Test` **5/5** y `AuxiliaryDeliveryStoreTest` **16/16**, 0 failures/errors/skipped; marcador de log `S3_A3_BOOKMARK_SCHEMA_V3_JVM_PASS 5/5 NEW`.
- Regresión Home/Avisos [42726963263](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42726963263), job `129534939932`: **completed/success**. XML 11 Home, 7 Workspace Search, 6 Finance unmounted, 7 Search bridge, 2 G3, 4 Avisos, 6 chat-pointer ABA, 1 active-host ABA, 10 search-host grant, 10 G2, 6 Reality Activity; merged manifests **2 PASS**. Esta matriz es regresión indirecta, no Guardados de UI.
- Guardia [42726963116](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42726963116): SUCCESS sobre SHA exacto. Permiso documental padre `421ca5337e4d99d5a71e6924998cf40391005f05`.
- Diff limitado a `AuxiliaryDeliveryStore.kt`, dos tests JVM y workflow dirigido. El esquema v3 crea cuatro tablas y 2 índices en la misma DB Auxiliar; v0/v1/v2 migran vía transacción `BEGIN IMMEDIATE`, se conservan avisos y la validación exige columnas, no introduce tabla de mensaje ni raw authority token.
- **B7 = PASS_SCOPED** SOLO para `S3_A3_BOOKMARKS_AUXILIARY_SCHEMA_V3`. Aún NO hay persistencia operativa, autoridad por generación/ACL inyectada, CAS de mutación, backup/restore/portabilidad de bookmarks, registro catálogo ni UI. Se congela este corte, no repetir 5/5 certificados. G15 físico pendiente de campaña final. V2 productiva/S4/Auto/Noche OFF, catálogo 172.

## Continuidad
Siguiente unidad independiente: `S3_A3_BOOKMARKS_AUXILIARY_CAS_RECEIPTS`, preauditar y habilitar CAS+recibos de metadata con autoridad externa **deny-by-default**; ninguna operación se monta hasta backup y real ACL. Writer liberado luego del commit documental; no retenerlo durante CI.
