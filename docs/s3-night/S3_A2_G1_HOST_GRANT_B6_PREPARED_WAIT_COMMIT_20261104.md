# FARO V5 — S3.A2-G1 host-scoped grant B6 PREPARED / NOT PUBLISHED

**Fecha:** 04/11/2026 AR. **Unidad:** `S3_A2_G1_HOST_SCOPED_SEARCH_GRANT`. **Estado:** `B1_B5_PASS; READY_PREFLIGHT_PASS; B6_CODE_OBJECTS_PREPARED_NOT_PUBLISHED; B7_NOT_RUN`.

## Estado remoto válido
- Rama `work/s3-night-20261102`, HEAD del permiso: `4ac4dc1e196acba70141196c4f5a56db06af2453`.
- B1–B5: `docs/s3-night/S3_A2_G1_HOST_SEARCH_GRANT_B1_B5_20261104.md`, commit `7b27f27eef4b9804e25ffd0495594d2c1ce0b00e`.
- Permiso de unidad acotada READY, commit `4ac4dc1e`. GitHub Actions `#41948833339`: `FARO_PROTOCOL_PREFLIGHT_PASS S3_A2_G1_HOST_SCOPED_SEARCH_GRANT`, guard SUCCESS.
- Código de shipping V2 sigue **OFF**. No se habilita UI Search ni se declara G1 cerrado.

## B6 — cuatro objetos Git preparados (NO integrados)
| Archivo | Git blob SHA |
| --- | --- |
| `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveWorkspaceSearchGrantOwner.kt` | `f9f7c5996b604ed0f99ea7b7190d97633a734f54` |
| `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java` | `31cec1dc78fd3bb0174c3c10e8b553bc6e4463f4` |
| `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveWorkspaceSearchGrantOwnerTest.kt` | `541ecbfdaa4c151992398290677a37274a1ffb0c` |
| `.github/workflows/faro-v5-s3-home-notice-directed.yml` | `c8f42a57730ea45985c2f819cc4806b486c20910` |

**Importante:** el SHA inicial de la clase `6ff498...` contenía una interpolación Kotlin equivocada y NO se debe usar. La corrección exacta es **`f9f7c599...`** con `val issued = "${scope}:${next}:${UUID.randomUUID()}"`. Los objetos Git no anclados podrían ser recolectados; si no están disponibles en el próximo turno, regenerarlos desde las decisiones auditadas, sin declarar que fueron publicados.

## Decisión técnica conservadora
- Owner real de sesión local del host activo: grant explícito, deny-by-default, token único por regrant, `StateFlow` de invalidaciones sólo como aviso; cada uso relee admisión y grant, sin reutilizar write token. Puntero durable completo ligado a active generation, revocación en evento y cierre del DeliveryGraph.
- Scope de integración: **interno al graph**, NO montado a BootstrapActivity o superficie productiva; UI Search sigue `null`.
- Cuatro tests JUnit dirigidos: no grant/close, scopes independientes y regrant, Search canónico real revocado, A→B→A sin observador. Workflow exige XML `S3_SEARCH_HOST_GRANT_XML_PASS 4/4`.
- **Limitaciones residuales:** no certifica cross-process, restore entre cuentas ni epoch ACL persistente. G1 formal 0/5 gates hasta comprobar las dependencias.
- **Criterio previo a certificar:** publicar los cuatro archivos **EN UN SOLO commit funcional** `FARO-UNIT:S3_A2_G1_HOST_SCOPED_SEARCH_GRANT` con writer CAS renovado; protocolo guard y CI dirigida con XML real; B7 separado. Evitar cuatro commits parciales porque el guardia exige B7 entre commits funcionales.

## Bloqueo real y siguiente acción
La herramienta GitHub permitió crear los blobs pero rechazó crear el árbol funcional con el control `Los controles de seguridad de OpenAI bloquearon esta llamada a la herramienta`. **No usar otro canal para eludir el control.** Dejar writer `RELEASED` y reportar explícitamente que B6 no se publicó. Próxima reanudación: comprobar HEAD/permiso/writer y si se admite publicar un único commit; si sigue bloqueado, trabajar sólo en tareas READ_ONLY de G1 y no inventar CI ni avance.

**Estado:** S2 8/8 CLOSED; S3 ≈40% desarrollo estimado, 0/5 etapas cerradas; S3.A2 0/5 gates, V2 productiva/S4/Auto/Noche OFF. Métrica de tiempo manual NO_MEDIDA.
