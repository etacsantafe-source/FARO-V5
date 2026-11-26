# FARO V5 · W05-A2B0A · Reservas SQL14 STAGED

## B7 — PASS acotado a DEV

- Código `5963052a962f49bbee9047305f2e15a4500ed592`. Cuatro rutas exactas y preflight contra el padre READY. [Guardia 45807851940](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45807851940) y [CI dirigida 45807851895](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45807851895) SUCCESS sobre el código.
- [Artefacto 15109904168](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45807851895/artifacts/15109904168). XML JUnit original descargado y verificado. 7/7, cero fallos, errores u omitidas. SHA-256 ZIP `7170c3655fea43edc82e6f179d5d836377ee49dc0ec8a7d04f7dc01615bc376f`. SHA-256 XML `b52e73b159f386f40e174abaef412a2d290836a2c193f5de1fbb2574423d02e5`.
- La migración instala una tabla STRICT append-only inicialmente vacía en una copia sellada. El fixture conserva compra 10, recepciones 7+3, compensación 7, neto físico 3 y datos heredados de Finanzas y necesidades. El archivo fuente conserva sus bytes.
- La prueba de revocación entre apertura y COMMIT revalida que el archivo vuelva a SQL13 sin DDL parcial. Una lectura concurrente durante el checkpoint conserva en forma atómica el SQL14 ya confirmado y su historial. Rechaza versiones incorrectas, archivo corrupto, aliases, enlaces y sidecars. Reapertura valida el DDL exacto.
- Se rechazaron IDs, unidades, cantidades y revisiones inválidas, duplicados, sustituciones, UPDATE/DELETE y RELEASE sin reserva compatible. El esquema admite liberaciones parciales acumuladas hasta el total original, con IDs e intents distintos. La capacidad sobre stock físico y el CAS de la autoridad Apply corresponden a unidades posteriores.
- SQL14 permanece STAGED. A2B0B FBS2v11, cutover DEV, Review/Apply y disponibilidad pendientes. V2 productiva, stock productivo, pagos y Auto/Noche OFF. Android y G15 físico NOT_RUN en A2B0A.
