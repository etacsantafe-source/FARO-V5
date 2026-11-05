# FARO V5 — S3.A2 G4 · B1–B5 propietarios reales (05/11/2026 AR)

Unidad: S3_A2_G4_REAL_OWNER_FEDERATION. HEAD leído: 9777ee990c697b6033b55d3905d8a99ff1ebf751. Protocolo manual de siete bloques; este documento es análisis PRE-B6, no código ni certificación funcional.

## B1 EVIDENCIA
- G1/G2/G3 CLOSED B7; G3 código 4eb0f24a9cf6f686903259984db54db4aee58c4f; S3 dirigido 54/54 run 41962522467, Surface 59/59 run 41962522487, Core y APK DEV CI PASS; guard del HEAD 9777ee99 run 41963483215 SUCCESS. NO hay prueba física, y V2 productiva continúa OFF.
- v5/control/src/main/java/ar/com/faro/v5/control/search/FederatedCanonicalSearchCoordinator.java YA admite fuentes canónicas explícitas, máximo 8, cobertura por dominio COMPLETE/PARTIAL/UNAVAILABLE y revalidación posterior al recorrer otros dominios. No reconstruirlo.
- v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5WorkspaceSearchSurfaceAdapter.kt SOLO usa Workspace. Los hits no conceden ACL; la pantalla abre conversación y mensaje Workspace, no tipos ajenos.
- Finanzas tiene autoridad material REAL en RealityStore.financePersistence y FinanceSnapshotReadApi; snapshots de cuentas/asientos con revision. No consta en esta revisión un SearchCanonicalReadPort de Finanzas, autorización/invalidación independiente ni navegación legítima a un registro de Finanzas.
- Asuntos: v5/kernel/contracts/.../MatterAttentionReadProjection.kt declara explícitamente que MatterAttentionReadPort no crea owner, tabla ni operación. No hay fuente canónica de Asuntos vinculada.
- Objetivos: revisión del árbol Git remoto 0089c5dc884a020085d3c3214a56340cd0a35617 (1321 rutas, no truncado) no encontró archivos de goal/objective/objetiv; no demostrar propietario, snapshot ni modelo persistido. Ausencia de nombre no prueba imposibilidad futura, sí impide certificar un owner ahora.
- El permiso docs/v5/FARO_PROTOCOL_WORK_PERMIT.json está BLOCKED tras G3 B7. Writer liberado epoch 228 al leer; cola STAGED, Auto/Noche OFF.

## B2 COMPARACIÓN
- V4.4: comportamiento multidominio y avisos de cobertura según el backlog S3 y la auditoría del federador del 04/11; reutilizar la diversidad y el tratamiento de incompletitud, no trasladar autoridad/índice V4.4.
- V5 reutilizable: búsqueda canónica Workspace G3, SearchAuthorityStamp, federación tipada, limitación de 50 resultados y separación de Evidence/Reality. La falta de owner para un dominio NO equivale a lista vacía ni a permiso.
- Diferencia crítica: una conversación se selecciona mediante Workspace; un movimiento financiero, objetivo o asunto requiere navegación canónica propia y revalidación de su owner. No usar ConversationSelectionPort para registros ajenos.

## B3 INVESTIGACIÓN
- Nextcloud Unified Search: https://docs.nextcloud.com/server/stable/developer_manual/digging_deeper/search.html — proveedores registrados, búsqueda unificada con respuestas por fuente y resultados parciales. ADAPTAR patrón de fuentes explícitas; NO su red ni proveedor remoto (FARO LOCAL_ONLY). No se copia código ni se incorpora licencia/dependencia.
- Android Room, FTS y tokenización Unicode: https://developer.android.com/training/data-storage/room/defining-data — FTS3/4/5 para datos indexables. DESCARTAR índice FTS nuevo en G4: requiere duplicar datos sensibles/migrar y no resuelve ACL ni owner inexistente.
- Android privacidad y minimización: https://developer.android.com/games/develop/permissions — comprobar alcance y acceso por acción, no asumir una autorización en otra fuente. REFERENCIA defensiva de privacidad, NO confundir permiso Android OS con ACL interno.
- Alternativa A: adaptadores de lectura respaldados por CADA owner real y registro explícito en el federador existente. ADAPTAR: id opaco + generación/revisión/ACL, sin mutación ni índice obligatorio. Alternativa B: búsqueda global vía Workspace, stubs vacíos o ACL copiado de Chat. DESCARTAR: falsa cobertura y fuga entre dominios. Alternativa C: añadir almacenes/owners nuevos sin contrato previo para Objetivos/Asuntos. DESCARTAR para esta unidad sin aprobación/modelos/receipts.
- Coste de la opción A: sin APIs pagas ni nuevas librerías; coste de ejecutar consulta canónica acotada por fuente, pruebas de latencia. Nextcloud es referencia de arquitectura, no dependencia ni trasplante licenciado.

## B4 UNIFICACIÓN
- Contrato G4 de cierre: tres fuentes reales Finanzas/Objetivos/Asuntos con identidad opaca, generación activa, ACL propio, revision, lectura acotada, invalidación genuina y navegación específica; federación conserva fuentes no disponibles y no presenta COMPLETE si falta una fuente esperada.
- Secuencia mínima: (1) Finanzas read-only y test owner/revision/revoke con Reality; (2) diseño y existencia real del propietario de Asuntos sin reutilizar proyección N-031 como autoridad; (3) owner real de Objetivos; (4) registrar solo fuentes autorizadas en composición DEV sin encender V2 shipping; (5) selección por tipo revalidada; (6) CI exacta / B7; luego G5 campaña física única.
- Prohibido fabricar objetivos o asuntos, persistir hit como permiso, mezclar datos financieros con Workspace, abrir S4, expandir catálogo 172, tocar main/V4.4 o activar V2 productiva. G1/G2/G3 no se repiten.

## B5 AUDITORÍA
- Negativas obligatorias: owner no instalado o cerrado; generación/revisión/ACL diferentes entre lectura y publicación; revocación de Finanzas mientras responde Asuntos; 0 resultados con alguna fuente ausente => PARTIAL/UNAVAILABLE, nunca ausencia global definitiva; resultados cross-namespace y selección stale; restore/recovery/ABA; información de cuenta publicada fuera de autorización; límites de scans; lifecycle Android y reinicio en G5.
- No se demostró fuente real de Objetivos ni de Asuntos, ni navegación financiera con ACL propia. Por lo tanto **B5 RECHAZA READY PARA G4 COMPLETO**. No emitir permiso funcional ni alterar G4 a CLOSED por CI del federador existente.
- Un subobjetivo independiente G4-FINANCE podría recibir B1–B5 específico y permiso acotado solamente si se acredita ACL owner auténtico, path-set exacto y tests; no extender este análisis como autoaprobación.
- Estado veraz: G4 BLOCKED_DEPENDENCIES_DISCOVERED, B1–B5 documentados como auditoría previa, B6 NOT_RUN, B7 NOT_RUN. G1/G2/G3 CLOSED (3/5 A2 = 60%). S3 formal 0/5, desarrollo estimado ~40% (35–45%), S2 8/8, G5 pendiente.

## Siguiente acción concreta
Primero proveer fuentes canónicas reales para Asuntos y Objetivos en el diseño V2, y definir read ACL/navegación de Finanzas con garantías de revocación. Tras eso, documentar subunidades ejecutables, permisos limitados y CI. No declarar G4 finalizado antes.
