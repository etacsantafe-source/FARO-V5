# FARO V5 — W04-C1B2 · integración lossless Evidence2 y portabilidad comercial — B7 GLOBAL PASS acotado DEV

Fecha local 2026-11-20. Alcance: **C1B2 solamente**, no cierre de W04-C, W04-D/E/F ni aceptación Android física. Fuente: `docs/v5/FARO_S4_W04_C1B2_LOSSLESS_INTEGRATION_PREAUDIT_20261120.md` y B7 específicos de sus subunidades. No repetir certificaciones anteriores.

## B7

**PASS_SCOPED_STAGED_DEV_C1B2.** Revisadas fronteras y evidencias por SHA:
- **E · backup tipado STAGED:** código `d17dbe344a8714ca30a6f0f4cfbd46e184bb3b92`, CI `44821224309` 9/9, guard `44821224076`, B7 `FARO_S4_W04_C1B2E_STAGED_TYPED_BACKUP_B7_PASS_20261120.md`.
- **F · FBS1v4 codec semántico:** código `65f075284a7006df24ad1a0e8f612ca8f4212c56`, CI `44824047295` 8/8, guard `44824047221`, B7 `FARO_S4_W04_C1B2F_SEMANTIC_CODEC_B7_PASS_20261120.md`.
- **G · cinco stores, STAGED:** código `ada348d07baf5dd26bd136a59f9e1afc8b27e1ac`, CI `44826771657` nuevas 5/5 y legacy 5/5, regresión `44826771655` SUCCESS, guard `44826771844`, B7 `FARO_S4_W04_C1B2G_STAGED_FIVE_STORE_B7_PASS_20261120.md`.
- **H1 · proyección portabilidad typed Evidence2 in-memory:** código `db4fdd3485deff9ab36aee9b5335b6328f90c4ba`, CI `44846516892` 8/8, guard `44846516826`, B7 `FARO_S4_W04_C1B2H1_STAGED_TYPED_PORTABILITY_B7_PASS_20261120.md`.
- **H2A · codec JSON local V2:** código `d4199af06dda06b947e4bbed254859e07c23386e`, CI `44848421308` 8/8 y regresión mantenimiento, guard `44848421273`, B7 `FARO_S4_W04_C1B2H2A_LOCAL_TYPED_JSON_B7_PASS_20261120.md`.
- **H2B · perfil V2, relectura/approval/fingerprint y fuente para documento elegido:** código `b6dff51670b74c1ddea51d48fb49ef17e567ed7c`, CI `44849294556` 10/10 con XML verificado; guard `44849294438` SUCCESS; regresión independiente Guardados `44849294456` SUCCESS; B7 `FARO_S4_W04_C1B2H2B_TYPED_EXPORT_B7_PASS_20261120.md`.

**Invariantes contrastados:** Evidence != Reality; serialización y backup antiguos V1 sin truncar terms, v2 tipada explícita, unknown físico distinto de UNKNOWN tipado, FBS1v4 lossless y cinco stores STAGED; watermark/fingerprint determinísticos, validación previa al copy, recibo durable CAS y RESULT UNKNOWN sin replay tras interrupción; no ejecución de sourceRef; no FX ni oferta vigente inferida. El nuevo perfil v2 exige aprobación del usuario y destino elegido, y no puede ejecutarse con ruta v1 silenciosa. Suites específicas prueban deltas; pruebas ya certificadas no se repitieron manualmente.

**Límites y trabajo posterior:** B7 se circunscribe a Kotlin/JVM/SQLite STAGED DEV y simulaciones locales, no prueba que exista exportación usable desde pantalla Android ni documento real. Faltan W04-C2/C3 (historial/comparación/refresco), W04-D carrito/presupuesto/promociones, W04-E Android y W04-F integración; todos necesitan sus permisos y B1–B7 propios. G15/SAF físico NOT_RUN. App productiva V2 OFF. No autorización para egress externo automático, datos del usuario, Finance/stock/pagos ni despliegue.

**Decisión:** cerrar C1B2 con B7 GLOBAL PASS **acotado**, consumir permiso H2B y avanzar a preauditoría independiente C2/C3. No afirmar cierre global de C ni de W4.
