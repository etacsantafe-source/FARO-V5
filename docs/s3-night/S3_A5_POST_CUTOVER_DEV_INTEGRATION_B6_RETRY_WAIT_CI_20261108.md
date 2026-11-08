# FARO V5 — S3.A5 post-cutover DEV integration B6 RETRY WAIT_CI
Fecha 2026-11-08 AR. Unidad `S3_A5_POST_CUTOVER_DEV_INTEGRATION`. Quinta unidad A5 anterior B7 PASS scoped sin revalidación redundante.
- B1–B5 `S3_A5_POST_CUTOVER_DEV_INTEGRATION_B1_B5_20261108.md`.
- Primer código `03f5af2ae9e1382c7a2f0ff0b9d51049868e374d` CI `#42536498612` FAILURE en compilación de tests `ILLEGAL_SUSPEND_FUNCTION_CALL`; protocolo PASS. B7 diagnóstico `S3_A5_POST_CUTOVER_DEV_INTEGRATION_B7_RED_FIX_20261108.md`.
- Test-only RED_FIX `3eda9c270478b6e2bae435d164664873d36665d5`: 3 métodos de prueba cambian a `= runBlocking { ... }`, no código producción; trailer `FARO-UNIT:S3_A5_POST_CUTOVER_DEV_INTEGRATION`.
- Nueva CI `#42536901243` IN_PROGRESS al checkpoint, 5 casos con SQLite reales; guard `#42536901256`; V2 Core viejo auto-run `#42536901284`, no relanzar. No XML 5/5 verificado aún, B6=WAIT_CI B7=PENDING.
- Para próximo turno: revisar solo run final SHA `3eda9c27` y XML `tests=5 failures=0 errors=0 skipped=0`, guard SUCCESS, diff exacto. Si fail, nueva causa ⇒ B5/reducir prueba, no repetir corrección.
- S3.A5 OPEN; S4/A3 writer nuevos no autorizados; G15 solo al final X01–X12; V2 shipping OFF; Auto/Noche OFF, catálogo172.
