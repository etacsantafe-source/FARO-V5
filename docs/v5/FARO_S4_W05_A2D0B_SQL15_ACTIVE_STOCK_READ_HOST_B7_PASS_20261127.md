# FARO V5 · W05-A2D0B · B7 PASS DEV JVM

## B7 · Auditoría final

- Código `05d20d79fad0ed9ccd95206c6d08526d9f99de07`, permiso exact3 `fe89b62897560e30ff6a9d212e94c6a970967371`, guardias de preauditoría 46050775042, permiso 46094492189 y código 46094672665 SUCCESS.
- CI dirigida 46094672844, intento 2, job 140823546284, SUCCESS. El intento 1 recibió HTTP 429 al resolver Kotlin desde Maven Central antes de compilar. El reintento usó el mismo SHA. Artefacto original 15242354696 descargado, SHA-256 `47d174a6d79ede0507b46ade6139cf243bd5b51d0d9bc6d58631e804955ae2f1` verificado, XML 6/6 casos únicos y cero fallos, errores u omisiones.
- Prueba positiva usa SQLite SQL15 ACTIVE con frames solamente en WAL, cinco stores y registros de selección COMPLETED en fixture temporal. Verifica origen por principal, aislamiento de historial, reapertura repetible y DB principal sin cambios.
- Pruebas de cierre seguro cubren revocación antes/después y nuevo epoch, pointer completo con ABA, ticket ausente/corrupto, journal pendiente, SQL14, lease ocupado y WAL enlazado. El host reutiliza el inspector y la comprobación canónica de journal/ticket completos. La prueba de restore integral con la UI queda para A2D2.
- El host expone exclusivamente lectura tipada. La autorización de la app y el montaje aislado de SmartCompra se validan en A2D1B. V2 productiva, Stock real, pagos y Auto/Noche OFF. G15 NOT_RUN.

A2D abierto, 2/5 B7. A2D1A dispone de B1–B5 documentados y exige permiso propio luego de la guardia de este checkpoint.
