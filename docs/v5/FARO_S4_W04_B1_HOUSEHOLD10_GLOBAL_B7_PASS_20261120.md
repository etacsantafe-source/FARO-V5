# FARO V5 — W04-B1 Necesidades SmartCompra DEV

Fecha local 2026-11-20. Rama `work/s3-night-20261102`. Código final `52db8e5d7c5c4d4c8f013af5d2d81fd3f29724d8`.

## B7 — PASS integral W04-B1 en DEV

| Unidad | Alcance | CI dirigida | XML |
| --- | --- | --- | --- |
| B1A | Lectura ACTIVE10 con lease, puntero y WAL | 44746107035 | 9/9 |
| B1B | Alta, edición, completar y eliminar con CAS | 44748638104 | 6/6 |
| B1C1 | Review inmutable por nombre y con preimagen | 44750589732 (intento 2) | 6/6 |
| B1C2 | Confirmación, grant propio, Apply y recibos durables | 44752592041 | 6/6 |
| B1D0 | Copia sellada desde ACTIVE10 y restore FBS2v6 en stage | 44754902344 | 4/4 |
| B1D1 | Undo, historial, regresión de Apply y archivo con Undo | 44766456887 | 17/17 |

- Guardia 44766456766 SUCCESS para el código final. Guardia 44766761888 SUCCESS para el checkpoint de espera. ZIP 14646151944 descargado e inspeccionado, digest `sha256:7f0f191cd24629731fed63f920b034c89958565ae4d7f047bb2b23f57a4d964d`. XML SHA-256 Undo `ce27eaca89249184a4ead7609a6679e5373fa7576b5a6459a76743e2d3d29e08`, Apply `54bc00f48569f10763e643426fbf9f356d0561ba2db24344c0bfda6db70e6ba9` y archivo `b00923d1c46215ae0ebdd2472c94f125939f739b4084622c19a499c4e576241a`, con cero fallos, errores u omisiones.
- Las cuatro mutaciones pasan por la confirmación humana, el writer compartido y la transacción Reality10. Persisten tras reabrir. Se comprobaron rechazo por revisión obsoleta, CAS, ABA del puntero, revocación final, journal y recibos durables. La reconciliación después de una interrupción de Workspace usa el recibo ya comprometido.
- Undo exige un Review nuevo y el origen COMMITTED verificable. Restituye los campos y estado con nueva revisión, enlaza los recibos y rechaza repeticiones, estados obsoletos y otro principal. Los registros históricos sin vínculo de principal verificable mantienen historial y carecen de admisión para Undo.
- Se probó FBS2v6 cifrado desde un ACTIVE10 sellado hacia una stage SQLite. El restore preservó el estado recuperado, el Review original, el Review Undo, ambos recibos y el vínculo entre ellos. Se reabrió el historial con la autoridad propia de la etapa restaurada. El puntero original y el contenido de la fuente se preservaron.
- Las pruebas de Apply y backup/restore comprobaron preservación de Finanzas, Objetivos y Asuntos. La prueba histórica de Finanzas `RealityFinance9ActiveDevGrantPortTest:175` ya fallaba sobre el checkpoint de entrada y quedó documentada en B1A/B1B.
- La certificación cubre JVM/SQLite de DEV. Android físico no ejecutado. Permisos B1A, B1B, C1, C2, D0 y D1 consumidos. V2 productiva OFF.
- Próximo bloque: relevo de W04-C0 en `docs/v5/FARO_S4_W04_C0_PRICE_READ_HANDOFF_AFTER_B1_20261120.md`, pendiente de su B1–B5 delta y READY exacto propios.
