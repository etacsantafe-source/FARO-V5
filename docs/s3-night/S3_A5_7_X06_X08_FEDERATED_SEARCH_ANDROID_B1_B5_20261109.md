# FARO V5 — S3.A5.7 X06/X08 Search federada Android — B1–B5 PREAUDIT_DOC_ONLY
Fecha 2026-11-09 AR. Unidad propuesta: S3_A5_7_X06_X08_ANDROID_SEARCH_UI. Baseline HEAD fa7e7589d14e2f6a80b3ced1530c5e202d9a4252. Este documento es solo preparación; la CI X01/X02 #42623474908 sigue en curso. **No permiso READY para código X06/X08 hasta B7 del anterior**.

## B1 — Evidencia y alcance
- Contrato congelado en docs/s3-night/S3_P00_CONTRACT_REVIEW_20261102.md: X06 búsqueda federada cuatro owners, X08 cobertura incompleta ≠ ausencia de resultados y no mostrar snippets revocados. Estado de matriz global X06/X08 OPEN.
- DEV FaroV5V2CoreDevAuthority.open() crea y monta un FaroV5FederatedSearchSurfaceAdapter con Workspace canónico, Finance, Objectives, Matters y merge de invalidations. Cada owner suministra epoch/authorityStamp y el adaptador exige readAuthorized y revisa epoch al abrir.
- FaroV5SearchSurfacePolicy distingue COMPLETE sin coincidencias (texto "No se encontraron resultados en las fuentes consultadas.") de PARTIAL sin hits ("Cobertura parcial: pueden existir más resultados.") y UNAVAILABLE ("Búsqueda no disponible. No se leyeron datos."). El surface puede navegar a Chat por confirmedMessageTarget o presentar detalle tipado read-only.
- X07/X11 owner revision 4/4 y seis A5 scoped 33/33 previamente certificados, sin repetir tests. X12 Compose Chat real 2/2 API35 certificado. X01/X02 Android nuevo commit 67524163 CI #42623474908 en ejecución; no asumir resultado.

## B2 — V4.4 y reutilización
- V4.4 solo pauta UX de búsqueda y navegación; no copiar estado ni indices históricos. La fuente de verdad es Workspace + tres owners Reality en DEV V5.
- Reusar FaroV5SearchScreen, FaroV5FederatedSearchSurfaceAdapter, SearchCanonicalReadPort y semántica oficial Compose, sin agregar OperationId ni modificar catálogo172.
- Buscar es destino secundario de drawer único. Un hit de Workspace no equivale a permiso: openAuthorized revalida canonical read + generation/ACL y devuelve destino solo de esa lectura. No usar hit como token permanente ni enviar contenido al modelo.

## B3 — Investigación y alternativas
- Android Compose testing https://developer.android.com/develop/ui/compose/testing: onNodeWithText, onAllNodes, performs y Accessibility semantics para pantalla real; USAR.
- Android testing interoperability https://developer.android.com/develop/ui/compose/testing/interoperability: click nativo inicial seguido de navegación Compose, ADAPTAR.
- AndroidX ActivityScenario https://developer.android.com/reference/androidx/test/core/app/ActivityScenario: recreación de instancia real sin reuso de permiso de Activity, USAR.
- Fuente primaria de política: FaroV5SearchSurfacePolicy.message() de repositorio, no suponer coverage COMPLETE si falta un owner. Alternativa A elegida: aceptación dirigida solo de UI en el emulador; alternativa B descartada: repetir owners JVM ya certificados. Alternativa C descartada: forzar cobertura partial con mocks colocados en producción, viola no-contaminación.

## B4 — Plan acotado y pendientes de viabilidad
- NUEVO test Android separado para (a) crear mensaje USER real por UI Compose Chat (sin IA), navegar Buscar desde drawer, buscar frase única; exigir hit canónico Workspace, pulsar hit y verificar Chat + foco en mensaje exacto sin duplicación ni otro conversationId. La pantalla de Search no debe usar un snippet revocado para abrir. Este caso cubre X06 **solo integración Workspace/UI**, NO garantiza que tres otros owners tengan datos.
- Segundo test Android con frase imposible: verificar estado final distingue COMPLETE sin hits de PARTIAL/UNAVAILABLE, nunca tratar cobertura parcial como "no se encontraron resultados". Sin un modo determinista de degradar owner sin código productivo, la rama PARTIAL no puede declararse ejecutada. Requiere otro diseño B4/B5 después de evidencia inicial.
- NO agregar test que llame directamente al SearchSurfacePolicy.message() ya certificado como sustituto de UI: eso sería repetición sin avance. No probar remoto/modelo/egress.
- Archivos potenciales: nueva clase test en g5AndroidTest y un workflow Android API35 exclusivo; **no crear esos archivos ni permiso READY en esta fase**.

## B5 — Ataques, puertas y no-completitud
- Hit sin revalidación, revocación de ACL por owner, generation/pointer ABA, cobertura parcial y apertura de destino equivocado son denegaciones duras, no textos cosméticos. No almacenar títulos/snippets en estado durable ni inferir resultado completo.
- La UI puede tener campos y botones "Buscar" duplicados entre TopAppBar y pantalla: elegir acciones semánticas del formulario con matcher y solo luego de que la raíz Compose real esté montada. Reusar patrón aprobado de X12; **no** reintroducir imports no existentes ni selectores Compose inventados.
- Si no puede garantizarse degradación de fuente en emulador mediante fixture legitimado y sin código productivo, reportar X08_PARTIAL_BRANCH_NOT_RUN y conservar evidencia JVM scoped, no reclamar PASS global.
- A3 Guardados X04/X05 no autorizado, 172 catálogo intacto; S4/V2 productiva/Auto/Noche OFF, G15 físico solo al final. CI y B7 X01/X02 deben cerrarse antes de comenzar código X06. S3.A5 global permanece OPEN.
