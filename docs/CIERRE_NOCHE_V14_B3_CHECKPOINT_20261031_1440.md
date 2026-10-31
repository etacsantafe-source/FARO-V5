# FARO V5 V1.4 — Noche B turno 3 (14:40 Argentina)

- **Horario programado:** 2026-10-31T17:40:00Z (14:40 Argentina). Ejecución empezó 17:35:13.124428Z, **READ_ONLY antes de 14:40**.
- **Writer:** adquirido mediante CAS a 17:40:10.500Z, epoch 38, owner NOCHE_B_V14_TURN3_20261031_1440; liberación por commit CAS que contiene este checkpoint, **verificar HEAD y writer NONE tras publicar**.
- **HEAD código:** `2586ef7705d7f3c10ce4e2fa91c043a6cb0aa82c`; **S2.A6 OPEN 3/8**, V2 OFF, S3/S4 cerrados, T4 físico NOT_RUN.

## Desarrollo funcional F3
Commit [`2586ef7705d7f3c10ce4e2fa91c043a6cb0aa82c`](https://github.com/etacsantafe-source/FARO-V5/commit/2586ef7705d7f3c10ce4e2fa91c043a6cb0aa82c):
1. Si la preparación de un intento externo de exportación termina pero falla la construcción del intent del selector SAF, se reconcilia la operación mediante cancelación antes de volver a la UI; no se deja un intento `AWAITING_DESTINATION` innecesario. El código sigue fail-closed y V2 OFF.
2. Nueva prueba Android `forgedRestoreIdentityCannotOpenSafWithoutDurableJournal`: extras de operación/target falsos, incluso con consentimiento y contraseña, no autorizan el selector ni crean autoridad de journal.
3. Oráculo de instrumentación ampliado de 10 a **11 casos exactos**.

## GitHub Actions
- [#41225697218](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41225697218), SHA `2586ef7705d7f3c10ce4e2fa91c043a6cb0aa82c`: **SUCCESS**. Job 124609261221, step de instrumentación SUCCESS y **Exact F3 XML oracle SUCCESS 11/11**. Creada 2026-10-31T17:40:52Z, job empezó 2026-10-31T17:40:55Z, terminó 2026-10-31T17:49:40Z. **Wall 528s; cola 3s; ejecución 525s**.
- Reutilizada sin repetir [#41223479830](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41223479830), SHA `e9a1097c...`, **10/10 PASS** previo.
- El oráculo 11/11 demuestra la regresión de extras falsos y pruebas anteriores, pero **NO prueba un export exitoso desde V2 habilitada ni un SAF picker positivo de usuario ni T4 físico**. El nuevo camino de cancelación anticipada compiló, pero no fue ejecutado directamente bajo V2 OFF. **F3 sigue OPEN, sin PASS ficticio**.
- Ninguna Action nueva no terminal al cierre.

## Tiempo observado
- Inicio real 2026-10-31T17:35:13.124428Z; checkpoint 2026-10-31T17:52:37.612Z; **wall 1044s** (incluye pre-ventana). Ventana programada transcurrida hasta checkpoint **758s**. Writer sostenido **747s**.
- Antes de la ventana: **287s** entre inicio efectivo y hora programada; hubo investigación READ_ONLY y espera deliberada, sin medición exclusiva de ambas.
- CI wall **528s**, en paralelo con parte del turno; **NO sumar como trabajo adicional**.
- Investigación, escritura, revisión, espera e inactividad como categorías exclusivas: **NO_REGISTRADO**. No imputar como productivos los minutos de espera ni el resto de la ventana tras este cierre anticipado.
- Cierre anticipado para evitar mantener un writer sin nuevas modificaciones seguras o repetir CI sin causa.

## Pendiente
F3: ruta positiva del selector SAF con usuario, respaldo/exportación y restauración autenticada de cinco owners bajo journal, compatibilidad y recuperación; conservar opción A (UI mínima S2, Ajustes S5). F1: E2E real de proveedor remoto consentido/Keystore, Review/Apply/Delivery; F2: activación/recovery; F4: regresiones integrales; T4 físico usuario. **S2 3/8**, sin S3/S4 ni cutover. Próximo turno primero verificar HEAD, writer NONE y CI; no repetir 11/11 para SHA idéntico.
