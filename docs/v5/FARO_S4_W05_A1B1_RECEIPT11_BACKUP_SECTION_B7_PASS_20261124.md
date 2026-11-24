# FARO V5 · W05-A1B1 · B7 PASS · Sección de respaldo tipada Reality11

Fecha: 2026-11-24 (Argentina). Unidad `S4_W05_A1B1_RECEIPT11_BACKUP_SECTION_DEV`. Rama `work/s3-night-20261102`.
Código B6 `23621ac756057fe865439c97dd9cac06a37ccdb7`; READY parent `6dbaeb1c4f6465009b0ed0b787b2eea589e28561`.

## B1–B5 — Evidencia original

Preauditoría `docs/v5/FARO_S4_W05_A1B1_RECEIPT11_BACKUP_SECTION_B1_B5_20261124.md`, blob `5d88772c640a44d00b48da87ac2c996087ecc34d`, guardia documental `45416878729` SUCCESS. Permiso READY exact3 en `6dbaeb1c4f6465009b0ed0b787b2eea589e28561` con guardia `45417144168` SUCCESS. A1B0 B7 PASS 8/8 desde checkpoint `92c8ab2f71a1f30a4fc18d8afaf24d7cf1947ebf`; no repetir W04, A0/A1A/A1B0.

## B6 — Código exacto y CI original

Commit `23621ac756057fe865439c97dd9cac06a37ccdb7` cambia exactamente 3 rutas:
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11ReceiptBackupSectionCodec.kt`
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11ReceiptBackupSectionCodecTest.kt`
3. `.github/workflows/faro-v5-s4-w05-a1b1-receipt-backup-section.yml`

Guardia del código `45417574883` **COMPLETED SUCCESS**; preflight exact3 contra READY parent PASS. CI `45417574919`, job `138527901318`, SHA exacto `23621ac756057fe865439c97dd9cac06a37ccdb7`, **COMPLETED SUCCESS**. Gradle `BUILD SUCCESSFUL in 3m 40s`. Verificador real JUnit XML: `tests=12, failures=0, errors=0, skipped=0`, marcador `FARO_W05_A1B1_RECEIPT11_BACKUP_SECTION_XML_PASS_12_12`, archivo original preservado.
Artefacto GitHub `14933995088`, `w05-a1b1-receipt11-backup-section-xml`, 915 bytes, digest reportado `sha256:e85d91daa3456825411c3e826b7d50e2143be88ed0b6a2f6d844769960b4fb2b`. No se afirma rehash independiente del ZIP. V2 core dirigida `45417574381` estaba RUNNING en la lectura posterior a CI; no se hereda un PASS que todavía no se verificó.

## B7 — Auditoría adversarial de alcance

**PASS_SCOPED_W05_A1B1_RECEIPT11_BACKUP_SECTION_XML_12_12.**

Se certifica **solo** codec puro de sección de eventos v11: `H11R`/FORMAT1/SCHEMA11, revisión global ≥0, filas ordenadas, duplicados de receipt_id/intent_key denegados, unidades/fechas/cantidades/revisiones SQL11 verificadas, bytes UTF-8 estándar canónicos, máximo 8192 eventos y 4MiB, corrección de truncados/headers/longitudes/counts/payload corrupto, sin inventar stock. Entrada y salida deterministas.

**No certificado:** autenticación/cifrado por contenedor FBS2 de versión nueva; snapshot SQL/restore v11, distinción de la generación real vs temporal, apertura/cutover activo, writes reales de stock, review/Apply/CAS/Undo, recibo idempotente de compra, restore tras reboot, móvil G15, V2 productiva. La sección v11 no se añade a FBS2v6/v7: formatos históricos permanecen intactos. La identidad de compra debe ser verificada por el futuro puerto de Apply y no por el codec.
Siguiente unidad **W05-A1B2A**: contenedor authenticated FBS2 versionado para secciones v11 y Household10 preexistentes; después **A1B2B** capture/restore SQL11 y **A1B3** cutover/reopen por separado con pruebas negativas de sello/generación. Cada uno requiere B1–B5/guardia/READY propios. No activar pagos, egress, Auto/Noche ni V2 productiva.
Consumir permiso a BLOCKED; writer RELEASED después del checkpoint.
