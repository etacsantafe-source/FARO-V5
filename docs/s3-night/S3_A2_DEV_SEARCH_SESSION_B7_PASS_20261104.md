# FARO V5 · S3.A2 — B7 PASS limitado a Buscar DEV aislado (04/11/2026)

## B7 AUDITORÍA FINAL
**Unidad:** `S3_A2_DEV_SEARCH_SESSION_OWNER`. **Código corregido verificado:** `c3f5e5d1238556988fe7cec7164641c1840d8e40`. **Permit B1–B5 aprobado en su padre:** `c1cf987c43bb5c567b0d5a583b1feb398595718c`. **B7:** **PASS SOLO EN DEV**.

### Cadena auditada, no inventar PASS anteriores
- Código inicial `8c01f4c4` pasó su CI, pero recibió **B7 RED_FIX** en `docs/s3-night/S3_A2_DEV_SEARCH_INITIAL_B7_REDFIX_20261104.md` por posible A→B→A stale pointer sin subscriber. No promover ese SHA como certificado.
- Fix `c3f5e5d1`: `FaroV5V2CoreDevAuthority.kt` toma el **sequence durable antes de abrir grafo** y lo exige en cada `ready`, adicionalmente a ID, journal, replay fence y alive. Test nuevo en `FaroV5V2CoreDevAuthorityTest.kt`: A→B→A por CAS con **cero observadores de invalidaciones**; no puede reautorizar old hit. Oráculo XML actualizado a 10/10. Diff GitHub contra su padre: **exactamente 3 rutas**, solo variante DEV, test y workflow. No cambió código de shipping ni schema/FBS2.
- La implementación inicial reutilizaba Workspace canónico de cinco owners y Search source/adapter existentes, integraba flujo de invalidación por sesión y selección real; su diff original contenía únicamente 5 rutas acotadas.

### CI exacta @ c3f5e5d1238556988fe7cec7164641c1840d8e40
| Comprobación | Run | Resultado |
| --- | --- | --- |
| Mandatory seven-block protocol guard | `41933426669` | **SUCCESS**, posterior al preflight `41933233191` SUCCESS, `FARO_PROTOCOL_PREFLIGHT_PASS S3_A2_DEV_SEARCH_SESSION_OWNER c1cf987c...` |
| V2 DEV core dirigido | `41933426446` | **SUCCESS**; log `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, T4 debug 7/7, DEV manifest 4/4, M1 isolation 4/4 |
| S3 Home notice regresión | `41933426482` | **SUCCESS**; log XML Home 9/9, Workspace Search 7/7, Search Bridge 5/5, Avisos 4/4 = **25/25** |
| APK DEV binario y firma | `41933426599` | **SUCCESS**; `M5_ASSEMBLE_V2_CORE_DEV_PASS`, `M5_APK_PACKAGENAME_PASS ar.com.faro.alpha.v2coredev`, launcher exclusivo, `M5_APK_BINARY_PERMISSIONS_DENIED_PASS`, `M5_APK_PACKAGE_SIGNATURE_PASS` |

APK SHA256 **`9be2c4d598ca0e53f6cf81641dd922ab7895a52ee6fa7c79c293b1cd62311ec0`**, tamaño **62,147,763 bytes**, firma APK v2 válida. Se verificó **cero permisos solicitados** en el binario. Este artefacto NO se instaló ni se publica sobre el paquete FARO habitual; su capacidad de actualizar una instalación previa depende de la identidad de firma y sigue sin prueba física.

### Negativas, exclusiones y salida
- **PASS probado:** búsqueda de título y cuerpo de mensaje aceptado en SQLite del Workspace activo DEV, selección por owner, denegación de hit luego de cerrar/reabrir, denegación al cambiar puntero y al volver a su ID anterior, invalidación observable local y no exportación de puertos de Apply/IA/OS.
- **No probado / no autorizado:** ACL independiente de producción, notificación **cross-process** de eventos, cualquier restore entre cuentas real, salto del Chat a la posición **exacta del mensaje**, navegación global multiowner (Finanzas/Objetivos/Asuntos), Android físico y nueva firma instalable. La política local busca denegar tras restart; el grant por sesión DEV no se persiste y no debe promoverse como epoch ACL de shipping.
- **S3.A2 G1–G5:** 0/5 cierres formales, incluyendo G1 (no confundir DEV con shipping). **S3 ≈40% desarrollado, 0/5 etapas cerradas**. S2 8/8 CLOSED; S4, V2 shipping, Auto y Noche OFF; catálogo 172 sin cambios.
- **Próximo:** bloquear permiso de código hasta nueva B1–B5 con investigación externa por unidad. Priorizar owner ACL verdadero del host activo con revocación durable e invalidación entre instancias, usando prueba de ABA y comparación con V4.4. No tocar datos del usuario, main ni APK física.
- **Tiempo efectivo humano:** NO MEDIDO. Tiempo CI no equivale a productividad de desarrollo.

**Decisión B7 final:** `PASS` del **módulo DEV aislado** en este SHA y alcance; formal S3.A2 continúa OPEN.
