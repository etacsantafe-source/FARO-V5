# S3.A2 DEV Search — B7 auditoría final del primer código: RED_FIX (04/11/2026)

## B7 AUDITORÍA FINAL
**Unidad original:** `S3_A2_DEV_SEARCH_SESSION_OWNER`.
**Commit auditado:** `8c01f4c4028a4b2f311e31031924fa194cb3303c`; permiso B1–B5 parent `be845434ce6b8c848ded554833e5f21859e7e6f8`.
**Dictamen: RED_FIX / NO DONE.** Motivo: vulnerabilidad de antigüedad de sesión tras salto de generación **A→B→A**, sin listener Search activo.

### CI verde exacta, conservada sin repetir por inercia
- Gate protocolo `#41932138079` **SUCCESS** SHA `8c01f4c4`.
- V2 Core DEV directed `#41932138084` **SUCCESS**: XML `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 9/9`; M2 debug T4 7/7; manifest/no-permisos `V2_CORE_M2B_DEV_APP_ISOLATED_PASS 4/4`; M1 aislamiento 4/4.
- S3 Home notice `#41932137991` **SUCCESS**: Home 9/9, Workspace Search 7/7, Search Bridge 5/5, Avisos 4/4 (25/25).
- APK DEV `#41932138014` **SUCCESS**: binario `ar.com.faro.alpha.v2coredev`, launcher exclusivo, **cero permisos solicitados**, firma APK scheme v2; SHA256 `0b05dacd9011edac3d0ced3d9f3c8d0252101139b40396d1e2b29ee9d9195618`; 62,147,763 bytes. La firma de actualización física y la instalación Android de este APK NO fueron verificadas, no instalar.

### Fallo adversarial de diseño pendiente (sin falsificar prueba fallida)
1. Buscar una conversación en sesión DEV y publicar hit con `generationId=A, aclEpoch=UUID-1, revision=R`.
2. No recolectar `searchReadPort.invalidations` (sin observador de UI). Cambiar puntero duradero A→B y después B→A con CAS permitido; la secuencia durable aumenta.
3. Código de `FaroV5V2CoreDevAuthority.ready` solo comprueba **ID A**, journal, replay fence y `alive`: vuelve a true al regresar A. `SearchReadAuthority.invalidate()` solo aumenta versión StateFlow; `aclEpoch=UUID-1` continúa válido. El adaptador conserva la publicación del hit si nadie consumió invalidaciones.
4. `openAuthorized(oldHit)` podría CONFIRMAR un locator anterior al switch aunque su secuencia del puntero ya sea otra. **No está cubierto por 9/9 y no se certifica.** Este es un análisis estático de carrera; no afirmar que se ejecutó ni que un CI falló.
5. Correctivo mínimo: capturar la **secuencia de puntero antes de abrir el graph** y exigirla en cada `ready`; solo el host recién abierto recibe un nuevo grant. Test negativo explícito A→B→A **sin subscriber** y old hit UNAVAILABLE. No cambiar FBS2, schema, shipping, OS ni usar epoch fijo. Código solo en variante DEV, nuevo test y oráculo XML 10/10.

### Estado y protocolo
- Mismo `unit_id` para RED_FIX. Nueva B1–B5 documental y permiso READY independiente son obligatorios antes del segundo commit funcional, aun siendo un fix.
- B7 original `RED_FIX`, **no PASS**, pese a todos los CI verdes; al verificar fix y nueva CI exacta reauditar B7 sobre su SHA.
- S3.A2-G1 ACL productivo OPEN (0/5), S3≈40% desarrollo estimado / 0/5 formal, S2 8/8, V2 productiva/Auto/Noche OFF.
