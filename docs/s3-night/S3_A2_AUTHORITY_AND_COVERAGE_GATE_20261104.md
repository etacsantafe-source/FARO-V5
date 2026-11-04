# FARO V5 — S3.A2: bloqueo de autoridad y cobertura, cierre híbrido · 04/11/2026

## Alcance y evidencia actual

- Fuente de verdad: `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`, baseline `5d50381c40ece371ce8f594d1f8327478413ad18`. S2 CLOSED 8/8; S3 abierto; S4, V2 de app, Auto y Noche OFF. Cola CLOUD STAGED. Catálogo 172 intacto.
- CI dirigida ya comprobada (NO repetir): `#41827910601` (búsqueda y revocación por publicación, 17/17), `#41828553243` (UI 50/50) y `#41828757767` (Avisos active owner + búsqueda, 21/21). Son JVM/builds dirigidos, **no end-to-end Android**.
- Contraste READ_ONLY con V4.4 `app-v4/src/main/kotlin/ar/com/faro/v4/conversation/FaroV4GlobalSearch.kt` (fuente `44f1e6e...`): cuatro dominios canónicos independientes, AND de términos normalizados, diversidad y degradación por propietario. La federación V5 ya implementó esquema tipado, pero NO equivale a cuatro fuentes reales instaladas.

## Investigación de autoridad: bloqueo estructural confirmado por código

1. `FaroV5CompositionRoot.java`: el `DeliveryGraph` de V1 no crea `chatReadSession` ni `chatSelectionSession` si `stores.getWorkspace()==null`. El único Workspace V2 depende de una activación que permanece deshabilitada (`FaroV5HostCertification.V2_ACTIVE_ENABLED=false`). No instalar un grafo staged como autoridad activa.
2. `FaroV5WorkspaceSearchReadSource.kt`: exige `currentGenerationId`, `aclEpoch` y `admitted`; sus tests inyectan epochs de laboratorio, no demuestran un emisor ACL de producción. La función de selección valida stamp completo y publicación fresca: mantener esos PASS, sin inventar grant/epoch.
3. `FaroV5BootstrapActivity.java`: `FaroV5SurfaceHost.install(..., null, ...)` para Search, explícitamente por ausencia de ACL independiente de Workspace. La búsqueda de títulos no está disponible en shipping.
4. **Hueco funcional adicional:** el mismo `FaroV5WorkspaceSearchReadSource.readAuthorized()` publica documentos `CONVERSATION` con título y `body=""`. El código actual no busca dentro de mensajes de conversación; por lo tanto **búsqueda global/in-chat completa no está certificada**, aun con el ACL resuelto.
5. `FaroV5HomeReadinessInvalidations.kt` sí observa readiness, commit Auxiliary y puntero activo; su propia documentación excluye revocación ACL independiente. No reciclar esa señal como una autorización ACL ficticia.

## Gates de cierre de A2 (en orden, no marcar PASS anticipadamente)

- **A2-G1:** identificar y conectar dueño real de autorización de lectura Workspace. Debe emitir epoch monotónico en revocación/re-grant y señal observable independiente; nunca usar un valor fijo. Probar que permisos/generación viejos invalidan página y selección, incluso entre instancias/cambios de puntero.
- **A2-G2:** con dueño + generación + readiness + selección genuinos, conectar `FaroV5SearchReadPort` en el host activo únicamente si dicho host está autorizado; en V1 sin Workspace permanecer UNAVAILABLE. No activar V2 para probar la UI.
- **A2-G3:** cubrir contenido de mensajes y contexto in-chat por lecturas canónicas acotadas, evitando copiar drafts o textos no autorizados al índice; añadir pruebas de coincidencia española, archivo, truncamiento, selección y restauración.
- **A2-G4:** enlazar otras fuentes canónicas reales (finanzas, objetivos, asuntos), con cobertura/fallo por propietario, without inventing empty COMPLETE; verificar recursos y degradación offline.
- **A2-G5:** correr CI dirigida con SHA exacto y XML y luego X01–X12; una sola campaña física Android al finalizar S3.

## Regla operativa híbrida acordada por el usuario

Una unidad funcional prioritaria hasta cerrar **o** encontrar dependencia real demostrada. Ante bloqueo externo/documentado, adelantar **solamente** trabajo aislado que destrabe esa unidad (por ejemplo G3 y su test) y retornar cuando G1/G2 esté disponible. No dispersarse en A1/A3/A4 por inercia; los PASS existentes se reutilizan y no se reauditan por rutina.

Secuencia de cada unidad: recuperar HEAD+CAS+último CI → investigar y comparar V4.4 READ_ONLY → detectar huecos y amenazas → cambio mínimo seguro → pruebas dirigidas → evidencia SHA/XML → checkpoint → liberar writer. Sin Git Actions manuales o activaciones fuera del alcance. El usuario continuará con la frase `Continuar FARO V5`.

## Salida de este bloque

Este archivo es un dictamen técnico y orden de trabajo, **no** implementación de ACL ni cierre de S3.A2. No se tocó V4.4, main, catálogos, esquemas, FBS ni datos físicos; no se lanzó CI por documentación. Tiempo efectivo de desarrollo: NO_MEDIDO. Próxima unidad ejecutable: pruebas+implementación aislada de búsqueda de texto canónico G3, conservando Search shipping nulo, mientras se resuelve G1.
