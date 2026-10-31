# FARO V5 — CIERRE Noche V1.4 · Noche A turno 16:10 (31/10/2026)

**S2.A6 OPEN 3/8**. V2 OFF; S3/S4 CLOSED; T4 físico NOT_RUN.

## Trabajo funcional y código
- [a90dbda](https://github.com/etacsantafe-source/FARO-V5/commit/a90dbda77f4764aa40304ee467e1f2630687cc4a): F3 revalida cancelación entre verificación del proveedor SAF y emisión del recibo externo SUCCESS; además revalida cancelación inmediatamente después de crear journal STAGED, antes de restaurar. Si se cancela, queda barrera durable de recuperación, nunca cutover V1.
- [1904a6b](https://github.com/etacsantafe-source/FARO-V5/commit/1904a6b4fab28e8cfdf9e7ecd393b545b1217167): regresión de integración Android para proveedor real MediaStore content:// -> runtime consentido -> archivo autenticado -> journal cinco owners VALIDATED con puntero V1 sin cambio. Fixture aislado inyecta startup PASS como precondición de test: NO certifica arranque real ni picker humano.
- [7716142](https://github.com/etacsantafe-source/FARO-V5/commit/771614297d1628d690fc849013a91aefd162cedd) y [f8edbcbf](https://github.com/etacsantafe-source/FARO-V5/commit/f8edbcbf7c7836c5c8fb5c93694bcb191c4f860e): correcciones del nuevo test (wipe de contraseña, nulabilidad de URI/payload). Oráculo 14/14, todavía pendiente de ejecución terminal.
- No se activó V2, no se modificaron main, V1, V4.4, FARO Auto, repositorio histórico, secretos, visibilidad ni pagos.

## CI verificada y en vuelo
- [Actions #41233024908](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41233024908), SHA `a90dbda77f4764aa40304ee467e1f2630687cc4a`: **SUCCESS**, XML exacto `F3_V14_PINNED_PROVIDER_JOURNAL_PASS 13/13`, job 124630956306. Run 19:11:19–19:19:47 UTC = **508 s wall**; log del runner 19:11:22.699–19:19:45.554 = **~503 s de intervalo de ejecución observado**. Diferencia desde creación hasta primer log ~4 s es PROXY de cola, NO medición oficial de cola. CI superpuesta con trabajo, no sumar dos veces.
- [Actions #41233238496](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41233238496), SHA `f8edbcbf7c7836c5c8fb5c93694bcb191c4f860e`: **IN_PROGRESS**, conclusión NO_TERMINAL, XML **WAIT_CI**. Esperar resultado real; NO declarar 14/14 PASS antes de verificar XML.
- Actions #41233184135 y #41233213373: **CANCELLED** por reemplazo automático de runs pendientes tras commits posteriores, **0 jobs**, XML NOT_RUN. No cancelación manual.

## Ventana y medición
- Programada 16:10 Argentina. Ejecución observada desde 16:07:52.843 (solo lectura antes del inicio); writer epoch 41 adquirido por CAS a las 16:10:04.064, [commit de adquisición](https://github.com/etacsantafe-source/FARO-V5/commit/23a7ca755a04f8a42dcf27da604edc3e2f285d21).
- Último commit de código 16:14:04.055. Checkpoint preparado 2026-10-31T19:25:29.452Z UTC. Pared observada desde inicio real: **1057 s**; desde ventana programada: **929 s**; writer poseído hasta preparación: **925 s**; antes de ventana sólo lectura: **127 s**; espera por writer: **0 s**.
- Intervalo adquisición→último commit: **240 s** de actividad mixta, NO tiempo exclusivo de programación. Desglose investigación / escritura / pruebas / espera CI / coordinación / tiempo muerto: **NO_REGISTRADO**. No inventar porcentajes ni sumar CI en paralelo.
- Este commit registra liberación **CAS expected HEAD** y writer **NONE**; comprobar por lectura posterior antes de declarar liberación efectiva.

## Criterios congelados y próximo trabajo
1 FIVE_OWNERS CERTIFIED; 2 FBS2_RESTORE CERTIFIED; 3 JOURNAL_WAL CERTIFIED; 4 F1 Android E2E OPEN; 5 F2 Android recovery OPEN; 6 F3 SAF backup/restore OPEN; 7 F4 regresiones OPEN; 8 T4 físico NOT_RUN. **3/8**.
Primero verificar #41233238496 y XML exacto sobre SHA final. Si falla, diagnosticar el test de runtime/fixture, no reintentar idéntico. Si pasa, seguir F3 con selector SAF real y consentimiento positivo Android en host aislado; exportación de producción sigue bloqueada por `V2_ACTIVE_ENABLED=false` hasta certificación. Mantener V1 protegido y S3/S4 cerrados.
