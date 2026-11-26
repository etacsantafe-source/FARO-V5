# FARO V5 · A1C4 · Revalidación de aceptación global

Unidad `S4_W05_A1C4_GLOBAL_DEV_ACCEPTANCE`. Se conserva la matriz T01–T13 de `FARO_S4_W05_A1C4_GLOBAL_DEV_ACCEPTANCE_B1_B5_20261125.md` y los B7 históricos acotados.

## B1 — Evidencia
El verificador SQL13 y el mantenimiento autenticado de SQL12 cerrado tienen B7 propio. La aceptación usa los hosts reales N-036, N-035 y N-016 y vincula los recibos al mismo inventario a través del cambio de generación. Los estados APPLYING, PREPARED y UNKNOWN se comprueban antes de mover el puntero.

## B2 — Reutilización
Llamar al mantenimiento aislado `FaroV5ClosedHousehold12DevMaintenance` con el mismo gate y el verificador canónico, tras cerrar los hosts SQL12. Ejecutar `prepare` y `start` desde el harness de pruebas. Las verificaciones de UI usan el panel y los lectores existentes.

## B3 — Alternativas
El mantenimiento compone los formatos v9/v10 y acredita la selección mediante ticket y journal. El fixture inicial establece el punto de partida de SQL12 para probar los hosts. Para el cambio SQL12→SQL13 usar únicamente el entry point de mantenimiento certificado. Conservar el puntero previo al revocar y observar COMPLETED o el bloqueo a través de los puertos existentes.

## B4 — Exactamente tres rutas
- `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05A1DevAcceptanceTest.kt`
- `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5W05A1DevAcceptanceAndroidTest.kt`
- `.github/workflows/faro-v5-s4-w05-a1c4-global-dev-acceptance.yml`

## B5 — Pruebas y cierre
JVM usa Apply real para compra 10, recepciones 7 + 3 y Undo con recibo durable. Interrumpir entrega a Workspace de un resultado COMMITTED y conciliar con el recibo existente, sin repetir Apply. Probar rechazo de contexto viejo y acceso ajeno. Vincular B7 de recovery PREPARED sin receipt y rechazo ABA con sus XML y SHA; no relanzar suites certificadas. Android API35 verifica fuente 200 %, aislamiento DEV, accesibilidad, autorización foreground, reposo durante mantenimiento, restauración del historial y compensación. Exigir ambos XML originales estrictos y APK DEV; G15 físico NOT_RUN. El journal COMPLETED y el puntero único deben observarse fuera de la UI.
