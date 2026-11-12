# A5 offline — revalidación acotada y READY independiente
Unit `S3_A5_OFFLINE_REVIEW_APPLY_ACTIVITY_SINGLE_ROOT`. HEAD revisado `99958e361f7dcccb86f4b46d1cc3a209a0ad2dc6`, IME B7 PASS2/2, guard cierre43304231252 SUCCESS; writer anterior RELEASED839. Sólo documentación en este commit.

## B1
Las cinco rutas del paquete no presentan diff desde c5869b860b391b2974e6b5d7021a0449c2b19b0d; auditoría original blob1179d41421309b96bf897cefd0fe3d28c0abf047 y paquete10510c4ac0eaa2fe1c047c9b36528de7da0df419. Delta ajeno: manifest/insets/test IME ya B7. Leídos host X03, executor Apply, test X10, History binding y federator. No repetir suites X01/X09/X10.

## B2
Reutilizar Activity y canonical Workspace reviews, Apply session y receipt lookup actuales. La raíz X03 es staged/no activa; History binding exige ActiveGenerationPointer genuino. NO crear un ActiveGenerationPointer ficticio ni cambiar/null-bypass en main binding. Reutilizar consulta canónica timedReceiptHistory y readHistoryPage, DTO/política Surface. V4.4/S2 preservadas; snapshot del root S2 preexistente en tests.

## B3
Vigentes alternativas de auditoría original (Android single-source/lifecycle/Compose, SQLite WAL). A no aplica porque este host carece de ActiveGenerationPointer real. Elegir B explícitamente preauditada: seam debug-only por sesión staged, con generación/directorio/descriptor y archivos físicos reales verificados; dos grants de lectura independientes y revocables de principal TÉCNICO debug, no autenticación humana ni autoridad productiva. No modelos ni fixtures de recibos. No modificar API de binding main.

## B4
Actividad debug agrega apertura offline sin credenciales/proveedor, con egress DENY y sin exponer assistant dispatch. Ninguna propuesta de test reside en debug/main: únicamente PreparedPlan sembrado por test APK en Workspace real. Apertura Chat offline normal NO concede lectura; apertura separada Chat+Actividad offline mediante botón foreground concede sólo las dos fuentes de Actividad de la sesión vigente. onPause/onDestroy revocan antes de cerrar SQLite; volver sin gesto no restaura. Un recibo COMMITTED real conserva transactionId/fingerprint/commit time del mismo Reality que Apply. Reader no inventa eventos, punteros ni autoridad. Epoch de sesión + descriptor y file identities reales fijan raíz, no un active pointer. Revalidar antes/después de cada lectura y no publicar tras revocación. UI sólo tipo/procedencia/hora, sin IDs ni contenido privado.

Implementar sólo Activity debug, bridge debug nuevo, test Android nuevo y workflow nuevo de las cinco rutas candidatas; main History binding permanece sin cambios. Cuatro casos Android nuevos mínimos: recorrido Review→approve(no write)→Apply→receipt→Actividad autorizada; doble click/reopen con exactamente un receipt; grant separado/pause/retorno sin restauración; denegación/identidad y unavailable≠cero. Red offline y estado Groq sin habilitar. CI exacta API35/XML≥4/0/0/0/APK/manifest/guard, B7 scoped.

## B5
Fail closed si identidad/descriptor/archivo/generación/grant/foreground no son verificables; null antes de leer owners sin grant. No cross-UID/copy DB ni timestamp/receipt falso. No extra permisos, Internet/credenciales ni Apply productivo; S4/Auto/Noche OFF. El host debug existente conserva su ruta remota previa sin invocarla en este flujo; la nueva apertura offline no crea provider ni exige Free/secret. No cambiar contratos main ni ampliar estas rutas. Si los cuatro archivos no bastan, NEEDS_PREP con delta exacto, no bypass. B7 IME y permiso consumido se conservan; READY individual A5 requiere preflight de TODAS las cinco rutas antes de código.
