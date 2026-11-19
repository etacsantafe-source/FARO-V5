# FARO V5 — W03-D2B2B3A · Preflight ACTIVE Auxiliary4 en DEV

Unidad S4_W03_D2B2B3A_ACTIVE_AUX4_PREFLIGHT_DEV. Base funcional 5e84764a7e3e6ea86677ed5d22342e2b3b3fde08, CI archivo 44605196641. Preauditoría previa al cutover lógico.

## B1 — Evidencia
FBS2v5 en STAGED, Auxiliary4 snapshot/restore y migración desde Auxiliary3 se verifican separadamente. FaroV5GenerationStoreGraph reabre ACTIVE Finance9 únicamente con Auxiliary3 y runtime ticket Finance9. AuxiliaryDeliveryStore tiene un constructor de recibos solo STAGED que podría migrar esquema 3, por lo que no se debe reutilizar para ACTIVE. Falta una admisión exacta y verificable de los cinco owners con Auxiliary4, sin cambiar el puntero ni otorgar permisos.

## B2 — Comparación y reutilización
V4.4 conserva recordatorios independientemente del canal de OS. Reusar ActiveSqlitePreflight read-only, existing V2 marker, pin completo y material gate del reopen Finance9. Reusar la tabla de recibos, CAS transaccional, fingerprint y respuesta idempotente ya certificados, evitando un segundo SQLite owner. Los modos Aux3 y Aux4 permanecen explícitos durante la transición.

## B3 — Investigación
USAR apertura SQLite READWRITE preexistente sin CREATE, con NOFOLLOW y comprobación de user_version antes de configurar WAL. SQLite https://www.sqlite.org/c3ref/open.html y https://www.sqlite.org/c3ref/c_open_autoproxy.html y https://www.sqlite.org/pragma.html, dominio público https://www.sqlite.org/copyright.html. REFERENCIA https://www.sqlite.org/wal.html para sidecars; reusar ActiveSqlitePreflight en vez de copiar archivos calientes. DESCARTAR on-open migration de ACTIVE y detectar el schema por excepciones de un constructor que ya pudo escribir.

## B4 — Unificación y rutas
Un único grafo con Finance9 exacto y Auxiliary4 exacto. Guardar modo de owner de recibos STAGED/ACTIVE explícito, mismo motor y misma conexión. El modo ACTIVE exige handle existente, material gate y full pointer; antes de SQLite writable, todos los stores pasan preflight estricto. El modo ACTIVE excluye los puertos STAGED de snapshot/restore y abre receipt CAS bajo el mismo fence de commit. Pruebas JVM en storage root aislado con pointer documental de fixture; sin aprobación de activación, host ni UI.
- v5/store/auxiliary/src/main/kotlin/ar/com/faro/v5/store/auxiliary/AuxiliaryDeliveryStore.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Auxiliary4ActivePreflightTest.kt
- .github/workflows/faro-v5-s4-w03-d2b2b3-active-preflight.yml

## B5 — Auditoría previa
Rechazar ACTIVE Auxiliary3 en ruta 4 antes de cualquier migración y viceversa; probar archivo sin ledger, corrupto/versión futura, puntero stale/ABA y descriptor cambiado. Mantener guardado/delivery/policy y acceso autorizado al ledger recibo en Aux4 con revisión exactamente una vez. Si se pierde el fence, no hay mutación ni nuevo recibo. No escribir journal, pointer, respaldos de usuario, permisos o OS en esta unidad. Cutover D2B2B3B reutilizará el owner durable y abrirá ACTIVE4 solo después de certificar preflight/rollback.
