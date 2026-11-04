# FARO V5 — S3.A2 búsqueda canónica y presentación · handoff 04/11/2026

## Estado verificado

- Rama `work/s3-night-20261102`. Inicio `22f41f650d87621b959b2c6a67bae0dc1df0653d`; código terminado `6b2ddcf4482bcd190d26cbc8ba6722ec90a44880`.
- Backend: `a65b56fa24dea3fa0c700c647400ae644ad4b298`, GitHub Actions **#41819489126 SUCCESS**, XML `S3_CANONICAL_SEARCH_XML_PASS 9/9`, verificado en log con SHA exacto.
- UI: `6b2ddcf4482bcd190d26cbc8ba6722ec90a44880`, GitHub Actions **#41820150581 SUCCESS**, XML `S3_SURFACE_XML_PASS 48/48`, verificado en log con SHA exacto. Incluye dos casos nuevos de política de resultados.
- Writer CAS de sesión código: RELEASED epoch 89; sesión UI: RELEASED epoch 91; sesión documental requiere RELEASED epoch 93. Cola CLOUD STAGED, activación false, Noche/Auto OFF.
- S3.A2 sigue **PARTIAL**, S2 CLOSED 8/8, S4 y V2 OFF; ninguna prueba física.

## Investigación, contraste V4.4 y resultado

Se comparó `etacsantafe-source/FARO:app-v4/src/main/kotlin/ar/com/faro/v4/conversation/FaroV4GlobalSearch.kt` (SHA histórico `44f1e6e...`, READ_ONLY). V4.4 permitía consultas multi-término y dominios; V5 ya tenía lectura canónica por Workspace, revisiones y autorización antes/después, búsqueda tolerante a tildes, índice descartable y degradación segura. Hueco nuevo acotado: títulos/cuerpo de V5 exigían antes la frase en orden. Se agregó AND de términos sin orden y normalización de signos en **contenido recién autorizado**, sin confiar en AppSearch, con test para tildes, puntuación, títulos y campos combinados.

La ruta `BUSCAR` ahora tiene pantalla de búsqueda, estados COMPLETE/PARTIAL/UNAVAILABLE explícitos, política que no confunde parcial con ausencia, aviso de índice degradado, invalidación del contenido visible y apertura de conversación **solo ante CONFIRMED** emitido por el propietario autorizado. La interfaz `FaroV5SearchReadPort` separa presentación de fuente y mutaciones; no añade datos ni operaciones al catálogo.

## Bloqueo real y siguiente unidad

`FaroV5WorkspaceSearchReadSource` requiere epoch ACL real y generación activa. En el composition root actual **no está demostrado un emisor ACL de revocación independiente** ni un binding completo de Search al host. Por eso `searchReadPort=null` en shipping y la pantalla muestra indisponibilidad, sin resultados fabricados. NO declarar S3.A2 PASS por tener interfaz y CI.

Siguiente bloque: investigar autoridad ACL efectiva del dueño de Workspace y eventos de revocación/generación (incluido el alcance proceso/instancia), comparar V4.4, y cerrar read admission y resolución/selección revalidada antes de exponer resultados. Luego federar fuentes con cobertura individual y probar Compose/Android. No usar un epoch ACL fijo ni un contador falso para eludir el bloqueo.

Independientes: N-031 no tiene persistencia de planificación/reminders/monthly alerts y requiere revisar S2 FBS/backup antes de schema; N-015 no tiene timestamp durable de COMMITTED ni historia federada. Guardados +8 sigue solo revisado, NO aprobado.

## Restricciones y métricas

Writer global CAS obligatorio; S3 manual, no tocar main/V4.4, no abrir S4 ni activar V2, sin API paga; no relanzar PASS sin nueva hipótesis. Pruebas físicas: NOT_RUN hasta campaña única final. Tiempos efectivos: NO_MEDIDOS (no inventar productividad). Termómetro del chat: mantener indicador cualitativo en cada respuesta sustantiva, no porcentaje de tokens inventado.
