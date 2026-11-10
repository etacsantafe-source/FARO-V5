# Guardados — portabilidad B7 PASS_SCOPED
Unidad `S3_A3_BOOKMARKS_PORTABILITY`. Goal S3_A3_GUARDADOS, B7 DONE_VERIFIED backend. Fecha 2026-11-10 AR.

## B7 — Evidencia contrastada
Código `d529e632bb2c19bb72510db7b0e24ad00ae12046`; [CI 42922295674](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42922295674), job 130199897219 SUCCESS. Log/XML: AuxiliaryBookmarkPortability 4/4, BookmarkPortability 4/4, AuxiliaryPortabilityPort 1/1, PortabilityCore 6/6 y PortabilityExternalWrite 6/6; cero failures/errors/skips. Marcadores reales `S3_A3_BOOKMARK_PORTABILITY_NEW_PASS 8/8`, `S3_A3_BOOKMARK_PORTABILITY_TOTAL_PASS 21`. BUILD SUCCESSFUL. Guardia 42922295405 SUCCESS.

Revisión independiente de diff vs aceptación: DTO explícito sin texto/token/digest de autoridad/receipt; solo ACTIVE con scope y target autorizados antes/después bajo lock, sin defaults de permiso ni éxito vacío al faltar owner. BOOKMARKS opt-in, RESTRICTED, advertencia metadata; all() conserva cinco secciones históricas para no ampliar consentimiento, NOTICES no lee Guardados. JSON válido con referencias exactas/carpetas, preview por watermark y aprobación previa; renombre/favorito cambian hash y SOURCE_CHANGED bloquea staging. Sin nuevo ejecutor, destino externo automático ni registro de operación. Backup permanece separado y restaurable por AEAD; JSON abierto declara NOT RESTORABLE.

Alcance: puerto real Auxiliary + integración de preview/serializer/staging, NO host Android ni ACL Workspace montada. No afirmar S3.A3 cerrado. Siguiente dependencia de seguridad observada: BookmarkAuthorityGate.isScopeActive alimenta tanto lectura como mutación; antes de vincular un grant real de lectura hay que separar permiso de escritura, default deny y revalidación pre-commit. Después owner Workspace y montaje seguro. Catálogo 172, V2 productiva/S4/Auto/Noche OFF; G15 NOT_RUN.

`expected_head=d529e632bb2c19bb72510db7b0e24ad00ae12046`; `acceptance=21/21 XML;8 nuevos`; `evidence=42922295674,42922295405`; `next_step=S3_A3_BOOKMARKS_MUTATION_AUTHORITY B1-B5`; `blocker=NONE_WITHIN_THIS_PORTABILITY_BACKEND_SCOPE`; `writer_release_epoch=676`; `last_observed_at=2026-11-10`.

Regresiones automáticas verificadas para d529e632: backup 42922295595 SUCCESS, CAS 42922295652 SUCCESS, schema v3 42922295455 SUCCESS, Home 42922295537 SUCCESS. No relanzadas manualmente. También los siete runs del backup 196a48fa concluyeron SUCCESS (incluido V2 core 42920843991).
