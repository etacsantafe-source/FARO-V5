# FARO W01 D1B1C2C — entregar un único grafo DEV bajo el lease existente

Unidad `S4_W01_D1B1C2C_FINANCE9_DEV_SINGLE_GRAPH_MOUNT`. Preauditoría del puente de almacenamiento, sin rutas Android ni ADB. W1 OPEN, W2 STOP, V2 productiva OFF.

## B1
C2B persiste y recupera la decisión de activación, pero devuelve solo selección. `FaroV5GenerationStoreGraph.reopenVerifiedActiveFinance9Dev` ya admite puntero completo, schema9 exacto y gate compartido. Falta poner ese opener detrás del lease único en orden de arranque: resolver selección durable, abrir una vez, verificar salud y soltar graph antes que el lease.

## B2
Reutilizar el runtime C2B, verificación del layout, journal/replay y opener de C1. Conservar los invariantes de V4.4 de writer, receipts y datos reales. El montaje de este delta aún no vincula UI o concede permiso de lectura/Apply.

## B3
Elegir una única instancia de grafo por lease, propiedad del runtime DEV durante su vida. Descartar que UI abra un SQLite directo, que el probe de recuperación corra con el grafo montado o que una marca de UI se considere journaling. `isCurrent` comprueba metadata de almacenamiento sin abrir SQL. Los triggers del runtime C2B siguen corriendo sus pruebas y auditan el checkout real.

## B4
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevCutoverRuntime.kt`. Exponer adquisición normal sin inyección de fallos para el adaptador DEV, y superficie para abrir el Finance9 exacto después de recuperar/activar. El runtime retiene el único grafo y lo cierra antes del lease. Revalidar selección y registro exacto previa a cada consulta de validez.
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevSingleGraphMountTest.kt`. Pruebas de un solo grafo, secuencia stale, cambio durable durante la sesión, liberación y reapertura.
- `.github/workflows/faro-v5-s4-w01-d1b1c2c-finance9-single-graph.yml`. JVM dirigida, XML exacto.
- `.github/workflows/faro-v5-s4-w01-d1b1c2b-finance9-durable-host.yml`. Solo actualizar preflight al verificador de checkout para regresión del archivo compartido.
READY EXACT4 tras guard. B6 y B7 JVM, sin comandos Android.

## B5
Rechazar lease cerrado, selección que cambió ID o sequence, journal distinto/incompleto, replay ausente/ajeno, apertura doble, recovery con graph vivo y acceso posterior al cierre. Probar fuente reabierta con nuevo lease. La UI W01 debe usar después el mismo objeto; los workflows existentes que se activan por el arranque Android incluyen ADB en emulador y ese montaje queda pendiente de autorización. No tocar producto, W2, termux ni teléfono.
