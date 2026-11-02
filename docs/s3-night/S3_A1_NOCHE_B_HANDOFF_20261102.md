# FARO V5 S3 — NOCHE_B 19:30 · A1 avance durable (02/11/2026)

## Resultado verificado
- S3-N00 **PASS A/B**: NOCHE_A y NOCHE_B leyeron misma cola CLOUD, B vio checkpoint A; writer compartido CAS epochs 12/13; prueba B expected SHA correcto no-op PASS, stale expected error genérico y ref intacta. No se simuló carrera simultánea de dos propietarios. Evidencia en `faro-v5-night-queue:queue.json`.
- S3-P00 **PASS_SPEC_ONLY**: R0–R3/H01–H04 congelados como especificación; **no** como implementación. 172 OperationContracts oficiales intactos; +8 Guardados siguen candidatos y requieren bounded reopen separado. `docs/s3-night/S3_P00_CONTRACT_REVIEW_20261102.md`.
- S3-A1 **PARTIAL_CI_VERIFIED**: commit `bae8a1a58cdff50c9783275cec17268143bef466` agrega Buscar/Guardados/Actividad/Avisos al drawer, retira accesos Search/Avisos de topbar y Chat duplicado del drawer. CI `#41538658363` SUCCESS con **31/31 XML** verificado en logs.
- Commit `67fda3b32f8405685bf7bf62365dbcd063806b97` crea Inicio con exactamente **Pendientes, Avisos, Actividad reciente, Guardados**, navegación de cada tarjeta y adapter read-only `FaroV5HomeReadPort` sin cifras inventadas. CI `#41539252466` SUCCESS con **34/34 XML** verificado en logs. Ambas CI del mismo workflow dirigido; no relanzadas.
- **No afirmar A1 completo:** el puerto Home no tiene binding de owner real en composición. Sin binding, UI muestra `No disponible`, distinto de cero real. Falta fuente N-022 `home.read`, identidad/dedupe, permisos, asOf y recuperación. La ruta Pendientes→Asuntos es una decisión de navegación provisional basada en N-031 `attention.read`; el cálculo canónico de Pendientes no está implementado.
- S2 8/8 CLOSED, app V2 OFF, S4 OFF, main/V4.4 intactos. X01–X12 y campaña física final NOT_RUN.

## Comparación V4.4 adicional (SOLO LECTURA)
- `app-v4/.../FaroV4HomeMateriality.kt` blob `4f5a4f6c` construye señales desde Finance, Objetivos, FollowUp y SmartCompra con orden/dedupe. No copiar esos conteos a los cuatro indicadores S3 sin contrato.
- `FaroV4HomeSurface.kt` blob `d3d3e844` mezcla lecturas y `radar.refresh()` al abrir. **No reutilizar ese efecto**: N-022 `home.read` debe ser puro, sin refresco con side effect.
- Fuentes candidatas para los cuatro indicadores: Pendientes N-031 `attention.read` (semántica exacta pendiente), Avisos N-032 `notice.read`, Actividad N-015 `history.query`, Guardados store auxiliar aún no contratado. No inventar valores por falta de fuente.

## Siguiente turno A (20:00)
1. Releer writer/queue/HEAD y Actions exactas; no repetir las dos CI PASS por SHA.
2. Prioridad A1: conectar `FaroV5HomeReadPort` a N-022 `home.read` puro, con estados parciales, autorización, timestamps y tests. Si owners faltan, marcar BLOCKED/READ_ONLY e investigar código existente, no inventar datos.
3. No habilitar A2/A3/A4 hasta que sus dependencias estén satisfechas. A3 requiere bounded reopen +8 previo. A5 integra después; una campaña física al final.
4. Respetar corte absoluto 22:00 AR y writer CAS. No publicar main ni activar app V2.
