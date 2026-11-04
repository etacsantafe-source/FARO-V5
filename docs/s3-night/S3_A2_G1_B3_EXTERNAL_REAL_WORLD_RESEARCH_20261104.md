# FARO V5 — S3.A2-G1 · B3 investigación externa verificable · 04/11/2026

**Unidad:** S3_A2_G1_ACL_OWNER · **Base inspeccionada:** `f976074280c4f9a4619902bdd6cee9af83d8230a` · **Clase:** DOCUMENTACIÓN / SIN IMPLEMENTACIÓN / V2 PRODUCTIVA OFF.

## Problema real y límites
FARO tiene búsqueda canónica de mensajes del Workspace, federación y revalidación por `SearchAuthorityStamp`, pero el `aclEpoch()` de los tests procede de callbacks sintéticos. Falta demostrar un **propietario real de concesión, revocación y sesión de lectura**. El Workspace tiene token de autoridad para escrituras; no debe tratarse como ACL de búsqueda. La activación aislada V2 DEV existe pero no equivale a permiso de producción.

## Soluciones externas investigadas — referencias y contraste

| Proyecto / fuente | Patrón o fallo real verificado | Decisión FARO |
| --- | --- | --- |
| OWASP Authorization Cheat Sheet (https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html) | Denegar por defecto; permiso mínimo; autorización en **cada acceso**. | **USAR:** el propietario real emite grant/revoke y se revalida antes/después de lectura y **antes de navegar**. |
| Android Guide to app architecture (https://developer.android.com/topic/architecture) | Single source of truth y separación UI/datos; componentes UI no son dueños persistentes. | **USAR:** authority del host/Workspace, nunca flag de Compose ni listener como fuente de verdad. |
| Android Room 3 multi-instance invalidation (https://developer.android.com/reference/androidx/room3/RoomDatabase.Builder) | `enableMultiInstanceInvalidation()` comunica tablas entre instancias, también entre procesos; solo si se habilita en ambas y comparten el archivo. | **ADAPTAR, NO CONFUNDIR:** evento secundario de refresh **si** el owner real usa Room y el mismo archivo; **no** crea permiso ACL, ni cubre cinco SQLite owners, ni generaciones diferentes. |
| SQLite `PRAGMA data_version` (https://www.sqlite.org/pragma.html#pragma_data_version) | Detecta commits desde otras conexiones, comparando lecturas sobre una misma conexión; no detecta los propios. | **REFERENCIA/NO USAR COMO ACL:** pista para invalidar caché, pero no revocación durable ni epoch de identidad. |
| Bitwarden Search your Vault (https://bitwarden.com/help/searching-vault/) | La búsqueda se acota al filtro/carpeta/colección abierta por el usuario. | **REFERENCIA UX:** distinguir alcance visible de resultados; un filtro de búsqueda **no** es por sí mismo autorización de acceso. |
| Element X Android PR #7877 (https://github.com/element-hq/element-x-android/pull/7877) | Corrección documentada: al revocar la sesión de **otra cuenta**, se cerraba la cuenta equivocada. | **ADAPTAR LA PRUEBA NEGATIVA:** revocar sesión A jamás invalida, concede ni selecciona sesión B; no asumir que replicamos su arquitectura. |
| Element X Android issue #7988 (https://github.com/element-hq/element-x-android/issues/7988) | Reporte 23/10/2026: ciertos resultados de búsqueda no pueden navegar al mensaje señalado. Reporte de error, no solución verificada. | **ADAPTAR LA PRUEBA NEGATIVA:** reabrir resultado con owner y generación actuales; sin locator vigente devolver UNAVAILABLE, nunca navegar a mensaje equivocado. |
| Signal Android Manifest (https://github.com/signalapp/Signal-Android/blob/main/app/src/main/AndroidManifest.xml) | Declara permiso Android de firma `ACCESS_SECRETS` para componentes. | **DESCARTAR PARA ESTA UNIDAD:** permiso del SO para frontera entre apps no equivale a ACL de lectura interna de una sesión. |

**B3 no copia código ni licencias.** Se reutiliza semántica comprobada; no importar librerías, dependencias, APIs pagas ni diseños ajenos por analogía.

## Alternativas decididas y preguntas falsables
1. **Descartar** `aclEpoch = "acl-1"`/hora/revisión Workspace/write-token/boolean de UI: no acredita identidad de concesión o revocación y admite ABA.
2. **Descartar** Room invalidation, polling y SQLite data_version como fuente de autorización: observan cambios **después**, pero no deciden quién puede leer.
3. **Adaptar** host-owned authority con `grantId/epoch` diferenciados por identidad y scope, revocación explícita, fail-closed de sesión, evento opcional y relectura tras suscripción; **solo si se comprueba primero que no existe ya owner real**.
4. **No acoplar** el ACL a persistencia FBS2 o al token Workspace sin auditar backup, restore, restart y ownership. Si no hay identidad/scope durable demostrable, **B5 permanece BLOQUEADO**.
5. Matriz de pruebas obligatoria: revocar en mitad de búsqueda, restaurar permiso con nuevo epoch, resultado previo a regrant, generación cambiada, dos cuentas/sesiones cruzadas, callback tardío, proceso reiniciado, sesión cerrada, restore, falla del owner y navegación stale. Probar negativos sin false COMPLETE ni fuga de índice.

## Procedimiento permanente de B3 para futuras unidades
Antes de cada B6: consultar **al menos un estándar/documentación mantenida y un ejemplo/fallo real externo cuando exista equivalencia material**; registrar enlaces concretos, versión/fecha, mecanismo real, limitaciones, licencia/dependencias/costos, amenaza y elección USAR/ADAPTAR/REFERENCIA/DESCARTAR. Si **no existe ejemplo comparable**, registrar búsqueda y evidencia de falta de candidato, no inventarlo. B5 debe revisar estos hallazgos y rechazar READY si la investigación es superficial; un título sin comparación no satisface B3.

## Resultado
**B3: EXTERNAL_EVIDENCE_RECORDED**, no B5 aprobado ni nueva autorización. `FARO_PROTOCOL_WORK_PERMIT.json` continúa BLOCKED; B6 NOT_RUN; B7 NOT_RUN; S3.A2-G1 0/5 gates, S3 estimado ~40%, formal 0/5. Próxima tarea: identificar la frontera real de sesión del host V2 DEV versus production, y resolver si el ACL adicional puede ser estrictamente efímero o requiere epoch persistente antes de diseñar un READY específico. Sin código ni cambio de comportamiento.
