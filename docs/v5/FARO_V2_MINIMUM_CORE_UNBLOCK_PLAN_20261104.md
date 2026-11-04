# FARO V5 — DECISIÓN DURABLE: desbloquear V2 mínima para retomar la producción de funciones
**Fecha:** 04/11/2026 (Argentina). **Decisión humana vigente** tras comparación de mantener V2 OFF, activación general y V2 aislada.
**Estado:** OBJETIVO/ESTRATEGIA APROBADOS; V2 NO ACTIVADA; SIN CAMBIOS DE CÓDIGO EN ESTE CHECKPOINT.
**Fuente:** `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`, baseline `63d516c23d0a26c2d1684275d042ad393616ecf9`.

## Objetivo único — prevalece sobre el viejo orden de cierres
**DESBLOQUEAR UNA V2 MÍNIMA, INTEGRADA Y SEGURA DE DESARROLLO** para recuperar productividad de desarrollo, y recién después retomar los cierres completos S3 y S4. Se autoriza investigar y desarrollar *solamente* las dependencias demostrables de S3/S4 necesarias para que el núcleo V2 funcione, conservando las protecciones. El orden de prioridad pasa a **V2_MIN_CORE → INTEGRACIÓN_CHAT_WORKSPACE → CIERRE_S3 → CIERRE_S4 → PROMOCIÓN_PRODUCTIVA_V2**. No inferir permiso de despliegue ni alterar la app habitual.

**No crear un nuevo círculo:** no exigir S3/S4 terminadas para permitir V2 aislada de desarrollo; tampoco llamar V2 productiva a una prueba en cuarentena. Cerrar gates funcionales **después** de integrar el núcleo. No cambiar numeradores ni porcentajes por esta decisión.

## Causa raíz confirmada en código
- `FaroV5GenerationStoreGraph.open` mantiene V1 de **4 SQLite** y `workspace=null`. Workspace es el quinto owner de `WORKSPACE_V2`.
- `FaroV5HostCertification.V2_ACTIVE_ENABLED=false`; `FaroV5ActiveGenerationGraphOwner.open` bloquea apertura activa de V2 sin certificación. `FaroV5PreSwitchHostAdmission` tampoco autoriza promoción V2 por simple existencia de staging.
- `FaroV5CompositionRoot.deliveryGraph` entrega Chat read/selection null cuando no hay Workspace; `FaroV5BootstrapActivity.showShellSurface` conecta Search null. Añadir exclusivamente una ACL no resuelve la falta de owner activo.
- `FileActiveGenerationPointer` tiene secuencia durable y aviso de cambios; `FaroV5StartupReadinessGate` tiene señales reales de recuperación; `workspace_authority.token` protege mutaciones, **NO** autorización de lectura ACL independiente.
- S2 8/8 cerrado, con ensayo físico integrado **aislado** y alcance de entrega documentado; *esa certificación no equivale a permiso automático de activación productiva de V2*. Reutilizar sus resultados, no repetir SAF/Groq/Apply por inercia.

## Núcleo mínimo, en orden, con puertas de salida
### M0 — Inventario preciso de dependencias, no auditoría extensa
En una sola pasada READ_ONLY verificar hooks y contrato de admisión activa, cinco stores, pointer/journal/fence, recuperación, Chat, Search, e identificar **si hay alguna dependencia S4 bloqueante real**. Registrar cada dependencia con ruta y prueba que falta. Si no existe dependencia concreta S4, no desarrollar S4 todavía.

### M1 — Separación física y de autoridad
V2 solo en **app/build de desarrollo aislado**, application ID y storage root distintos de la instalación habitual; sin acceso a sus SQLite, claves, receipts o proveedores. Rama de trabajo aislada. No escribir main/V4.4. No mudar ni promover el puntero activo de la app habitual. Arranque del núcleo con acciones externas, Apply, IA remota y dispatch OS deshabilitados por defecto.

### M2 — Admisión real de cinco owners (solo desarrollo aislado)
Reutilizar `FaroV5GenerationStoreGraph.openActiveV2WithSharedGate`, `FaroV5ActiveGenerationGraphOwner`, el gate único y el journal existente. Crear el **camino mínimo de activación explícita limitado al host aislado**; no alterar `V2_ACTIVE_ENABLED` global de producción a `true`. Verificar que V1 habitual rechace V2; ningún descriptor/marker ni test dado habilita promoción sin owner y revalidación. No construir cinco stores desde cero si ya existe stage verificable.

### M3 — Chat/Workspace integrado y permiso de lectura legítimo
Montar lectura, timeline, selección y composer SOLO si generación activa, readiness y owner Workspace son reales. Search debe continuar UNAVAILABLE hasta tener autorización legítima y revalidación de páginas/selección.
**Evitar ACL ficticia:** decidir explícitamente si FARO local monousuario necesita un estado de revocación/regrant independiente. Si existe requerimiento real, incorporar owner durable epoch monotónico + invalidaciones después del commit, y ensayos interinstancia; si no existe, revisar G1 formalmente contra fences reales existentes antes de modificar contrato (no renombrar token de escritura como ACL).

### M4 — Safety gates sin abrir todo por un switch
La habilitación del quinto owner no debe conceder automáticamente autorización de Groq/IA, Review Apply, Delivery ni ejecución OS. Separar capacidad de lectura Chat, escritura local controlada, ejecución de modelo, mutación Reality, salida externa. No ejecutar plan ni tocar datos materiales de V1 por desbloquear V2 de desarrollo. Preservar AtomicApply, PREPARED/COMMITTED, receipts, idempotencia, Undo, recovery y separación Evidence/Reality.

### M5 — Pruebas de admisión e integración (no prueba física por cada bloque)
CI exacto por SHA con XML para: separación de perfiles y raíces, rechazo V2 productiva, apertura cinco stores sanos, denegación ante faltantes o WAL corrupto, pointer/recovery/rollback, escritor único, restart y cross-instance stale, navegación Chat, invalidación Search, no Apply/delivery sin permiso. Una sola campaña física consolidada cuando corresponda; nunca declarar funciones de producción certificadas por la sola JVM.

### M6 — Reanudar cierres
Con M1–M5 PASS suficiente, usar el núcleo activo **de desarrollo** para completar S3.A1–A5 (incl. Buscar, Guardados bajo 172 catálogo hasta decisión separada, Actividad/Avisos, integración). S4 se incorpora solo en las dependencias indispensables demostradas y se cierra después. La activación de producción requerirá decisión humana separada, paquete/upgrade/rollback y evidencia final en el dispositivo.

## Criterio inequívoco de éxito de este objetivo
- `V2_DEV_CORE_READY` SOLO si el build aislado abre el verdadero Workspace de cinco owners, Chat lee/selecciona/escribe localmente bajo gate, reinicia y recupera con integridad probada y negación de efectos externos no autorizados. **No significa V2 en producción.**
- Un bloqueo real se documenta con condición de salida concreta; no circular de S3 a S4 salvo dependencia demostrada.
- Al conseguir `V2_DEV_CORE_READY`, **priorizar cierre S3** sobre nuevas investigaciones o mejoras periféricas. Después cerrar S4. No abrir campañas de certificación duplicadas ni incrementar el catálogo.
- Si alguno de los límites de seguridad exige una decisión humana nueva, detener solamente esa acción sensible, continuar con componentes independientes dentro del alcance manual aprobado.

## Estado heredado (no modificar por planificación)
- S2 CLOSED 8/8 (100%); S3 ≈40% desarrollado (heurístico 35–45%) y 0/5 formal; A2 G1–G5 0/5 cerrados, G3 parcial; S4 sin abrir formalmente.
- Pruebas ya PASS: búsqueda→Chat `abca920c`, Actions #41853477492 **59/59**; prefijo canónico `f64699e5`, Actions #41854013498 **25/25**. NO repetir sin cambios pertinentes.
- Producción V2 **OFF**; FARO Noche y Auto **OFF**, CLOUD STAGED, writer único CAS, catálogo 172 intacto, sin APIs pagas; no ISO/IRAM.
- El pedido `Continuar FARO V5` recupera este objetivo y prosigue **desarrollo manual autorizado en alcance V2 mínimo**, sin habilitar automáticamente V2 productiva, Auto/Noche ni pagos. Termómetro cualitativo siempre, y S3 porcentajes estimado + formal cada turno.
- Próxima tarea ejecutable: **M0 en una pasada precisa → elegir M1/M2 mínimo seguro → implementar/test dirigido → checkpoint CAS → seguir**. No volver a abordar G3 periférico antes de despejar M2.

**Writer:** epoch 135, RELEASED al finalizar este registro. **CI:** ninguna nueva; solo documentación y puntero.
