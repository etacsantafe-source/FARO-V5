# FARO V5 · W05 C2C1 · Android Evidence2 ACTIVE DEV

## B7 — PASS acotado
- Implementación `3a171952d498b5831f3adc0266dd18e59c479eef`. Reejecución por ajuste de heap D8 `2d0e383878f4a1bbfe59ffe95d06c42d01a3e26e`; el diff del RED_FIX solo toca el workflow autorizado.
- [CI 45531217759](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45531217759) y [guardia 45531217764](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45531217764) SUCCESS sobre `2d0e383878f4a1bbfe59ffe95d06c42d01a3e26e`.
- [Artefacto original 14985746883](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45531217759/artifacts/14985746883), ZIP SHA-256 `a82a5278e1938d5b389fcb35496f606eeb1d8c653a100128cbff3677e7f43b2e`. Auditados XML 6/6 JVM y 3/3 Android API35, cero fallos, errores u omitidas. APK SHA-256 `7f582eb30bf89fbafae20e6aa020142510ccff9f9aece3c35b5de8d18fa1663a`.
- JVM cubre VALIDATED sin switch, recuperación de switch autorizado, rollback sin exposición, revocación, ABA, perfil incorrecto y archivo ausente. Android comprueba dos reaperturas y cierre en modo solo lectura con H10/H11R esperados, revocación y rechazo de la fixture con revisión divergente en WAL.
- C1C Evidence1 histórico (45529803361) se disparó por la Activity compartida. El preflight de C1C rechazó el permiso C2C1 antes de sus tests. W03-G (45529803518) recibió HTTP 429 resolviendo Kotlin, antes de su ejecución. Los resultados anteriores certificados se conservan.
- Permiso C2C1 consumido. A1C0 auditado hasta la admisión del grant en `docs/v5/FARO_S4_W05_A1C0_STOCK_RECEIVE_ADMISSION_20261125.md`. A1C1–A1C4 y A1 global OPEN. Moto G15 físico NOT_RUN. Stock real, V2 productiva, pagos y Auto/Noche OFF.
