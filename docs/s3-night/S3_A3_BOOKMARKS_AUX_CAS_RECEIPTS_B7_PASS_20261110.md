# S3.A3 Guardados — Auxiliary CAS/recibos, B7 PASS_SCOPED

Fecha: 2026-11-10. Rama `work/s3-night-20261102`. Unidad `S3_A3_BOOKMARKS_AUXILIARY_CAS_RECEIPTS`.

## B7 — Certificación exacta
- Código: `97fc4492b1a97fe305fc9251cae13a21fbd1dd2d`, RED_FIX de la compilación fallida 42728500403.
- Diff revisado: propiedades Kotlin cross-module estabilizadas como locales inmutables (cursor, mutation, folderId); `bookmark_scope` se inserta ANTES de la primera fila hija, dentro de la transacción; abortos siguen con ROLLBACK.
- CI dirigida [42729311671](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42729311671), job `129542965159`: completed/success; BUILD SUCCESSFUL y verificador XML `S3_A3_BOOKMARK_CAS_RECEIPTS_JVM_PASS 8/8` (ocho pruebas, sin failures/errors/skips).
- Guardia protocolo B1–B7 [42729311925](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42729311925), job `129542969813`: SUCCESS.
- Regresión Home [42729312050](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42729312050), job `129542970662`: SUCCESS.
- Anterior certificado reutilizado: contratos tipados 8/8 en 42720699353; esquema Auxiliary v3 5/5 y Delivery 16/16 en 42726963284.

**B7 = PASS_SCOPED**, únicamente para metadatos Guardados Auxiliary CAS/recibos/orden FK. NO cerrar S3.A3 completo: falta respaldo/restauración, portabilidad, autoridad Workspace real por generación, integración operaciones y UI. No habilitar catálogo 180 ni V2 productiva. G15 físico NOT_RUN.

## Continuidad
La siguiente unidad productiva es S3.A3 backup/restauración/portabilidad segura; validar dependencia de ACL/generación en B1–B5 y emitir permiso documental limitado ANTES de nueva escritura funcional. No repetir B1–B7 ya certificados de esta unidad ni CI vigente salvo cambios relevantes. Catálogo oficial 172; ocho contratos Guardados aprobados pero no montados. S4 formal, Auto/Noche OFF.
