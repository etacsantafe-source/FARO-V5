# FARO V5 — W03-D2B2B1 · FBS2 con recibos de Auxiliary4

Fecha 2026-11-19. Unidad S4_W03_D2B2B1_FBS2_RECEIPT_CODEC_DEV. Base exacta 15b7f6a513b0e4d2e845b78d7a1e251b6162012d. Preauditoría documental. El permiso vigente corresponde a D2B2A, consumido.

## B1 — Evidencia
HEAD remoto y guardia 44592981102 SUCCESS. Writer remoto RELEASED 1325 y cola STAGED. D2B2A SHA 5156d98eb924477293ad054759ea0d2068d5b581, CI 44591846981, verificador XML 16/16, ledger en Auxiliary4 STAGED. El formato Finance9 FBS2v4 termina en cinco secciones y el snapshot de Auxiliary3 solo contiene Notices y Guardados. Los puertos de backup, restore y portability de Aux4 están bloqueados. Primer incremento comprobable de D2B2B: una representación FBS2v5 interna con el ledger completo, incluso vacío, distinta de la ausencia legacy. Aceptar solo round-trip canónico, rechazo de corrupción/omisiones y rechazo de v5 en la ruta de restore legacy.

## B2 — Comparación y reutilización
V4.4 FARO@bfa0ee71f5b9cd104baaadd71f1e77a61f203939, FaroV4FollowUpSurface.kt (blob e14db5616c18130d0d6485e4835a6cf53d34d3f5), mantiene recordatorios guardados aunque no se autoricen notificaciones. V5 separa ya ese owner de Avisos y persiste la atención por CAS. El formato Finance9 usa snapshots semánticos, límites previos a reservar y verificación por sección. Reusar el bloque completo FBS2v4, las clases de recibo y el digest SHA-256. No importar DB/UI de V4.4.

## B3 — Alternativas
USAR datos tipados y acotados dentro de FBS2, conservando el bloque completo Finance9 y una sección obligatoria de recibos. La copia física SQLite Online Backup API / VACUUM INTO (https://sqlite.org/backup.html, licencia dominio público https://www.sqlite.org/copyright.html) es REFERENCIA para un snapshot de una DB, DESCARTAR como formato portable porque incluye tablas internas y no ofrece consistencia de cinco owners. SQLite WAL (https://www.sqlite.org/wal.html) y PRAGMA integrity_check con foreign_key_check (https://www.sqlite.org/pragma.html) son REFERENCIA para preflight y recuperación posteriores. Sin SDK ni dependencias nuevas.

## B4 — Unificación y rutas exactas
Codec aislado DEV, con cabecera FBS2 versión 5, dos payloads acotados, digest de cada uno y verificación estricta de longitudes y EOF. Payload base FBS2v4 con Auxiliary3 como proyección común de Notice/Guardados; payload adicional contiene las filas de Auxiliary4 con claves en orden canónico y relación validada con Notices. D2B2B1 convierte entre el modelo semántico completo y bytes, sin abrir SQLite. D2B2B2 consumirá el codec en un coordinador autenticado con captura bajo quiescencia y restore transaccional en stage. D2B2B3 cubrirá el preflight ACTIVE4, journal, rollback/restart y host de Avisos. Estos siguientes incrementos mantienen sus propios B1–B7.
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Auxiliary4Fbs2Codec.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Auxiliary4Fbs2CodecTest.kt
- .github/workflows/faro-v5-s4-w03-d2b2b1-receipt-codec.yml

## B5 — Auditoría previa
Rechazar duplicados, revisión desbordada o superior a la Notice actual, referencia huérfana, Notice ajena a Recordatorios/Avisos local, y hash de petición distinto del contrato aplicado en D2B2A. Usar UTF-8 estricto, presupuesto global de FBS2 y límites de campo y fila. Digest detecta daños y no sustituye AEAD. Un v5 truncado o con digest inválido falla; la ruta v4 preexistente rechaza v5 de manera explícita por versión. Un ledger vacío exige igualmente su sección versionada y se distingue de v4. Nada modifica bases/leases/runtime productivo ni dispara notificaciones. Probar fixtures con recibo y Notice reales, corruptos/huérfanos/duplicados, vacío explícito y rechazo desde el codec legacy. Preflight por las tres rutas y guardia verde antes de código.

## Continuidad
Tras guardia documental exacta, publicar READY solo para D2B2B1. B6 JVM dirigido y XML. Tras B7, B1–B5 de captura/restauración autenticada STAGED, luego cutover/host y cierre D, E, G. La promoción de Auxiliary4 queda pendiente de esos pasos.
