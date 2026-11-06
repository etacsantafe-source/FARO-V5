# FARO V5 — S3.A4 N031 planning semantic section codec · B1–B5
Fecha 2026-11-06 AR. Baseline exacto `79bee10f36722a78af2b5be4893ed1ffff56a8e5`.
Unidad `S3_A4_N031_PLANNING_SECTION_CODEC`. Exclusivamente **codec interno JVM de sección candidata** para futuro FBS2 v3, SIN ampliar el encoder FBS2 real, SIN Realm/SQLite, ni backup exportable o restaurador público. El STOP de owner durable permanece.

## B1 — Evidencia verificable y aceptación
- Reminder & MonthlyPlan typed contracts existen por primera vez en módulos JVM, B7 `docs/s3-night/S3_A4_N031_TYPED_PLANNING_CONTRACTS_B7_PASS_20261106.md` código `1bf0f03e`; CI nueva #42254471143 **12/12**, antigua G4 #42254470896 **8/8**, guard #42254470820 SUCCESS.
- `FaroV5G4DomainSemanticCodec.kt` es el formato de sección tipado G4 `G4B1`, schema7, tamaño máximo 4 MiB, orden canónico UTF-8 BINARY y validación. `FaroV5FiveStoreSemanticCodec.kt` v1/v2 actual nunca tuvo Planning. `FaroV5FiveStoreBackupCapture.kt` capta solo Reality legacy+G4; no hay Reminder/MonthlyPlan persistent owner. `RealityStore.kt` schema7. **NO emitir FBS2 v3** ni declarar respaldo real por un encoder standalone.
- Resultado binario de esta unidad: un `PlanningDomainSectionSnapshot(schemaVersion=8, reminders, monthlyPlan)` tipado, serializador con magic/version propios y decode bounded/strict UTF-8, 6 tests de round-trip y entradas adversas, más regresión de G4 v2 5/5 y FBS2 v1 7/7. Ninguna ruta productiva lo invoca.

## B2 — V4.4, reutilización y alternativas
- V4.4 tiene Reminder (regla+ocurrencia) y MonthlyPlan separados (referencia preaudit `S3_A4_N031_REMINDER_MONTHLYPLAN_OWNERS_B1_B5_STOP_20261106.md`); NO copiar persistencia o ejecución de V4.4 ni inferir pagos.
- V5 G4 codec provee patrón DataInputStream/DataOutputStream, límites por cadena, strict UTF8, validación y orden por IDs. Usar como referencia de diseño, no cambiar su bitstream ni el de FBS2 v1/v2.
- Alternativa A JSON libre: rechazar por falta de presupuesto estable/canonicalización; B protobuf plugin nuevo: descartar dependencia/migración no necesaria; C codec binario privado independiente versionado: ELEGIR. No API paga, no código externo.

## B3 — Investigación de soluciones externas y negativos
- SQLite backup oficial https://www.sqlite.org/backup.html: snapshot de un DB coherente pero no cinco dueños ni identidad del archive. REFERENCIA; codec no debe hacerse pasar por backup completo.
- Android DB migration https://developer.android.com/training/data-storage/room/migrating-db-versions: exige migraciones que preserven datos y pruebas de todas rutas; REFERENCIA para futuro 7→8, sin usar Room sobre bundled SQLite.
- Tasks.org https://tasks.org/docs/recurrence/ y https://tasks.org/docs/notifications/: regla, ocurrencia y notificación OS diferentes. ADAPTAR separación de tipos y timestamp real.
- Negativas a capturar: magic/version/schema desconocidos; EOF/truncamiento/tailing; longitud o registro fuera de cota; UTF8 malformado; IDs duplicados/desordenados; ocurrencia huérfana; enum inválido; pago CONFIRMED sin instante; fecha civil y zona inválida. Hash de sección no sustituye AEAD de futuro FBS2.

## B4 — Delta exactamente cuatro rutas
1. `v5/store/generation/build.gradle.kts` agrega solo `implementation(project(":owner:reminders"))` y `implementation(project(":owner:monthlyplan"))`; módulos nuevos ya B7.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5PlanningDomainSemanticCodec.kt`: snapshot schema8, codec interno magic distinto, version1, <=4MiB, texto UTF8 estricto, orden canónico de rule/occurrence/item ID, validador no-op sobre datos vacíos. No conexiones DB ni generador de eventos/recurrencia.
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5PlanningDomainSemanticCodecTest.kt`: seis casos (real round-trip; empty; oversized/canonical order; version/magic; truncation/trailing; referencia/estado invalid).
4. `.github/workflows/faro-v5-s3-n031-planning-codec-directed.yml`: Java21 + Android SDK para Gradle config; 6/6 nuevo + G4 5/5 + FBS2 v1 7/7, XML exacto, sin Android físico.
- No permiso a `RealityStore`, `FaroV5FiveStoreSemanticCodec`, `FaroV5FiveStoreStagedRestore`, export/app/surface/WorkManager, schema ni puntero.

## B5 — Auditoría adversarial previa
- No hay owner durable aún: este codec *no puede* codificar datos del usuario porque no tiene port de captura ni referencia desde FBS2 ni host. Crear el contrato estructural tipado solo mejora la preparación. No declarar que los datos nuevos sobreviven respaldos.
- DataInputStream no debe aceptar longitudes negativas, exceder 4 MiB, usar Strings UTF-8 con reemplazo silencioso o confiar en filas no canónicas. Validar snapshot dentro de codificación y decodificación. No suponer que índices/sort otorgan permiso de lectura.
- La unidad B6 debe incluir **solo esas cuatro rutas**. Si CI falla STOP, RED_FIX con nueva revisión. B7 PASS_SCOPED_CODEC_ONLY tras XML y guard exactos, sin liberar export G4 ni activar persistencia.
- Todo B5 del owner real `schema7→8 + FBS2v3 + active generation/ACL` permanece STOP por dependencias; S3.A4 OPEN, A3 Guardados+8 no aprobadas, 172, A5 G15 X01–X12 NOT_RUN, V2 prod/Noche/Auto/S4 OFF. Main/V4.4 intocados.

**B5 READY para este codec privado y aislado, NO para owner durable, port, backup ni integración FBS2.**
