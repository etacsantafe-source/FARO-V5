# FARO V5 — S3.A2 G5 cuatro owners en V2 DEV real — B1–B5 (05/11/2026)

**Unidad exacta:** `S3_A2_G5_REAL_ACTIVE_OWNER_DIRECTED`; rama `work/s3-night-20261102`; HEAD auditado `50d89151692fbebaba4f5272fb6f3c9fd28999c7`. **B6 pendiente de CI; B7 pendiente de auditoría independiente.** No es autorización para dar G5 físico por cerrado ni habilitar V2 shipping.

## B1 EVIDENCIA
- Campaña física parcial verificada por usuario y registrada en `docs/s3-night/S3_A2_G5_ANDROID_PHYSICAL_PARTIAL_20261105.md`: Chat restaurado, búsqueda Workspace positiva/negativa, navegación, reinicio, UID y permisos favorables. Los registros reales Reality de Finanzas, Objetivos y Asuntos estaban vacíos; no hay evidencia de sus resultados positivos en el Moto G15.
- G4 CI `#42016712209` 27/27 verifica federación canónica con `RealityStore(":memory:")`, dueños y port UI de prueba. V2 Core CI `#42040913961` 10/10 verifica núcleo durable DEV y seguridad de Workspace/ACL/puntero. **Brecha nueva:** no existe prueba que siembre registros legales por `RealityStore.run { scope -> owner.upsert/recordEntry }` en la generación REAL ya inicializada por `FaroV5V2CoreDevAuthority(root).open()`, reabra **ese mismo host** y verifique tres resultados y navegación read-only.
- `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthorityTest.kt` tiene 10 tests exactos certificados. No alterarlos ni repetirlos por inercia. `FaroV5FederatedSearchSurfaceAdapterTest` ya verifica 3 owners en fixture de unidad: reutilizar el patrón, no editarlo.
- Criterio binario **mínimo complementario**: nuevo test con raíz temporal aislada; bootstrap real de 5 stores y owner Reality v6; escribir tres documentos mediante contratos oficiales de transacción; cerrar/abrir sesión; `session.searchReadPort.search()` trae solo 3 namespaces correctos; `openAuthorized` entrega solo detalle read-only, sin monto/IDs; no coincidencias no inventan hits; stale grants antes/después de reiniciar no navegan. CI XML exacto 1/1 o FAIL. El resultado es **G5_AUTOMATED_SUPPLEMENT**, no prueba física.
 
## B2 COMPARACIÓN
- V4.4 y el Chat V5 preservan conceptos de búsqueda y navegación, pero V4.4 no ejecutaba la federación DEV de 4 owners con `RealityStore` v6; no transplantar sus datos ni permisos. El patrón de seguridad existente V5 mantiene `SearchHit` como localizador, nunca autorización.
- G4 ya tiene `FaroV5FederatedSearchSurfaceAdapterTest` que cubre cuatro owners con `Workspace` y `RealityStore` en memoria, revocación de ACL, puntero y resultados tipados. La nueva unidad comprueba **integración transaccional del host real y recuperación durable**, no los contratos individuales duplicados.

## B3 INVESTIGACIÓN DE ALTERNATIVAS
- Alternativa A (ELEGIDA): reutilizar `FaroV5V2CoreDevAuthority` y su suite JVM dirigida existente, sembrando **sólo en un directorio @TempDir de prueba** con `RealityStore.run` y puertos originales, con test separado y conteo XML independiente. Sin nuevas librerías ni permisos, carga CI mínima.
- Alternativa B (REFERENCIA PARA FASE FÍSICA): emulador Android de GitHub Actions usando `reactivecircus/android-emulator-runner@v2` (https://github.com/ReactiveCircus/android-emulator-runner), que inicia un Android real y corre `connectedCheck`; implica instrumentación Android y configuración de variante, no confundir un JVM PASS con un Android PASS. **No se ejecuta en esta unidad** para evitar ampliar alcance sin necesidad; queda como opción para remanente Android.
- Alternativa C (DESCARTADA): insertar fixtures directamente en SQLite del teléfono o modificar el puntero activo, rompería autoridad de Reality, firma de datos y aislamiento. Alternativa D (DESCARTADA): exponer botón de semilla de producción/DEV en interfaz habitual solo para forzar un cierre. Alternativa E (DESCARTADA): tratar búsqueda vacía como prueba positiva.

## B4 UNIFICACIÓN
- Cambios funcionales únicos y acotados: 
  1. **Nuevo** `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5G5ActiveFourOwnerSearchTest.kt` con una prueba complementaria sobre `@TempDir`, sin modificar runtime ni suites anteriores.
  2. `.github/workflows/faro-v5-v2-core-directed.yml`: conservar 10/10 exactos y añadir ejecución dirigida del nuevo test con comprobación XML exacta 1/1. Nunca alterar el umbral de las 10 pruebas aprobadas.
- La CI V2 Core existente se dispara por el nuevo archivo de prueba y el workflow. Las suites 10/10, T4 7/7 y aislamiento 4/4 deben seguir pasando.
- Ausencia de egress y permisos shipping, V2 productiva OFF, main/S2/V4.4 intactos. El número de pruebas real separado de 10/10.

## B5 AUDITORÍA ADVERSARIAL
- **No datos de usuario:** únicamente `@TempDir` JVM. El seed se hace con `reality.run(TransactionId)` y `RealityTransactionScope` válido, jamás con SQL directo.
- **Sin dos escritores:** cerrar completamente la sesión y grafo DEV antes de abrir `RealityStore` para insertar test, cerrar Reality antes de reabrir host. No alterar puntero, journal ni root real.
- **No autorización falsa:** resultados solo si ACL/grants DEV vivos tras abrir host y revalidación por propietario. Verificar apertura read-only de tres targets, one-shot, sin cantidades, IDs ni saldos expuestos. Verificar que hit de otra sesión sea rechazado.
- **Sin falso PASS:** CI debe ejecutar la nueva clase y leer su XML (1 caso, 0 fallos, 0 errores, 0 skipped), no aceptar sólo compile verde; si falla, unidad RED_FIX y G5 OPEN.
- **Límites:** JVM PASS no convierte pruebas físicas pendientes en certificadas. Emulador o Moto G15 con registros canónicos reales y navegación física G5 siguen sin verificarse. El backup FBS1/FBS2 de nuevos owners no está resuelto y permanece fail closed.
- Dictamen B5: **READY_BOUNDED_TEST_ONLY** para dos rutas exactas. Writer Git CAS único. Tras B6 ejecutar B7, guardar evidencia, liberar writer; no anunciar G5 completo por este suplemento.
