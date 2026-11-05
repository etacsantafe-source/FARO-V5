# FARO V5 S3.A2/G4 — Finance canonical entry target · B1–B5 (05/11/2026)

**Unidad estricta:** `S3_A2_G4_FINANCE_CANONICAL_ENTRY_TARGET`. HEAD revisado `ea9fd1d3c676ff19ed5ec74d738ce2f84ccab4e5`. Separada del Finance source B7 existente y de G4 global OPEN.

## B1 EVIDENCIA
- `FaroV5FinanceSearchReadSource` EXISTE, fue aprobado B7 solo como fuente no montada en `302b92c...`; `FinanceSnapshotReadApi` lee Reality, revisión canónica y ACL/generación/admission inyectadas. Exposición indexada no incluye monto, ID crudo ni cuenta.
- G1/G2/G3 CLOSED; `FaroV5WorkspaceSearchSurfaceAdapter` solo navega a conversación, no a asientos. `FaroV5SearchReadPort` todavía carece de navegación tipada. No modificarlo ni construir navegación financiera por Chat.
- `SearchAuthorityStamp` compara generación, ACL epoch y ownerRevision. `SearchProjectionKey` tiene namespace + ID opaco. Finance source NO posee resolver de ID de asiento autorizado.
- El nuevo contract owner foundation Objetivos/Asuntos pasó 8/8 en run 41968020623; B7 doc `S3_A2_G4_OWNER_CONTRACT_FOUNDATION_B7_20261105.md`. No se toca ese PASS ni Reality schema5.

## B2 COMPARACIÓN
- `FaroV5WorkspaceSearchReadSource.resolveAuthorizedConversation` reconsulta metadata canónica y revalida stamp; el caso Finance necesita además el stamp EXACTO de la página original y la comprobación de emisión en un futuro host (anti-regrant).
- V4.4 `FaroV4GlobalSearch.kt` gestionaba resultados multidominio y navegación específica; V5 no reutiliza sus permisos ni mapea FINANCE_ENTRY a conversación.
- El objetivo es añadir sólo el resolver de ID REAL bajo Reality con permisos que permanecen propios, sin montar un host falso ni emitir `CONFIRMED` para una pantalla inexistente.

## B3 INVESTIGACIÓN
- Nextcloud Unified Search https://docs.nextcloud.com/server/stable/developer_manual/digging_deeper/search.html: registrar fuente y resolver específicamente por su propietario; **ADAPTAR** relación fuente/resultado, no su red ni permisos.
- SQLite schema/migraciones https://sqlite.org/lang_altertable.html: mutaciones sobre el dueño necesitan transacción/migración; **DESCARTAR** cambio de esquema para esta unidad de sólo lectura.
- Android Room migrations https://developer.android.com/training/data-storage/room/migrating-db-versions: no recurrir a migración destructiva para resolver un locator; **DESCARTAR** toda escritura/migración.
- Alternativa A: resolver `SearchProjectionKey` con un `expectedAuthority: SearchAuthorityStamp` procedente de publicación, volver a leer FinanceSnapshot real, verificar revisión, generación, ACL antes/después; **ELEGIDA**. Alternativa B: ID desde hit sin stamp/publicación y acceso directo a Finance; **DESCARTADA** (stale ACL, filtración). Alternativa C: abrir movimiento en Chat; **DESCARTADA** por autoridad cruzada.
- Sin APIs pagas, dependencias nuevas o código externo.

## B4 UNIFICACIÓN
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5FinanceSearchReadSource.kt`: `resolveAuthorizedEntryId(key, expectedAuthority): FinanceEntryId?`; namespace no esperado, owner cerrado, ACL/generation/revision diferentes o mismatch => null. El resultado es un ID interno para un futuro Finance host, NO permiso de UI.
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5FinanceSearchReadSourceTest.kt`: 2 tests con ID opaco real/namespace ajeno y revoke/regrant + revisión + generación + owner closed, preservando 4 existentes.
- `.github/workflows/faro-v5-s3-home-notice-directed.yml`: ajustar SOLO XML Finance de 4/4 a **6/6**, sin modificar otros gates.
- Tres rutas exactas, sin cambiar Compose/host/selection, almacenes, backup, permisos de producción, V4.4, main, S4 ni Auto/Noche.

## B5 AUDITORÍA
- Negativas: un `expectedAuthority` previo a revoke/regrant NO recupera acceso aunque coincida hash; nuevo stamp no concede acceso a key no publicada por el host; revisar namespace antes de consultar.
- Verificar doble lectura de revisión contra `expectedAuthority` y ACL/generación/admission, fail-closed si el RealityStore cierra. El resolver no devuelve documento ni concepto/monto, solo ID interno y sin persistirlo.
- ABA con revisión reciclada requiere rotar owner-issued ACL en recovery; responsabilidad de host real, no puede quedar probado por tests de este source. La futura navegación deberá revalidar publicación, token de emisión y punto de llegada; sin ello G4 global OPEN.
- B5 aprueba **READY_BOUNDED_INTERNAL_FINANCE_KEY_RESOLUTION_ONLY**. G4 global **NO** está READY, Objetivos/Asuntos sin owner persistente y Finance todavía sin host ACL/navegación propia. La campaña física G5 NOT_RUN.
