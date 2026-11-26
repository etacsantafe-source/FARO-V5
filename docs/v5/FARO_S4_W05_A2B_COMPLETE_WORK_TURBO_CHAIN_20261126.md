# FARO V5 — W05-A2B completo · Work Turbo

Base verificada c4d1901549005e7fdb990eb5b83aba62099eb01c. A2B0A SQL14 STAGED B7 PASS 7/7, guardia 45808479503 SUCCESS, sin repetir A1/A2A. Este lote adelanta la auditoría B1–B5 de diseño de A2B, no implementa ninguno de sus B6/B7.

## Cadena de unidades

1. **A2B0B1** FBS2v11 tipado y cifrado — `docs/v5/FARO_S4_W05_A2B0B1_B1_B5_WORK_PREAUDIT_20261126.md` — primera a certificar y emitir READY exact4
2. **A2B0B2** SQL14 rows/capture/restore STAGED — `docs/v5/FARO_S4_W05_A2B0B2_B1_B5_WORK_PREAUDIT_20261126.md` — condicional; revalidar tras el B7 predecesor, emitir guardia+READY propios
3. **A2B0B3** FBS2v11 autenticado, cinco stores SQL14 STAGED — `docs/v5/FARO_S4_W05_A2B0B3_B1_B5_WORK_PREAUDIT_20261126.md` — condicional; revalidar tras el B7 predecesor, emitir guardia+READY propios
4. **A2B0C0** Lectura SQL14 ACTIVE DEV cerrada — `docs/v5/FARO_S4_W05_A2B0C0_B1_B5_WORK_PREAUDIT_20261126.md` — condicional; revalidar tras el B7 predecesor, emitir guardia+READY propios
5. **A2B0C1** Cutover/recovery SQL13→14 DEV — `docs/v5/FARO_S4_W05_A2B0C1_B1_B5_WORK_PREAUDIT_20261126.md` — condicional; revalidar tras el B7 predecesor, emitir guardia+READY propios
6. **A2B1A** Admisión tipada Review reserve/release — `docs/v5/FARO_S4_W05_A2B1A_B1_B5_WORK_PREAUDIT_20261126.md` — condicional; revalidar tras el B7 predecesor, emitir guardia+READY propios
7. **A2B1B0** Writer Reality SQL14 con invariantes — `docs/v5/FARO_S4_W05_A2B1B0_B1_B5_WORK_PREAUDIT_20261126.md` — condicional; revalidar tras el B7 predecesor, emitir guardia+READY propios
8. **A2B1B1** Host Review→Confirm→Apply y receipts — `docs/v5/FARO_S4_W05_A2B1B1_B1_B5_WORK_PREAUDIT_20261126.md` — condicional; revalidar tras el B7 predecesor, emitir guardia+READY propios
9. **A2B1C** Aceptación global A2B DEV — `docs/v5/FARO_S4_W05_A2B1C_B1_B5_WORK_PREAUDIT_20261126.md` — condicional; revalidar tras el B7 predecesor, emitir guardia+READY propios

## Misión para Work

Primero A2B0B1 B6/B7 directamente si el permiso READY y guardia sobre SHA actual existen. No repetir B1–B5 de la unidad cuyo blob esté VALIDADO. Para cada sucesor: leer preauditoría publicada, comparar cambio B7 de predecesor y HEAD, auditar solo DELTA, congelar rutas, emitir documento/guard/permiso individual READY, preflight, B6, CI real XML, B7, consumir permiso y liberar writer. Continuar sin pausas injustificadas mientras existan créditos y permisos legítimos. Si ruta adicional imprescindible, STOP y cambiar solo el scope con permiso propio; nunca tomar writer ajeno ni adelantarse a un B7 fallido.

La ruta A2B0B1→B2→B3→C0→C1→B1A→B1B0→B1B1→B1C se resuelve en DEV aislado. FBS2v11 cinco stores/SQL14, journal recovery y Review→Apply con consentimiento real y recibos. No declarar available hasta A2C; A2C consumo, A2D UX, A2E global A2 quedan FUERA de A2B. V2 productiva, stock real, pagos, Auto/Noche OFF. G15 NOT_RUN.

## Barrera final

Checkpoint autenticado + guardia SUCCESS + XML JUnit original + writer RELEASED. Sin eso reportar BLOCKED y conservar evidence. Un solo permiso de código vigente; no emitir READY múltiple.
