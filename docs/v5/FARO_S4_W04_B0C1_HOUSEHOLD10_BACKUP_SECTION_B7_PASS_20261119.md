# FARO V5 · W04-B0C1 · B7 de la sección portable Household10

Unidad `S4_W04_B0C1_HOUSEHOLD10_BACKUP_SECTION_DEV`. SHA B6 `52961b19f6db6c1a37dfb3d0576776ff28c9a22c`, padre READY `8fe6f4af0877b9028f7789227addd143de11a78d`.

## B6 — Implementación y evidencia

- Guardia READY `44666617251` SUCCESS, preflight de cuatro rutas PASS y guardia B6 `44666984320` SUCCESS.
- CI dirigida `44666984553`, job `136058554687`, SUCCESS. Artefacto descargado `14598536187`, ZIP SHA-256 `fb70bd72a79ea470d54bc0d10f3d9e7cf94c650352853df9d9c8f0d24f5db0ae`. XML verificado directamente. **8/8** y cero failures, errors o skipped.
- La sección H10S preserva revisión agregada, filas tipadas y fechas opcionales. Pruebas de Unicode, resultado determinista, sección vacía, duplicados, orden, revisión, formatos inválidos, truncamiento, bytes sobrantes, cantidades, enums y límites. Sin cambios en datos o archivos SQLite.
- Regresiones automáticas del SHA exacto: codec de planificación 44666984319 y V2 core M1/M2 44666984348, ambas SUCCESS.
- Android y G15 NOT_RUN. No se ha conectado H10S a un archivo FBS2v6 ni a captura, restauración o cutover.

## B7 — PASS_SCOPED_W04_B0C1_PORTABLE_SECTION

Consumir el permiso B0C1. La sección está disponible para B0C2, que debe integrar base v5 y Household en el archivo autenticado v6 con guardia y permiso propios. Los adaptadores SQLite y el ciclo de restore son B0C3.
