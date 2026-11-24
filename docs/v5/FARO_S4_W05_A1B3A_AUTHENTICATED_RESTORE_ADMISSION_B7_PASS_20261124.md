# FARO V5 · W05-A1B3A · B7 PASS · FBS2v8 authenticated restore admission (read-only)

Fecha: 2026-11-24, Argentina. Rama `work/s3-night-20261102`.
Unidad: `S4_W05_A1B3A_AUTHENTICATED_RESTORE_ADMISSION_DEV`.

## B1–B5 — Autorización verificada

Preauditoría `docs/v5/FARO_S4_W05_A1B3A_FBS2V8_RESTORE_ADMISSION_B1_B5_20261124.md`, blob `cd9b70ee5cc1849931a5d7ed625c7d37c030fc86`, commit `e43a27596c14f1dd70a66e377838e71ed4487a8f`, guard `45437310828` SUCCESS. READY exact3 `025261d94603257ff7f148f4cc5d51db6393be25`, guard `45437474931` SUCCESS.
A1B0/A1B1/A1B2A/A1B2B B7 ya estaban certificados. No repetir ni reabrir. Entrada desde checkpoint `0e9c4595fe8b5f99e7386745b1c11d66fc7780ce`, writer RELEASED época 1830.

## B6 — Código, guardia, pruebas y XML original

Commit de código **`55150d70c66e3bb2df61d0ba356d6db69cca23b6`**, exactamente tres rutas:
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8RestoreAdmission.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8RestoreAdmissionTest.kt`
- `.github/workflows/faro-v5-s4-w05-a1b3a-restore-admission.yml`

Guardia de código `45437871066` COMPLETED SUCCESS. CI dirigida `45437871429`, job `138600521996` COMPLETED SUCCESS, SHA exacto del código. Preflight parent READY exact3 PASS para las tres rutas. Gradle `BUILD SUCCESSFUL in 2m 41s`. Marcador de workflow `FARO_W05_A1B3A_FBS2V8_RESTORE_ADMISSION_XML_PASS_8_8`.
Artefacto original GitHub `14941178322`, nombre `w05-a1b3a-fbs2v8-restore-admission-xml`, ZIP de 853 bytes, digest reportado `sha256:560e7464327faeec5e0ff3f271af15e6bfd35ab0aff9ab156149171fefc8d2a1`. ZIP descargado en runtime de auditoría e inspeccionado con `zipfile` + `xml.etree.ElementTree`: un archivo `TEST-ar.com.faro.v5.generation.FaroV5Household11V8RestoreAdmissionTest.xml`, **tests=8, failures=0, errors=0, skipped=0**, exactamente ocho `testcase` y ninguna `failure`/`error`.

## B7 — Auditoría independiente de alcance

**PASS_SCOPED_W05_A1B3A_FBS2V8_RESTORE_ADMISSION_XML_8_8**.

Pruebas de contrato observadas:
- Admisión de FBS2v8 autenticado en destino Finance9/Auxiliary4 sellado y sin cambio de bytes/puntero; SHA256 exacto de ciphertext, origen/target separados, manifest y evidencia tipada; solo metadatos no-capability devueltos.
- Dos recibos parciales de 7000 y 3000; revisión con cero filas conservada, sin stock sintético.
- Password incorrecta, tag y header alterados -> rechazo; FBS2v5/v6 rechazos sin usar fallback.
- Igualdad source-target y destino ACTIVE rechazados; destino con Reality11 o Auxiliary3 rechazado para etapa inicial; alias hardlink y sidecar WAL rechazados; host revocado y ABA del puntero exacto denegados.
- No se escribió SQL, no se creó journal, no se montó Android, no se ejecutó Apply, no se activó generación ni stock real. No se mutaron codecs ni owners anteriores.

**NO autorizado por B7:** replay de los cinco stores, copia/capture SQL11 integral, identidad del ciphertext vinculada durablemente a un operationId/target, reanudación tras crash en etapa 10/11, cutover/reopen de generación o activación de inventario. Para eso seguir en **W05-A1B3B** (replay y pin durable) y **W05-A1B3C** (switch/reopen/recovery), después W05-A1C (Apply/stock). El preflight no es un permiso de restauración. V2 productiva/pagos/Auto/Noche/stock real siguen OFF.

Permiso A1B3A se consume a BLOCKED, writer debe quedar RELEASED con target HEAD exacto tras puntero y última guardia SUCCESS. No iniciar código A1B3B sin B1–B5/guard/READY propios.
