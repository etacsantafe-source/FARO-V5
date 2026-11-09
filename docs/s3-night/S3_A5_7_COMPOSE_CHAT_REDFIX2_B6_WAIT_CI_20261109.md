# FARO V5 — S3.A5.7 Compose real Android X12 RED_FIX2 B6 WAIT_CI
Fecha 2026-11-09 AR. Unit S3_A5_7_COMPOSE_CHAT_REAL_ANDROID; A5 global OPEN.

## Estado y evidencia exacta
- Preaudit original docs/s3-night/S3_A5_7_COMPOSE_CHAT_REAL_ANDROID_B1_B5_20261109.md. Tras dos CI fallidas por imports de métodos miembros Kotlin, revisión B2/B3/B5 y B7 RED_FIX2 en docs/s3-night/S3_A5_7_COMPOSE_CHAT_B7_REDFIX2_IMPORTS_AUDIT_20261109.md, blob c03c3905c3db1f5baf3f5131e5eb66401f1ec6db.
- Primera CI #42592348610 SHA d0c61074 FAILURE unresolved `assertExists`; primera corrección SHA 4186e0b2 removió ese import. Segunda CI https://github.com/etacsantafe-source/FARO-V5/actions/runs/42593488175 FAILURE unresolved `onAllNodes` en AndroidTest línea11; guard #42593488965 SUCCESS. No Android emulado positivo en esos SHAs.
- Nueva corrección SHA 9ddd585162bc1fb6e7f3522f3149d797968f9d86 SOLO elimina línea de import de `onAllNodes`, ninguna otra edición de código ni workflow. Permit READY previo en padre 5b6f66a210459c17b491ca5d108787570d064bb4 y trailer mismo FARO-UNIT.
- Nueva CI dirigida https://github.com/etacsantafe-source/FARO-V5/actions/runs/42594626211 **RUNNING al checkpoint**, esperados `OK (2 tests)` y `INSTRUMENTATION_CODE: -1` Android emulator API35. Guard https://github.com/etacsantafe-source/FARO-V5/actions/runs/42594626180 inicialmente RUNNING. Nada de esto constituye PASS hasta evidencia exacta.
- No se repitieron suites A5 anteriores 33/33 ni X07/X11 4/4 ni native X12 2/2. V2 productiva/S4/Auto/Noche OFF; A3 Guardados no autorizado, catálogo 172 sin cambios; G15 no probado, única campaña final.

## Siguiente
Si CI dirigida y guard SUCCESS en SHA 9ddd585, verificar stdout exacto Android, B7 PASS_SCOPED, registrar checkpoint. Si RED nuevo de runtime/otro tipo, STOP, auditoría adversarial con causa, no corregir a ciegas. La preparación X01/X02 queda read-only hasta B7 Compose. 
