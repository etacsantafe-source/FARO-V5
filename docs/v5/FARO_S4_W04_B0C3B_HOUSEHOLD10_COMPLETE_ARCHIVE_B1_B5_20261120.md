# FARO V5 — W04 B0C3B · Capture / restore integral de FBS2v6 — B1–B5
Fecha local 2026-11-20 (Argentina). Baseline 768c56824d04b051d323988aa2129461d8d16dd6. Unidad candidata S4_W04_B0C3B_HOUSEHOLD10_COMPLETE_ARCHIVE_DEV. PREAUDIT_DOC_ONLY, NO_READY y B6/B7 NOT_RUN. Depende de cierre B0C2 y de B0C3A.

## B1 — Evidencia, diferencia real
- B0C2 ya tiene FBS2v6 autenticado en memoria: v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10Fbs2Codec.kt blob 835263174d9e74c21b12fe216ea295dcd1a371ca; v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStorePortableBackupCrypto.kt blob 3a5207d6cf0b05e7a1aba90832e0ded7837981a5, métodos internos sealWithHousehold/openWithHousehold. CI 44668249894 5/5 SUCCESS y guard 44668249799 SUCCESS, B7 aún PENDIENTE.
- FBS2v6 lleva base FBS2v5 (cinco stores, Finance9 y Auxiliary4 receipts) y sección H10S obligatoria. Ninguna de estas pruebas certifica captura SQL/restore, persistencia Household real, pointer ni generación.
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Auxiliary4ArchiveCoordinator.kt: captureAuthenticated/restoreAuthenticated FBS2v5 aislado, exige Reality9; primero revisa archive, usa pinned identity, repone cinco stores y Finance9, compara snapshot y deja STAGED.
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9FullArchiveCoordinator.kt blob 9ad51fc2f550b7ea30c3959b8c8b4acfb6f22052: patrón de una lease exterior, captura Finance9 sellada, replay por fases, resultado StagedVerified o Blocked; no sirve sin cambios para Reality10.
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt blob 109a993de231f503380b810f4ee54d7d9701c476: los openers Finance9Auxiliary4Stage y el preflight de cinco stores esperan Reality9 exacta, y el RealityStore de producción actual no admite schema10. Falta seam explícita stage10 para leer/comparar todo sin auto-migrar.
- W04-B0B staged9→10 conserva los otros cuatro stores, la fuente y puntero bajo lease. B0C3A deberá aportar puertos SQL H10, sin mover schema ni necesidad persistente por sí solo.
- Requisitos de producto P05 S01/S02 siguen sin host, pero B0C3B es solo integridad de backup/restore DEV.

## B2 — Comparación y reutilización
- V4.4 L15 usaba SmartCompraStore real; no importar sus archivos, ni su esquema, ni recurrencias para montar aquí. V5 conserva cinco stores, la transacción Reality y la separación Evidence/Reality/Finance.
- Reutilizar AEAD/FAROBAK2 actual (sin tocar KDF/nonce/header), codec v5 que incluye recibos Auxiliary4, sección H10S, archivo identity pin, StagedSqlitePreflight y mapeos Finance9. NO reimplementar crypto ni reescribir el replay antiguo sin necesidad.
- No tratar v5 como si tuviera sección Household: solo v6 puede restaurar Household10; v5 con Household ausente requiere decisión EXPLÍCITA de migración a stage10 con Household vacío, jamás silenciosa.

## B3 — Alternativas, decisión falsable
A: coordinador DEV v6 con un solo lease, captura stage10 sellado y replay en stage controlado con snapshot tipado AUTENTICADO, reusando operaciones internas de cinco stores donde admitan snapshot tipado. ELEGIDA para diseño.
B: pasar bytes v6 al restaurador v5: RECHAZADA; su parser exige v5 y se pierde la identidad de archivo del original.
C: descifrar y reencriptar v5 para engañar parser, cambiar pin de archive o usar dos credenciales/archivos: RECHAZADA, rompe traza/semántica e introduce segundo artefacto.
D: intentar abrir un archivo Reality10 por un opener Reality9 o copiar tablas bajo fichero ACTIVE: RECHAZADA.
Fuentes: codebase real de Finance9FullArchiveCoordinator/Auxiliary4ArchiveCoordinator, documentación SQLite de transacción y P02 L11/L15; sin APIs pagas o servidor.

## B4 — Composición y secuencia de B6 propuesta
Bloque funcional: un único H10 FBS2v6 autenticado, cinco stores y Household, sin haber activado generación. Antes de emitir READY, congelar estos seams por SHAs post-B0C3A:
1. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10ArchiveCoordinator.kt (nuevo, coordinación capture y restore).
2. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt (adaptación mínima para snapshot base v5 YA autenticado, sin aceptar bytes falsificados; si excede, separar C3B1/C3B2 con permisos propios).
3. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt (solo admisión stage10/lectura strict, no active productivo).
4. v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10ArchiveCoordinatorTest.kt.
5. .github/workflows/faro-v5-s4-w04-b0c3b-household10-archive.yml.
Si la admisión stage10 requiere cambiar RealityStore u otra ruta no listada, STOP, separar C3B1 para la admisión y C3B2 para replay; PREAUDITAR rutas delta antes de READY. Las cinco rutas son candidatas, NO autorizadas.
CAPTURA: pin full expectedActive; quiescence y root exclusivo; sealed source10 y otros cuatro stores exactos; read Household10 vía C3A, Finance9 DATA por puerto versión10 compatible (si no existe, auditar delta), snapshot de cinco stores con receipts; codificación FBS2v6+AES-GCM; después validar source/pointer sin mutar.
RESTAURACIÓN: autenticar v6 una sola vez ANTES de cualquier cambio, snapshot íntegro y límites; misma identidad de archivo/operation pinada durablemente. Usar target no ACTIVO, independiente, y solo la ruta de replay v5 autenticamente tipada; decidir/admitir explícitamente esquema target9 para base -> migración staged10 (o equivalente demostrado) -> H10 restore con C3A. No reabrir con graph9 si ya es10. Solo STAGED, nunca switch pointer en esta unidad.
VERIFICACIÓN: cerrar todos handles, reabrir snapshots exactos bajo admisión strict (cinco stores, Finance9, Auxiliary4 receipts y H10S), comparar tipado; si cualquier error, Blocked/QUARANTINED sin cambiar pointer, jamás éxito parcial.
Sólo B7 con XML+guard correcto habilita pasar a B0D; no inventar prueba G15.

## B5 — Pruebas adversariales (diseñadas, NOT_RUN)
C3B-T01 source10 con Household 2 filas, Finance9, receipt Auxiliary4; roundtrip export/import v6 y comparación exacta.
T02 0 Household con revision no cero preservada y cinco stores; no implica nuevo gasto ni Apply.
T03 compatibilidad: v5 se abre solo por método v5 y no se acepta como v6 salvo ruta explícita migratoria separada.
T04 passphrase incorrecta/AEAD alterado/truncado/digest/H10 faltante: denegar ANTES de mutar target.
T05 archivo v6 duplicado -> misma identidad/replay; archivo distinto con mismo operationId -> conflicto, no mezcla.
T06 fallo después de alguno de cinco stores y antes de Household: target sigue quarantined y source activo intacto.
T07 falla después de restore H10, antes de verificación: ni pointer move ni declare B7.
T08 ABA/rotación de pointer, generation, lock expirado, symlink/hardlink/sidecars: denegar.
T09 Household restaurado distinto o Finance9/Auxiliary4 faltante: bloqueo global; no mostrar datos parciales como completos.
T10 reintento después de crash: no segundo asiento, no recibos duplicados, no sobrescribir estado distinto.
T11 revisión por error de credenciales/ACL: no filtrar nombres Household o cantidades.
T12 no contaminación W1/W2/W3, no Android productivo, no escrituras app/REST.
B5 PREAUDIT_DOC_ONLY. Guardia documental tras publicación, B0C2 B7 y B0C3A B7 son puertas secuenciales antes de cualquier READY propio.
