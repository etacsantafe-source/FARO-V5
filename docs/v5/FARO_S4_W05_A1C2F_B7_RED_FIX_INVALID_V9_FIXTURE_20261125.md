# FARO V5 · W05-A1C2F · B7 RED_FIX: respaldo v9 artificial inválido

## B7 — RED_FIX misma unidad

- Código auditado: `e2e1a5ddedeacc06ed5ce3ef5664aa354ccfa1b2`; tres rutas exactas A1C2F. Guardia `45628413589` **SUCCESS**.
- [CI 45628413752](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45628413752) **FAILURE** con `31` pruebas JUnit ejecutadas, `30` PASS y `1` FAIL. El XML original de cuatro suites fue preservado, [artefacto 15028987919](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45628413752/artifacts/15028987919), no expirado al auditar.
- Fallo exacto: `FaroV5Household13Evidence2DevJournaledCutoverTest.rejectsArchiveSubstitutionAndCorruptedTicket`, línea 339, `IllegalArgumentException` al fabricar *antes de llamar al cutover* un FBS2v9 contradictorio: tres recibos por 7+3+7=17, compra por 10, pero v9 no admite la compensación de 7 que solo está en v10. El codec rechaza correctamente el fixture.
- Reparación mínima en el mismo archivo de prueba: derivar un v9 histórico consistente con los **dos recibos iniciales 7+3** y ledger revision 2, manteniendo compras 10; firmar v9 y confirmar que la autorización para SQL13 con ese archivo devuelve `BLOCKED` por versión. No alterar el codec ni permitir v9 como SQL13.
- Ningún fallo confirmado en las otras 30 pruebas, incluidas activación, crash recovery, rollback, rechazo de puntero ABA, SQL12 previa y lectura SQL13. No declarar B7 PASS sin reejecutar las 31 pruebas por SHA, XML cero failures/errors/skipped y guardia verde.
- Este documento constituye **RED_FIX** para el permiso de la misma unidad. `last_code_review.result=RED_FIX` y prueba blob exacta exigidos por `faro-protocol-gate.py` antes del commit funcional. No abrir unidad siguiente ni ampliar las tres rutas.
- V2 productiva, stock real, Apply, pagos y Auto/Noche `OFF`; físico G15 `NOT_RUN`.
