# FARO V5 — S3.A2 G4 / FINANCE SEARCH SOURCE · B1–B5

Fecha 05/11/2026. Unidad específica S3_A2_G4_FINANCE_SOURCE_UNMOUNTED. HEAD analizado 2a219ce428a33272665ede00dd557aee0cf6cc7a. El G4 completo sigue BLOCKED por faltar Objetivos/Asuntos; NO se infiere cierre de G4 de esta subunidad.

## B1 EVIDENCIA
- Owner FinanceSnapshotReadApi obtiene FinanceSnapshot(revision,accounts,entries) del propietario de Reality: v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceModel.kt y RealityStore.financePersistence. FinanceEntrySnapshot ofrece entryId, accountId, concept, category, recordedAt; el ledger no contiene permiso de búsqueda.
- SearchCanonicalReadPort y CanonicalSearchCoordinator existentes exigen SearchAuthorityStamp(generation,aclEpoch,ownerRevision), relectura canónica y cobertura completa/parcial. SearchCanonicalDocument.Kind.FINANCE_ENTRY existe. G3/G1/G2 Workspace no se reabren.
- La integración real de ACL/invalidaciones de Finanzas y una pantalla/navegación a asientos no están verificadas. G4 global B5 permanece BLOCKED en S3_A2_G4_FEDERATION_REAL_OWNERS_B1_B5_20261105.md.
- Permiso de código del HEAD anterior BLOCKED. Solo se preaudita un adaptador DE SÓLO LECTURA, no la composición root ni una activación.

## B2 COMPARACIÓN
- V4.4 (S3 backlog / S3_A2_ADAPTER_FEDERATION_VERIFIED_20261104.md) utilizaba búsqueda multidominio con degradación textual. V5 agrega cobertura por fuente tipada y revalidación owner; reutilizar federador, NO copiar bases, credenciales o índice.
- El adaptador WorkspaceSearchReadSource ya separa propietario canónico de proyección; nuevo FinanceSearchReadSource conserva la misma semántica con revision financiera propia, namespace reality.finance.entry y clave opaca por entryId, sin tratar Finance como Workspace.

## B3 INVESTIGACIÓN
- Nextcloud Unified Search https://docs.nextcloud.com/server/stable/developer_manual/digging_deeper/search.html : plugins de búsqueda por fuente; ADAPTAR declaración explícita, resultados parciales, sin dependencia ni red ni uso de código ajeno. No tomar su autorización de cuentas remotas.
- Room FTS https://developer.android.com/training/data-storage/room/defining-data : permite índice local; DESCARTAR en esta subunidad para no duplicar conceptos o importes y no confundir índice con autoridad.
- Android minimización de permisos https://developer.android.com/games/develop/permissions : REFERENCIA de mínimo privilegio, permisos externos no equivalen al ACL de FARO.
- Alternativa A ADAPTAR: SearchCanonicalReadPort alimentado por FinanceSnapshotReadApi, inyecta generation/ACL verificables por host futuro, nunca habilita si faltan, no entrega importes o identificadores de cuenta como texto innecesario. Alternativa B DESCARTAR: falso stamp por defecto, indexar recibos/descripciones vía Workspace o abrir automáticamente resultados financieros en Chat.
- Licencia/mantenimiento: código original FARO, sin nuevas dependencias ni APIs pagas; consultas acotadas, costo de snapshot no certificado aún en dispositivos.

## B4 UNIFICACIÓN
- Implementar SOLO fuente read-only no montada, test JVM propio y su inclusión en CI dirigida existente. Rutas exactas: v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5FinanceSearchReadSource.kt; v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5FinanceSearchReadSourceTest.kt; .github/workflows/faro-v5-s3-home-notice-directed.yml.
- Namespace reality.finance.entry, SearchCanonicalDocument.Kind.FINANCE_ENTRY. Id opaco SHA256 de entryId; title=concept; body=category. No incluir cantidad, moneda, cuenta, concepto privado en hash/source stamp de salida. Stamp almacena solo generación, ACL y revision verificados. Sin índice nuevo ni esquema.
- Limitar cantidad de documentos según maxDocuments, ordenar determinísticamente por recordedAt desc+entryId, devolución PARTIAL al truncar y UNAVAILABLE si se revoca o el owner falla. El caller debe aportar ACL real, generación y recovery admission. No preautorizar UI.
- Sin cambiar Compose, host, permisos, catálogo, app shipping o test físico. Este adaptador solo puede ser registrado tras otro B1–B5 de host financiero auténtico; esa integración NO se aprueba aquí.

## B5 AUDITORÍA
- Negativas: sin generation/ACL/admission -> UNAVAILABLE; cambio entre lecturas -> fail closed; revision cambiada -> fail closed; invalidaciones y regrant son responsabilidad de otro owner; 501 entradas / 500 docs -> PARTIAL; namespace ajeno no se lee; no documento finanzas con monto o cuenta; no navegación por hit.
- El scope no incluye G4 completo y NO puede declararse listo para UI; pruebas de mutación, event bridge, restore físico y Asuntos/Objetivos quedan NOT_RUN. La fuente aislada es segura SOLO porque no se conecta a shipping y exige autoridad externa no nula.
- Dictamen: READY_FOR_BOUNDED_UNMOUNTED_FINANCE_SOURCE_ONLY, con permiso separado por rutas y writer CAS; B6 y B7 pendientes. G4 GLOBAL BLOCKED; G5 campaña única pendiente. Prohibido CI verde == propietario real registrado.
