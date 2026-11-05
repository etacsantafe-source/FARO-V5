# FARO V5 — S3.A1 N032: secuencia de puntero durable (B1–B5) · 05/11/2026

Unidad **S3_A1_N032_POINTER_SEQUENCE_FENCE**. HEAD auditado `f053ad278dfc7ee28a63141498da6f7993bbf757`. Subunidad de seguridad de **Avisos**, NO cierre de A1 ni autorización de N015/N031/Guardados.

## B1 — evidencia exacta
- `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java`: `readActiveHomePort()` usa el `DeliveryGraph` y el factory Avisos. Su `deliveryGraph()` solo fija `pinnedPointer` si existe Workspace V2; con V1, `DeliveryGraph.matchesActivePointer()` acepta mismo generationId aunque el puntero se haya publicado A→B→A con otra secuencia.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5HomeNoticeBindingFactory.kt`: el sello N032 exige readiness y mismo generationId, pero no compara secuencia durable. Una invalidación de puntero es señal de UI, no barrera de lectura si se pierde.
- `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/FileActiveGenerationPointer.kt`: `ActiveGenerationPointer(sequence,generationId)` es durable y cada CAS cambia la secuencia. Reutilizar esta identidad real; no inventar owner ACL ni contador.
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5HomeNoticeBindingFactoryTest.kt`: valida cambio de generationId, evento de puntero y cierre del store, pero no prueba A→B→A con **mismo ID y distinta secuencia**.

## B2 — comparación V4.4 / V5
- V4.4 Home refresca al abrir, sin una garantía contractual de identidad durable del puntero en cada lectura. V5 ya tiene el puntero con secuencia y `FaroV5HomeReadCoordinator` con read-stamp pre/post/batch. **ADAPTAR el sello real V5**, no copiar el refresco mutante de V4 ni escribir otra autoridad.
- V5 Chat/Search active graph ya usa identidad durable completa para Workspace V2; A1 Avisos debe tener la misma defensa tanto en V1 como V2, sin habilitar V2.

## B3 — alternativas externas verificables
1. Android offline-first, propietario de datos canónicos: https://developer.android.com/topic/architecture/data-layer/offline-first (**ADAPTAR** validación al obtener cada valor desde dueño real, sin caché de UI).
2. OWASP MASVS PRIVACY-1: https://mas.owasp.org/MASVS/controls/MASVS-PRIVACY-1/ (**ADAPTAR** minimizar exposición, fail closed en revocación).
3. Android Room multi-instance: https://developer.android.com/reference/androidx/room3/RoomDatabase.Builder#enableMultiInstanceInvalidation() (**DESCARTAR como prueba de autorización**: invalida tablas, no identidad del puntero ni ACL).
4. CommonsWare Room Across Processes: https://commonsware.com/Room/pages/chap-processes-001.html (**REFERENCIA** para límites multiproceso, no prometer bus universal).
Alternativas: (a) **USAR** pin de `ActiveGenerationPointer` (mismo dueño durable, sin dependencia), (b) **DESCARTAR** confiar solo en emisiones de invalidación (evento perdido), (c) **DESCARTAR** epoch inventado de UI/Room. Sin código de terceros, licencias ni APIs pagas.

## B4 — solución DELTA ONLY
- Cambiar **solo** factory N032, composition root y pruebas dirigidas.
- En apertura del `DeliveryGraph`, capturar `FileActiveGenerationPointer.read()` para *V1 y V2*, verificar que apunte a generación seleccionada y usar igualdad completa de `ActiveGenerationPointer` para detectar A→B→A incluso sin listeners. Mantener el mismo único graph/owner.
- Agregar método explícito del factory para la composición productiva que admita `BooleanSupplier` de autoridad del puntero fijado; preservar API legacy del factory para pruebas/compatibilidad. El sello N032 debe denegar si readiness, generación, puntero o owner no están vigentes. No incrementar contadores por cuenta propia.
- No tocar S2, Reality, backup, OperationContract catálogo 172, N015/N031, Guardados, V2 shipping, S4 ni políticas de egress.

## B5 — casos adversariales / gates
1. El puntero publica generación A→B→A, mantiene mismo ID pero secuencia nueva: el Home port antiguo **no** vuelve a admitir Avisos ni lee store. Un graph V1 cacheado debe dejar de coincidir con la identidad completa.
2. Cambio A→B→A **durante** `readNotices`: la revalidación posterior deniega el conteo aunque el ID final sea A. Peligro de ABA reproducido con puntero real, no una cadena sintética.
3. Puntero ausente/lectura falla, startup no ready, owner cerrado, excepción de autoridad → `UNKNOWN`, nunca cero inventado; cancelación preservada.
4. Conflictos con cambio a V2, rollback/restauración, multiproceso: pin verifica en cada lectura; eventos posteriores al render siguen requiriendo invalidación y no se certifican cross-process por este commit.
5. B6: ejecutar pruebas JVM nuevas de puntero+regresión Home, y CI exacta por SHA; B7 independiente posterior con diff y XML.
**Dictamen:** B1–B5 VERIFIED **solo** para S3_A1_N032_POINTER_SEQUENCE_FENCE. No cerrar S3.A1; N015/N031 y Guardados permanecen UNKNOWN, X01–X12 y G15 diferidos S3.A5.
