# FARO V5 — S3.A2 + S3.A4: cuatro commits funcionales encadenados · 04/11/2026

## Fuente de verdad y exclusión

- Repositorio: `etacsantafe-source/FARO-V5`, **única rama funcional** `work/s3-night-20261102`; baseline `ebe538308eff5c41218b57d2ca7a4ee29c7023c3`.
- HEAD de código final `a72b723475c0ea37f57c62752324765577d2b9c8`. Solo se modificaron `v5/app/**`, `v5/surface/**` y el workflow dirigido; V4.4, main, catálogos, Reality, schemas y credenciales intactos.
- Writer global Git CAS: adquiridos y liberados epochs 112/113 (A2) y 114/115 (A4); checkpoint documental epoch 116, liberación esperada 117. Cola CLOUD `STAGED`, `activation_authorized=false`, Noche/Auto OFF.

## Unidad 1 — S3.A2: procedencia por publicación

1. `c27905a8290acfd24d9562af49491fd04f193838`: cada página canónica emite `issuanceId` aleatorio por hit, con publicación in-memory asociada a `SearchAuthorityStamp` y conjunto exacto de hits. La generación anterior nunca puede resucitar por una búsqueda reciente del mismo título, ID y epoch; se rechazan hits fabricados y respuestas de consultas intercaladas.
2. `ca0eec7146c3e933d726acb1b20f00d267f4b624`: una invalidación entregada por el propietario revoca los hits publicados, invalida las consultas en curso y se verifica también la publicación tras selección. **No emite ACL ficticia**.
3. CI definitivo `#41827910601` **SUCCESS** exact SHA `ca0eec71`. XML del job: `S3_HOME_NOTICE_XML_PASS 9/9`, `S3_WORKSPACE_SEARCH_XML_PASS 3/3`, `S3_SEARCH_BRIDGE_XML_PASS 5/5` (**17/17 dirigidas**).
4. Histórico `#41827753966` sobre `c27905a8`: la compilación y los 4 tests pasaron, pero el verificador del workflow aún esperaba 3; falló su regla XML, corregida en `ca0eec71`. **No repetir ese run ni marcarlo como PASS**.

**Límite A2:** ACL independiente y observable del Workspace shipping AÚN NO implementada/probada. `searchReadPort=null` en producción; el adaptador y federación permanecen preparados, sin búsqueda real habilitada, sin falsos grants. `FaroV5HostCertification.V2_ACTIVE_ENABLED=false`.

## Unidad 2 — S3.A4: pantalla de Avisos con propietario real

1. `a4327d7753958d35cfdcaa141378ef4b4f4a46fa`: `FaroV5AvisosScreen` y tipos READ_ONLY; distingue carga no disponible, cero real y cobertura parcial, no marca leído. Drawer único, navegación existente, y la instancia se reemplaza al cambiar el readPort.
2. `a72b723475c0ea37f57c62752324765577d2b9c8`: binding real `AuxiliaryDeliveryStore.readNotices` de la generación activa del shipping V1; gate readiness/puntero pre/post; invalidaciones del owner por commit/cierre/recovery/generación. Muestra solo OPEN no DISMISSED, conserva SEEN, dedup por NoticeId, primero `NEEDS_ATTENTION` luego informativos, máximo 100 por página con `complete=false` cuando truncada. `SENSITIVE` devuelve solo «Aviso privado», sin texto.
3. Comparación READ_ONLY V4.4: `app-v4/.../followup/FaroV4Notifications.kt` no modifica Reality al notificar; `app-v4/.../radar/FaroV4RadarNotificationContract.kt` separa niveles y preferencias; no importar su persistencia. Benchmark ya recuperado: `docs/v5/FARO_S3_BENCHMARK_APPS_20261102.md` (Priority/Otros, no auto-mark-read, no snooze no aprobado).
4. CI de UI `#41828553243` **SUCCESS** exact SHA `a4327d77`; `S3_SURFACE_XML_PASS 50/50`. CI completo dirigido de app `#41828757767` **SUCCESS** exact SHA `a72b7234`: Home 9/9, Workspace 3/3, Search Bridge 5/5, **S3_AVISOS_XML_PASS 4/4** (**21/21 dirigidas**).

**Límite A4:** la lectura de Avisos es real y conectada al host V1, pero S3.A4 global permanece PARTIAL: no se añadió una categoría «crítica» sin autoridad real ni snooze, resúmenes/Actividad siguen pendientes y no hay prueba física. La pantalla es READ_ONLY, no genera recibo Reality, no envía/descarta avisos ni altera el estado de entrega Android.

## Próximos bloques sin duplicación

- **S3.A2:** autoridad ACL real de Workspace con revocación independiente, revalidación y eventos de UI; federación y navegación por propietarios genuinos; pruebas instrumentadas en la campaña física única final.
- **S3.A1 y A4:** completar propietarias N031 `attention.read`, N015 `history.query` con procedencia temporal COMMITTED, y restantes fuentes sin fabricar contadores. No crear esquema N031 ni timestamp durable N015 sin revisar FBS/backup/restore.
- **S3.A3:** Guardados +8 operaciones continúan CANDIDATAS, no aprobadas; catálogo oficial **172**, no mutar.
- **S3.A5:** integración Chat/review, gates X01–X12 y una sola campaña física final. S2 8/8 CLOSED; S4/V2/Auto/Noche OFF.

## Métricas y evidencia

- Cuatro commits funcionales nuevos, más checkpoint documental, y tres runs finales exitosos: `41827910601`, `41828553243`, `41828757767`. No son equivalentes a end-to-end Android.
- Tiempo efectivo desarrollo/investigación humano **NO_MEDIDO**; runtime CI observable en GitHub, no inferir productividad.
- Pruebas físicas **NOT_RUN**. S3 **OPEN_PARTIAL**; no declarar cierres completos.
