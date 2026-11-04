# FARO V5 — S3.A2: protección de resultado antiguo tras revocación ACL · 04/11/2026

## Evidencia verificada

- Repositorio `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`. Baseline previo `1a7eeba76dcd897c34d271f6a9f54fdb708c9962`.
- Código `ed5b129338cb56d61db947f4d8751e4b33f7e7ff`: el adaptador de búsqueda conserva el `SearchAuthorityStamp` completo de la última página publicada (generación, epoch ACL, revisión). Lo invalida al iniciar búsqueda, revalida tras mapear la página y exige que coincida antes de abrir un hit antiguo. No muta la fuente canónica.
- Regresión reforzada `703ebd27be50739c4125c2ce1486e6de96859494`: consultar -> revocar ACL sin nueva consulta -> denegar apertura del hit viejo -> reautorizar con **nuevo epoch** sin nueva consulta -> seguir denegando el hit viejo -> nueva búsqueda y apertura confirmada -> cambio de generación denegado. Esta secuencia falla contra el adaptador anterior a la corrección.
- CI de código `#41825188553`: **SUCCESS** exact SHA `ed5b1293`. Log: `S3_HOME_NOTICE_XML_PASS 9/9`, `S3_WORKSPACE_SEARCH_XML_PASS 3/3`, `S3_SEARCH_BRIDGE_XML_PASS 3/3`.
- CI definitivo `#41825631364`: **SUCCESS** exact SHA `703ebd27`; `BUILD SUCCESSFUL in 3m 59s`; log XML exacto: `S3_HOME_NOTICE_XML_PASS 9/9`, `S3_WORKSPACE_SEARCH_XML_PASS 3/3`, `S3_SEARCH_BRIDGE_XML_PASS 3/3`. **15/15 pruebas dirigidas**, no certificación integral Android.

## Autoridades, límites y estado

- **S3.A2 permanece PARCIAL:** no hay propietario de ACL Workspace con epoch genuino y evento independiente observable de revocación en el host shipping. `searchReadPort` permanece nulo; no usar epochs fijos, grants inventados o contenido de índice para eludir autorización.
- `FaroV5HostCertification.V2_ACTIVE_ENABLED=false`. El adaptador probado y la federación canónica siguen preparados, NO montados como búsqueda real en producción. El guard de procedencia no sustituye eventos ACL a Compose ni autorización real del propietario.
- Writer exclusivo CAS: adquirido/liberado epochs 106/107 y 108/109; checkpoint documental epoch 110 y liberación esperada 111. Cola CLOUD `STAGED`, `activation_authorized=false`; Noche y Auto OFF.
- S2 `8/8 CLOSED`; S3 OPEN; S4/V2 OFF; catálogo 172 intacto; Guardados +8 candidatos sin aprobación. X01–X12 y campaña física única **NOT_RUN**.
- Tiempo efectivo humano **NO_MEDIDO**. Tiempo CI registrado separadamente, no confundido con desarrollo útil.

## Próxima unidad

Resolver la autoridad ACL real de Workspace y sus eventos de revocación/generación entre instancias; conectar lectura/selección y revalidación de Buscar contra esos propietarios; probar invalidación de resultado visible y navegación bajo revocación. Reutilizar pruebas históricas PASS solo en su mismo alcance y SHA. Completar fuentes canónicas de otros dominios después, sin activar V2 ni S4. En A1 siguen pendientes N031/N015/Guardados reales.
