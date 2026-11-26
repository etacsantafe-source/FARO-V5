# FARO V5 · W05-A2B0B3 · Replay autenticado de cinco stores SQL14

## B7 — PASS acotado a DEV

- Código inicial `b638f077be08221e9d67f4a5580ae799f298d3bb` con nueve rutas. La corrección `1d850a459f2f48163e68e640ca27aff7a0b61a91` ajustó solo el fixture ABA y la validación de alcance del workflow. [Guardia 45819543679](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45819543679) y [CI dirigida 45819543956](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45819543956) SUCCESS.
- [Artefacto original 15116647887](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45819543956/artifacts/15116647887). ZIP SHA-256 `db174005e299a2fd2d8571a83172bbaa3634426e9e8ad2dce828eee4b49deb60`. XML original 13/13, cero fallos, errores u omitidas.
- Household14V11 5/5; XML SHA-256 `c694b190ec11eb790c7ed8edf983a623903cb426c055af2f1f96e946d42cc3a8`.
- Household13V10 8/8; XML SHA-256 `7e45b7c62902171b4d1bf0227c0d61118ecaa6689bb39a4bd9ddd7c358087377`.
- Reproduce cinco stores desde ciphertext v11, exacto y fijado antes del primer cambio de filas. Verifica base restaurada sobre SQL14, Evidence2 y ledger, recaptura y rechaza sustitución de ciphertext aun con el mismo plaintext, secreto incorrecto, WAL inesperado, revocación y ABA.
- El import v10 explícito conserva su pin y deja su ledger vacío. Reintento tras migración conserva el pin v11 original y no duplica eventos. El puntero conserva su origen; SQL14 permanece STAGED.
- Próximo C1 antes de C0. V2 productiva, stock real, pagos y Auto/Noche OFF. G15 NOT_RUN.
