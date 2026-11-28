# FARO V5 · W05-A2D0A · B7 PASS DEV JVM

## B7 · Auditoría final

- Código `b48243328c3224e0e99f17dffb408be7b03fe2c4`, parent `64ac0bf4458d599626698eeb3a554d9613778985`. Tres rutas exactas autorizadas y preflight PASS. Guardia documental 46050775042, permiso 46050943010 y guardia de código 46093922974, todas SUCCESS.
- CI dirigida 46093923131, job 140820863015, SUCCESS. Artefacto 15243285314. ZIP original descargado, SHA-256 `45884060d0593dae216499f8205355651a05bc7be9c93e8203cb0179f27b197e` verificado y XML parseado independientemente. Siete casos únicos, 7/7 PASS, cero fallos, errores u omitidos.
- Oráculo de stock por producto, unidad y principal. Compra 10 y recepción 7 dejan 3 pendientes. Físico/reservado/disponible pasan por 7/2/5, 6/2/4, 7/2/5 y 7/0/7. Consumo reservado, release excesivo, Undo con origen incorrecto, intención duplicada, revisión colisionada y falta de procedencia quedan rechazados. Aritmética Long exacta con prueba de overflow y balances mayores que Int.
- La proyección recibe historia autenticada por el owner. No abre SQLite, emite grants ni monta UI. ACL, WAL ACTIVE y ticket COMPLETED corresponden a A2D0B. Android API35, APK de A2D, persistencia integral y Moto G15 siguen pendientes. V2 productiva, stock real, pagos y Auto/Noche OFF.

A2D0A cerrado en DEV JVM. A2D0B requiere READY propio tras guardia de este checkpoint. A2D global abierto, 1/5.
