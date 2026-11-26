# FARO V5 · A1C4 · Mantenimiento desde SQL12 cerrado

## B7 — PASS acotado

- Código `ef16c14e60c5161088fbc77469388c84a1bd6964`, cuatro rutas autorizadas. [Guardia 45741277068](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45741277068) SUCCESS sobre el SHA exacto.
- [CI JVM 45741277089](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45741277089) SUCCESS. El [ZIP original 15081207918](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45741277089/artifacts/15081207918) verifica 2/2, 0 fallos, errores u omitidas. SHA-256 del ZIP `d5500c62b71306b148e180fe5057fde5dd6e99e828be7c437646e7b9ea436e58`, XML `ab887cbdbb0721251a57691e0d21b0610eedf9878bd201772a8a691c58e80ba3`.
- Mantenimiento bajo lease DEV compartido con Apply. Captura de los cinco stores de SQL12 cerrado, restore autenticado v9 al stage puente, v10 al stage final SQL13, ticket VALIDATED, rechazo por revocación antes del switch, puntero único y journal COMPLETED. Se recuperó y reabrió SQL13 con las dos recepciones y el Review del fixture. El reintento de Undo, Apply de app, Android API35 y accesibilidad pertenecen a A1C4.
- Rechazados lease existente e intento PREPARED, sin seleccionar un destino ni crear el journal SQL13. Los workflows históricos de verificador y C2F disparados por el archivo compartido quedaron fuera de la unidad de su permiso.
- A1C4 conserva exact3; revalidación documental y permiso independiente antes de implementar. V2 productiva, stock real, pagos y Auto/Noche OFF. G15 físico NOT_RUN.
