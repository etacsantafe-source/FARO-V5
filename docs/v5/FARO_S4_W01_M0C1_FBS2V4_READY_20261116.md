# W01-M0C1 Finance9 FBS2v4 codec + legacy restore DENY — READY
Fecha 2026-11-16 AR. Unidad: S4_W01_M0C1_FINANCE9_FBS2V4_CODEC_FENCE. Reviewed HEAD a8dec11ad6210daa87a10646a8b64bba2be568b0, guard 43961104348 completed/SUCCESS, writer RELEASED epoch987 y last_checkpoint igual. B1–B5 verificados por archivo docs/v5/FARO_S4_W01_M0C1_FINANCE9_FBS2V4_CODEC_B1_B5_20261116.md, blob 8df15dd9604435bea69e9cf04ae7d454c2ef6bcb.
## B1
FBS2v3 carece de Finance9, base legacy no basta; ruta public restore debe bloquear v4 antes de archive pin. Fuente de inventario exacta preauditoría.
## B2
Reusar FBS2 v1/v2/v3/WSB2/G4/Planning/FAROBAK2 existentes y no repetir tests certificados. No tocar código Android ni schemas7/8/9.
## B3
Whitelist ocho tablas Finance9 sin tablas ACL; contenedor FBS2v4 con sección SHA256 y envelope AES/GCM existente, nunca confundir integridad con autenticidad.
## B4
READY exclusivamente cinco rutas:
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9BackupCodec.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreSemanticCodec.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9Fbs2V4CodecFenceTest.kt
- .github/workflows/faro-v5-s4-w01-m0c1-finance9-codec-directed.yml
Una clase test nueva con seis XML verificables, workflow dirigido; NO capture/restore9 real hasta M0C2.
## B5
Denegar ausencia G4/Planning, corrupción, duplicados, ACL export, v4 en restaurador viejo antes de writes/pin. No shipping, no G15, no F02–F06 aún.
## B6
READY_TO_IMPLEMENT after exact-path preflight + CAS writer; funcional y permiso jamás mismo commit.
## B7
PENDING_FINAL_AUDIT, requiere seis tests JUnit XML PASS, guard SHA, diff exact cinco archivos, RELEASED y permiso CONSUMED.
