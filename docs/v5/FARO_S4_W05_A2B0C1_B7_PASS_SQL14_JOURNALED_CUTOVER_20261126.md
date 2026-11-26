# FARO V5 · W05-A2B0C1 · Cutover SQL14 Evidence2 DEV

## B7 — PASS acotado a DEV

- Código `852b40eef1ce6b732ca1366b107ff5c340c6aed3`, cuatro rutas exactas. [Guardia 45821441233](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45821441233) y [CI 45821441471](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45821441471) SUCCESS.
- [Artefacto original 15118012983](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45821441471/artifacts/15118012983), ZIP SHA-256 `14066d09f9d391afe89888779db21fad2ca8472c89505fc69ff48447ea079ec2`. XML original 16/16, 6 SQL14 y 10 SQL13, cero fallos, errores u omitidas.
- FaroV5Household14Evidence2DevJournaledCutoverTest.xml 6/6; XML SHA-256 `53e8c97ad5edadd25abda0a4ecdf702f0ec16acb07df4502f204d8883444bfec`.
- FaroV5Household13Evidence2DevJournaledCutoverTest.xml 10/10; XML SHA-256 `d7fa8f113a59606bcd9170d15f917cd539460a0c9c3dffbf83dfde85d9ba83af`.
- Exige SQL13 con su selección real certificada y archivo v11 autenticado del stage. Ticket v14 separado, dos confirmaciones, autoridad con lease exclusiva, journal y fence. Preflight WAL de cinco stores tras la selección.
- Recovery antes y después del CAS conserva una única publicación. Fallo de salud recupera SQL13 con secuencia nueva, testado con el host real SQL13 y ticket ROLLBACK_VERIFIED. Se rechazaron ABA, ciphertext sustituto, formato anterior y origen sin ticket.
- C0 usa el ticket real COMPLETED. Alcance DEV, G15 NOT_RUN. V2 productiva, stock real, pagos y Auto/Noche OFF.
