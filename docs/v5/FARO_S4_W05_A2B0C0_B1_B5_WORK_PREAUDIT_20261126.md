# FARO V5 · W05-A2B0C0 · Lectura SQL14 ACTIVE DEV cerrada

Unidad: `S4_W05_A2B0C0_SQL14_ACTIVE_DEV_READ_HOST`. Base c4d1901549005e7fdb990eb5b83aba62099eb01c. Estado **PREAUDITADO CONDICIONAL, NO_READY; requiere B7 predecesor y delta audit**. Evidencia B1–B5 de diseño, NO son resultados de tests. V2 productiva, stock real, pagos, Auto/Noche OFF. Moto G15 NOT_RUN.

## B1 — Evidencia

Entrada: A2B0B3 B7 certificado. Host y grafo SQL13 ACTIVE WAL-aware existen y son read-only; versión14 todavía no admitida. Falta apertura tipada sin inferir cutover.

## B2 — V4.4 y V5

Adaptar FaroV5Household13ActiveDevReadHost, RealityStore y graph, conservando V4.4 separación de lectura/escritura. Reusar lease, puntero secuencia+ID, Evidence2 y checksum.

Matriz V4.4: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md`; reutilización: `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`. No copiar legado ni repetir B7 previo.

## B3 — Soluciones externas

ADAPTAR https://www.sqlite.org/isolation.html y https://developer.android.com/topic/architecture/data-layer/offline-first; REFERENCIA Room transaction https://developer.android.com/reference/androidx/room/Transaction.html sin dependencia nueva. DESCARTAR lectura directa de UI y immutable=1 sobre WAL ACTIVE.

Sin nuevas dependencias, servicios ni APIs pagas. Ante límites reales, retener control local y pruebas adversariales; los proyectos externos son patrones/referencia, no permisos.

## B4 — Diseño y límites

Agregar opener interno SQL14 en DEV que exige root lease, puntero full y selección ticket COMPLETED (cuando exista). Reconoce schema14, historial13 y five-store; expone solo snapshot de físico y reserved, **available desconocido** hasta A2C. No puede efectuar Apply ni mover puntero; debe fallar cerrado si ticket no existe.

**Rutas candidatas; reconfirmar tras B7 de dependencia, sin READY anticipado:**

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household14ActiveDevReadHost.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
4. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household14ActiveDevReadHostTest.kt`
5. `.github/workflows/faro-v5-s4-w05-a2b0c0-active-read.yml`

No main, V4.4, ni activación de stock productivo. Toda ruta adicional obliga a rehacer solo el delta del permiso.

## B5 — Pruebas negativas propuestas

- T01 SQL14 seleccionado realmente, WAL activo, lectura sin mutación.
- T02 SQL13/SQL14 STAGED o ticket inexistente NO abre ACTIVE14.
- T03 ABA/lease ajeno/ACL revocada => denegado.
- T04 Reopen consistente y 5-store intactos.
- T05 Nunca mostrar available ni autorizar reserve por solo lectura.

Exigir guardia 7 bloques documental SUCCESS, evidencia blob SHA, permiso READY individual y preflight de **cada ruta** antes de B6; código `FARO-UNIT:S4_W05_A2B0C0_SQL14_ACTIVE_DEV_READ_HOST` en línea propia, CI del SHA real, XML JUnit original tests>0 y failures=errors=skipped=0, B7 independiente, permiso consumido, writer CAS RELEASED. Para sucesores hay que reconfirmar HEAD y contrato real después del predecesor. Nunca declarar B7 desde este documento.
