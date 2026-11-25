# FARO V5 · W05-A1B3C2C1 · Evidence2 SQL11 Android ACTIVE DEV

## B1 — Evidencia
C2C0 certificado en `f96bbb154ca09450d4123f3eac69b24fe12185c4` (26/26 XML, guardia 45528363998). Certifica transición con bases selladas y recuperación antes de abrir el host. C1C histórico certifica la lectura Evidence1, con contrato WAL. Falta reapertura Android Evidence2 de ese mismo SQL11. No repetir cutover de un COMPLETED aceptado.

## B2 — Comparación y activos reutilizados
V4.4 no tiene el contrato Evidence2/SQL11 ni el puntero con secuencia de V5. Adaptar `FaroV5Household11ActiveDevReadHost`, su `ActiveSqlitePreflight` JNI y el cierre en `FaroV5V2CoreDevActivity.onPause`. Reutilizar C2C0 para recuperación incompleta, únicamente después de un SWITCH_PREPARED durable. Conservar la rama Evidence1 y su botón en el build aislado.

## B3 — Alternativas
[SQLite WAL](https://www.sqlite.org/wal.html#read_only_databases) documenta el WAL como parte del estado y las condiciones de apertura solo lectura. [SQLiteDatabase](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#OPEN_READONLY) expone el modo sin CREATE y [AOSP Compatibility WAL](https://source.android.com/docs/core/perf/compatibility-wal) describe el comportamiento de la plataforma. Adoptar OPEN_READONLY para la consulta Android y el probe ACTIVE existente para integridad/versión de los cinco stores. Ambos mecanismos ya están disponibles offline sin dependencias ni coste nuevos. Descartar immutable (lectura puede perder WAL) y copiar/checkpoint activo (rompe consistencia y no permite afirmar cero mutación de filas). Hipótesis: las dos reaperturas ven las filas confirmadas vía WAL y el lease deja de servir al cerrar o revocar.

## B4 — Diseño y rutas
Lectura Evidence2 por ticket v2 exclusivo, journal RESTORE COMPLETED aceptado, fence RESTORE, puntero exacto N+1, raíz/archivos canónicos. Evidence2 exige schema2 y tabla de términos tipados. Mantener ABI y variante aisladas. Releer ticket/journal/fence/puntero antes y después de la consulta bajo el lock raíz compartido y el gate, verificar revisiones y recuentos esperados. Usar el mismo ciclo de vida del botón nativo y una fixture SQLite real proveniente de C2C0 para Android API35. Rutas exactas:
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2ActiveDevReadHost.kt`
- `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05Household11Evidence2DevStartup.kt`
- `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2ActiveDevReadHostTest.kt`
- `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5W05Household11Evidence2ActiveAndroidTest.kt`
- `.github/workflows/faro-v5-s4-w05-a1b3c2c1-evidence2-android-reopen.yml`

## B5 — Negativas y cierre
VALIDATED no hace switch; journal incompleto autorizado converge solo vía C2C0; rollback no entrega target; ticket v1 o coexistencia, cambio de fase/identidad, pointer ABA, host abierto o revocado y archivos enlazados o inexistentes se rechazan sin entregar lector. La app abre solo READONLY, observa WAL y libera el lease al cerrar o ir a fondo. Verificar el modo no productivo, ausencia de Apply/pagos e instrumentación API35 con XML original además del JVM. G15 físico pendiente. A1C0 debe comprobar el grant; el descriptor `stock.receive` de N-035 ya figura en el catálogo 180 como Household REALITY_MUTATION, BINDABLE, MERGE.
