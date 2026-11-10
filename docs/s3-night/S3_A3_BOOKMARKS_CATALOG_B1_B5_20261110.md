# Guardados — registro acotado de ocho contratos autorizados
Unidad `S3_A3_BOOKMARKS_CATALOG_REGISTRATION`, fuente 77bbd27c75af6a0e3eb6061720e1fe659ffc41af. Fecha 2026-11-10 AR. Ejecución manual remota GitHub/Actions: executor local UNAVAILABLE, conector y CI comprobados operativos.

## B1 — Evidencia y autorización específica
Autorización humana recuperada 2026-11-09T17:51:03Z «Autorizo» a incorporar acotadamente los ocho search.bookmark.* bajo B5 y commit/CAS, no otra autorización contractual. Pointer dd365d9d conserva condición CATALOG_172_STILL_FROZEN_UNTIL_B7_AND_CAS. Ya certificados backend: backup71/71, portabilidad21/21, mutación separada5/5 y Workspace read10/10. Catálogo actual leído y contrastado:172 operaciones,54 capacidades,0 search.bookmark registrados; perfiles49/70/35/7/5/6.
Inventario Markdown parseado contra baseline:172/172 filas EXACTAS. Golden independiente de metadata-v1 de los172 descriptores (campos ordenados ID,capability,executor,profile,exposure,version; sin modificar generador): Git blob SHA1 300bb32731bcebd6f4aea3f8e923a2341428dcd6. Será verificado por JVM, además del fingerprint Finance existente.
BindingRegistry no enlaza OperationId ausente del catálogo. Esta unidad registra únicamente los ocho aprobados; no monta ejecutor, UI o V2 productiva.

## B2 — Reutilización y V4.4
Reutilizar comparación P00/A3: Saved Views y pin-chat no son Guardados. Reutilizar N-018/executor federated-search ya canónicos,54 identidades y SpecBridge. No crear nueva capability/store ni renombrar172 contratos existentes. Contratos BookmarkOperationIds/profiles ya tienen8/8 B7 y no se repiten.

## B3 — Alternativas externas reales
Fuentes primarias consultadas: OpenAPI Generator https://github.com/OpenAPITools/openapi-generator (Apache-2.0, generación desde spec y advertencia explícita sobre inputs/templates no confiables) REFERENCIA de schema-first; no añadir dependencia ni importar código. Protobuf https://protobuf.dev/programming-guides/proto3/#updating documenta identidad estable y peligro de reutilizar identificadores; REFERENCIA para mantener IDs/perfiles/fingerprints viejos, sin cambiar formato a Protobuf.
(A) agregar descriptores a mano en BuiltInContractCatalog DESCARTAR: ignora fuente congelada y baseline; (B) cambiar generator/version global DESCARTAR: delta innecesario; (C) enmienda declarativa exacta8 + baseline derivado y verificado por SpecBridge existente USAR. Sin red/servicios/dependencias/coste en FARO. Proyectos mantenidos, documentación/patrón solamente.

## B4 — Delta y aceptación
Añadir filas N-018/federated-search/BINDABLE/NEW_V5:
- search.bookmark.read — READ_ONLY
- search.bookmark.add — AUXILIARY_MUTATION
- search.bookmark.lifecycle.set — AUXILIARY_MUTATION
- search.bookmark.favorite.set — AUXILIARY_MUTATION
- search.bookmark.folder.create — AUXILIARY_MUTATION
- search.bookmark.folder.rename — AUXILIARY_MUTATION
- search.bookmark.folder.delete — AUXILIARY_MUTATION
- search.bookmark.folder.assign — AUXILIARY_MUTATION
Total180, capacidades54; perfiles50/70/42/7/5/6 (REALITY_MUTATION sigue70). Enmendar autoridad inventario/refreeze con procedencia de aprobación posterior al freeze D-229; conservar historia y versión metadata-v1. Baseline actualizado desde autoridades, verificación OFICIAL Gradle verifySpecBaselineFresh y generator runtime en CI; no editar clase runtime generada. Antes de publicar se comprobará que172 filas previas permanecen idénticas.
Actualizar BuiltInContractCatalogTest: conteos180 y perfiles; conservar golden Finance; dos pruebas nuevas: ocho IDs exactos/perfiles/executor/exposure y golden metadata de172 anteriores. Cuatro JUnit en total. Workflow propio ejecuta únicamente spec-fresh y esa clase, XML4/4.
Rutas limitadas: docs/v5/V5_OPERATION_CONTRACT_INVENTORY.md, docs/v5/V5_RG06C_07_GLOBAL_REFREEZE.md, v5/spec/generated/contract-baseline.tsv, v5/kernel/runtime/src/test/kotlin/ar/com/faro/v5/kernel/runtime/BuiltInContractCatalogTest.kt, .github/workflows/faro-v5-s3-a3-bookmark-catalog-directed.yml.

## B5 — Auditoría previa
READY solo para esos8 registros previamente autorizados. Los172 existentes no cambian campos/identidad/fingerprint; no cambiar generador, metadata version, capas, binding, permisos o perfiles de Reality. Exponer descriptor BINDABLE no otorga autorización ni implementa ejecución: gates de mutation siguen default deny y host no montado. Las7 nuevas escrituras son AUXILIARY_MUTATION, jamás COMMITTED Reality. Runtime resolver faltante no se sustituye con bypass directo.
Preflight obligatorio en Actions sobre el commit documental READY, verificar log de faro-protocol-gate antes de escribir código, adquirir CAS nuevo para implementación y liberar durante CI. Guard rojo => STOP/RED_FIX. B7 exige spec derivada fresca y4/4 reales, no solo conteo. Sin APK/dispositivo ni API paga. V2 productiva/S4/Auto/Noche OFF. B6/B7 NOT_RUN.
