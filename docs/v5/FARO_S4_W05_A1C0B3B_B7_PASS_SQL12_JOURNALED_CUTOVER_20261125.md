# FARO V5 · W05-A1C0B3B · Cutover SQL12 Evidence2 DEV

## B7 — PASS acotado

- Código final `c0fcabc006d021f498c30480e31ee9041f919f8e`, guardia [45599209368](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45599209368) SUCCESS.
- [CI 45599209365](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45599209365) SUCCESS y [ZIP original 15014698398](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45599209365/artifacts/15014698398) descargado y verificado. Ocho pruebas de cutover y ocho del replay STAGED afectado. Total 16/16, cero fallos, errores u omitidas.
- ZIP SHA-256 `dff4e078d1e671df62e0312b264280210cb0c7ce3cfaf85f4f31745eea5e15b7`.
- XML `FaroV5Household12Evidence2DevJournaledCutoverTest` SHA-256 `b8420edc17e2c10c806e4f8308b01c09ee744d95e6246eafcff459dd6d81308d`.
- XML `FaroV5Household12V9StagedReplayTest` SHA-256 `8d0d2f4860f566ce2ebdf209dd572d4bbc9a000a46bbb269e03daec5a65fe396`.
- La CI inicial 45597914806 expuso el fallo de verificación del rollback tras el reopen readonly. La corrección en mismo scope admite sidecars vacíos del destino únicamente con prueba plena de selección anterior y conserva las huellas de los cinco archivos, rechazo de WAL con frames y chequeo de alias. RED_FIX `92e0558a8e6dc47a54aae9c1540d2492921a6b7d`, guardia 45598928519 SUCCESS.
- El corte confirmó v9 autenticado y pin, ticket y journal, confirmación foreground, reanudación antes y después de publicar puntero, rechazo ABA/alteraciones, rollback con origen saludable y ADMISSION read-only SQL12 tras el fence. W05-A1C0B3 queda B7 PASS en DEV por A1C0B3A 19/19 y A1C0B3B 16/16. Moto G15 físico NOT_RUN. W05-A1 DEV OPEN. V2 productiva, stock real, Apply productivo, pagos y Auto/Noche OFF.
