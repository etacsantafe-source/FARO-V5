# FARO V5 — Conciliación de cierre S3.A1 (Inicio) · 05/11/2026

## Dictamen y autoridad de alcance
**S3.A1 = CLOSED_AUTOMATED_SCOPE_UI_SHELL_ONLY** conforme al backlog original `docs/s3-night/S3_BACKLOG_20261102.md`: «pantalla Inicio cuatro indicadores, Chat-first, drawer único, sin navegación paralela, accessibility; test de estado/navegación, rollback». Este dictamen significa **cierre automatizado de la pantalla y la navegación de Inicio, NO entrega funcional de todos los datos de las cuatro tarjetas**. S3 global y V2 productiva siguen OPEN/OFF. El cierre de A1 NO declara cerradas A3/A4/A5.

Este registro es **reconciliación documental** con B7 previas, no nuevo commit funcional ni aprobación de ampliar contratos.

## Matriz de aceptación y evidencia existente
| Criterio A1 original | Estado y prueba |
|---|---|
| Chat-first + Inicio en drawer único | PASS JVM. `FaroV5ShellNavigationContractTest.rootsAreExactOrderedAndChatFirst` y `drawerKeepsSettingsAsRootButPlacesItInFooter`. |
| Sin navegación paralela; secundaria vuelve a raíz | PASS JVM. `rootNavigationReplacesRatherThanStacksRoots`, `secondaryNavigationPreservesOneRootAndReplacesPriorSecondary`, `secondaryBackReturnsToRootAndRootBackIsStable`. |
| Restore/rollback de estado de navegación | PASS JVM. `savedPresentationStateRestoresValidContextAndFailsClosedWhenMalformed`, `malformedSecondaryOnlyStackFailsClosedToChatRoot`. No ensayo físico ni restore de datos. |
| Inicio con exactamente cuatro indicadores | PASS contrato UI. `FaroV5HomeIndicatorsContractTest.exactlyFourApprovedIndicatorsWithCanonicalNavigation`; Pendientes, Avisos, Actividad reciente y Guardados. |
| Accesibilidad mínima | PASS revisión estática y JVM de anuncios: encabezado, rol de botón, tamaño mínimo 48 dp, semántica combinada y lectura explícita de «No disponible» frente a cero. **Compose/Android instrumentado no ejecutado**. |
| Conteos con procedencia y autoridad | PASS **solo comportamiento fail-closed y N032 Avisos admitido**, no cuatro fuentes vivas. `FaroV5HomeReadCoordinatorTest`, `FaroV5HomeInvalidationTest`, `FaroV5HomeNoticeBindingFactoryTest`. Revocación y cambios durante lectura niegan datos viejos. |

CI exacta reutilizada, no repetida: [Surface 61/61 #42094317827](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42094317827); [Avisos N032 11/11 #42103685170](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42103685170); [Generation 17/17 y V2 DEV 11/11 #42115722898](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42115722898); [protocolo 42 unidades y preflight BLOCKED esperado #42119458698](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42119458698). Cada run respalda su SHA histórico específico; no presentar como una única prueba de producto final.

La última unidad de código `S3_A1_N015_SCHEMA7_GENERATION_XML_CI` SHA `088c0e51b478d726bfd884493d5f68173ce0250d` tiene B7 independiente `docs/s3-night/S3_A1_N015_SCHEMA7_GENERATION_XML_B7_PASS_20261105.md`; permiso `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` ahora **BLOCKED** en commit `2960cff8bada80125818008d61c5cacf80ea83ab`. No inventar tests del G15.

## Dependencias explícitas, NO cerradas ni perdidas
1. **N031 / Pendientes:** falta owner real agregado Matter + Reminder + MonthlyPlan, semántica prioridad/due/snooze y read admission ACL. Mientras falte, Inicio muestra «No disponible». Trabajo de fuente/seguimiento transferido a **S3.A4**, no contado como contenido funcional entregado de A1.
2. **N015 / Actividad reciente:** la lectura Reality de receipts con timestamp duradero existe como unidad acotada 6/6, pero sigue **REALITY_ONLY_UNMOUNTED**; faltan federación real `history.query`, filtros, permisos y conexión a Home. Trabajo de Actividad transferido a **S3.A4**.
3. **Guardados:** no hay owner canónico completo ni contador certificado. La revisión de **8 operaciones candidatas NO implica aprobación**; catálogo oficial **172**. Implementación de Guardados y conexión a Inicio transferida a **S3.A3**.
4. **S3.A5:** tests integrados adversariales X01–X12, accesibilidad Android instrumentada, recovery y **única campaña física Moto G15**; aún NOT_RUN. La experiencia completa de Inicio con los 4 datos reales no se declara PASS hasta integrar estas fuentes y A5.

## Restricciones y próxima acción
S2 permanece CLOSED 8/8; S3.A2 sigue CLOSED_AUTOMATED_SCOPE 5/5; S3.A1 se agrega como segundo cierre **solo en alcance automatizado UI/shell**: **2/5 etapas cerradas en alcance automatizado, 0/5 entregas finales integradas**. No deducir «40% de desarrollo real» del 2/5; la estimación de desarrollo anterior no se recalculó. V2 productiva OFF; S4 OFF; no APIs pagas, no cambios en main, catálogo 172, Auto/Noche OFF.

**Próxima etapa:** S3.A3 Guardados, empezando por bounded review del catálogo/owner, o S3.A4 owners de Pendientes/Actividad según dependencias. Un nuevo cambio funcional requiere nuevo B1–B5, permiso READY acotado y B7, nunca reutilizar el permiso BLOCKED. Esta reconciliación no relaja guards ni duplica CI verde. Los datos faltantes en Inicio permanecen visiblemente «No disponible» hasta existir read owners auténticos.
