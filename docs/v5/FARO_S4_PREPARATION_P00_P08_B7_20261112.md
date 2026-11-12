# FARO V5 — B7 de publicación documental S4 (2026-11-12)
Unidad: `S4_PREP_P00_P08_HANDOFF_DOC`.
**Alcance: SOLO DOCUMENTACIÓN; NO es P00 certificado ni S4 autorizada.**

## B1–B5 — referencias previas, no repetir
Ver `docs/v5/FARO_S4_PREPARATION_P00_P08_HANDOFF_20261112.md`, secciones B1–B5: HEAD y writer verificables, comparación D-491/D-493, benchmark heredado, unificación de índice + pointer y auditoría adversarial. El permiso funcional anterior sigue BLOCKED.

## B6 — escritura y evidencia
- HEAD de origen antes de publicar: `fbc2c1a15029b7ebe156332958043ce5f2747ef8`.
- Writer CAS HELD `FARO.MANUAL.S4.P00.DOC.20261112`, epoch 843, commit writer `9564e2e1edf570c5105fe86fdf2e3b97fad94d0d`.
- Primer commit documento: `9dd851cd44e58694f23784024f125097b710f932`.
- Archivo: `docs/v5/FARO_S4_PREPARATION_P00_P08_HANDOFF_20261112.md`.
- SHA blob remoto verificado mediante GET: `0f1a6b3c45c1bb0060b3d1ffac6c3011defb63f4`; contenido P00–P08 y B1–B5 comprobado.
- Segundo commit de continuación agrega este B7 y actualiza el pointer (SHA final se obtiene de ref remoto tras push).
- Sin cambios funcionales, sin CI Android nueva. S2 / S3 / catálogo / productiva V2 / main / V4.4 intactos.

## B7 — auditoría independiente de alcance documental
Comprobar posterior a publicación:
1. GET ref HEAD exacto y HEAD padre del primer documento, verificando avance fast-forward.
2. GET pointer: S4 `s4_open=false`, `s4_preparation.status=PLANNING_ONLY_P00_NOT_STARTED`, `handoff_document` existente, `next` de S3 preservado.
3. Verificar los dos documentos y que el diff solo afectó `docs/v5/`.
4. GET writer final RELEASED epoch 843 con last_checkpoint igual a HEAD.
5. Si cualquier comprobación falla, reportar HANDOFF_INCOMPLETE y STOP sin declarar PASS.
**Estado de este archivo: CHECKLIST PARA VERIFICACIÓN REMOTA FINAL**. Un commit no demuestra por sí solo PASS.
