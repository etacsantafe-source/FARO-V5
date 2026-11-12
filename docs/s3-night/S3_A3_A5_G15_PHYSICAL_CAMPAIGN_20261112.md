# FARO V5 — Campaña física manual Moto G15
Fecha 2026-11-12 AR. Estado: PREPARADA, NO EJECUTADA. Work no accedió al teléfono.
Responsables de ejecución/evidencia: usuario y chat manual. Reservar un solo recorrido final, sin repetir CI certificadas.

## APK y alcance
**APK PENDING_CI / NO LISTA PARA CAMPAÑA FINAL.** Código0993e6837bfc955d914a9a60366724c77f2d165a; run43161141857. Hash pendiente de descarga y verificación. No instalar hasta completar esta identidad.
- Paquete esperado: ar.com.faro.alpha.v2coredev; variante v2CoreDev; launcher ar.com.faro.v5.app.FaroV5V2CoreDevActivity.
- Sin INTERNET y sin Activity IA X03 U02; V2 productiva OFF.
- CORE DEV permite Chat local, Guardados, lecturas autorizadas Actividad/Avisos/Pendientes y Reality8 vacía DEV. No tiene edición manual de Finance/Objective/Matter, alta de avisos, creación de recibos Apply ni IA/Review/Apply debug.
- Host debug X03/X09/X10 usa ar.com.faro.alpha.s2isolated.v5dev, el mismo paquete de S2 aislada. **No instalar esa variante en esta campaña**, porque puede actualizar la aplicación S2 existente. La evidencia automatizada debug sigue válida en su SHA.
- APK androidTest instala fixtures destructivas sobre raíz DEV del emulador. **No instalar ni ejecutar el APK androidTest en G15**, tampoco am instrument: borraría datos DEV. Entregar solamente app-v2CoreDev.apk.

## Instalación segura (solo usuario/chat manual)
1. Descargar el APK de aplicación del artefacto indicado, comparar SHA-256 exacto con este documento antes de instalar. Con Termux: `sha256sum /ruta/app-v2CoreDev.apk`. No confundir app y androidTest.
2. Comprobar paquete instalado, versión y firma con herramientas disponibles antes de actualizar. Si ya existen datos DEV que se quieran conservar, detenerse hasta disponer de respaldo autorizado y verificable. No usar pm clear, uninstall, downgrade forzado ni borrar raíces.
3. Actualización ordinaria si paquete/firma/versiones compatibles: instalador Android; alternativamente `adb install -r /ruta/app-v2CoreDev.apk` solo si el usuario dispone de ADB y decide usarlo. Firma incompatible o downgrade: **STOP**, registrar error, conservar la instalación. No resolver desinstalando.
4. Abrir el launcher FARO V2 CORE DEV. Registrar versión Android, versión instalada, package/variant, hash, commit y hora AR. Modo avión es compatible. No copiar bases habituales.
5. No pulsar “Crear Reality8 vacía (DEV)” sobre datos que se quieran conservar. Los positivos de owners se ejecutan solo con fixture explícita y separada; este paquete no trae seed ni datos personales.

## Evidencia común y criterios
Crear un registro por caso: ID, fecha/hora, APK SHA/commit, pasos, resultado esperado/observado, PASS/FAIL/BLOCKED/N_A/NOT_RUN, archivo de captura/video y observaciones.
- PASS: todos los pasos y condiciones observados, evidencia legible y paquete correcto.
- FAIL: cierre inesperado, dato perdido/duplicado, contenido privado expuesto, grant que revive sin gesto, o acción distinta de la confirmada.
- BLOCKED: falta de fixture/host/consentimiento; no equivale a defecto ni PASS.
- N_A: ancho expandido no soportado por el G15; indicar configuración. No forzar wm size/density en el teléfono.
- NOT_RUN: caso aún no ejecutado. No completar con resultados del emulador.
Usar solo texto sintético “G15 FARO prueba fecha-hora A/B”, nombres de carpetas de prueba y referencias propias. Capturas recortadas de pruebas, sin claves ni mensajes personales. Logcat si hace falta, filtrar por proceso y revisar antes de compartir. No compartir bases completas.
Si falla, guardar el paso exacto y evidencia; detener esa ruta, no repetir muchas veces ni alterar datos para conseguir verde.

## Grupo A3 — Guardados (ejecutable offline; inicialmente NOT_RUN)
| ID | Pasos y criterio PASS | Evidencia |
|---|---|---|
| A3-P01 / X04 | Abrir Chat local; enviar A y B distintos. Cada USER aparece una sola vez y no hay respuesta FARO inventada | Captura timeline |
| A3-P02 / X04 | En mensaje A usar “Guardar / Quitar”. Revisar “Guardar mensaje”, cancelar: sin alta. Repetir y confirmar: “Guardados actualizado”, sin Apply | Antes/cancelación/confirmación |
| A3-P03 / X04 | Cerrar y abrir app sin borrar datos; “Consultar Guardados locales (DEV)”, “Ver activos”; abrir fuente de A. Conversación y mensaje exactos, no B, sin duplicado; anotar foco/resaltado realmente observado | Video selección→fuente |
| A3-P04 / X05 | “Quitar referencia”→review→Confirmar. Releer “Ver activos”: referencia ausente. “Ver quitados”: aparece solamente por recuperación explícita | Capturas activos/quitados |
| A3-P05 / X05 | “Restaurar”→review→Confirmar. Reabrir Guardados, activos y fuente: vuelve A exactamente una vez | Video corto |
| A3-P06 | Crear carpeta sintética, revisar/cancelar y luego confirmar; renombrar, favorito, mover referencia, mover a raíz, eliminar carpeta vacía. Cada operación requiere confirmación y relectura; cancelar no muta | Review y estado releído |
| A3-P07 / X11 | Abrir review de Guardados y cambiar de app. Volver: diálogo/autoridad anteriores no pueden mutar; nuevo acceso nativo explícito funciona | Video ida/vuelta |
| A3-P08 | Reabrir proceso/app: USER y referencias confirmadas persisten; no se restauran confirmaciones pendientes ni se guarda/quita otra vez | Capturas antes/después |
Backup/restore, cuotas1024/32, paginación50 y fallos SQL ya tienen M05 automatizado. No llenar el G15 ni restaurar datos personales para duplicarlos; si se exige evidencia física, unidad y fixture separadas.

## Grupo X01–X12 — UI, seguridad e integración (inicialmente NOT_RUN)
| ID | Caso / criterio PASS | Disponibilidad |
|---|---|---|
| X01-P01 | Chat normal no concede Actividad. Gesto “Abrir Chat y Actividad local” muestra evento USER sintético con procedencia; pausa oculta y resume no reautoriza | CORE DEV; si no hay evento creable, BLOCKED con evidencia |
| X01-P02 | Recibo Reality COMMITTED visible una vez y sin mutación por consultar | BLOCKED_FIXTURE: CORE DEV no ejecuta Apply |
| X02-P01 | Sin grant Avisos, “no disponibles” y nunca falso cero; gesto “Consultar avisos registrados (DEV)” usa solo Auxiliary. Ausencia legítima y falta de autoridad deben distinguirse | Negativo CORE DEV; positivo con aviso sintético BLOCKED_FIXTURE |
| X02-P02 | Vencimientos autorizados por gesto separado; ausencia de fuentes no se presenta como cobertura completa | Negativo CORE DEV; owners positivos BLOCKED_FIXTURE |
| X03-P01 | Enviar USER local persiste sin red ni respuesta fingida. Consultar remoto solo tras plan Free humano+consentimiento y host correcto | Local CORE DEV; remoto BLOCKED_HUMAN_PROVIDER, NO ejecutar |
| X04/X05 | Referencia compuesta y recuperación explícita | Cubiertos una sola vez por grupo A3 |
| X06-P01 | Buscar token de A desde Chat: resultado Workspace canónico, abrir fuente correcta, sin ID privado | CORE DEV, dato sintético propio |
| X06-P02 | Cuatro owners simultáneos, Finance/Objective/Matter con datos canónicos | BLOCKED_FIXTURE; no sustituir por tres listas vacías |
| X07-P01 / X11-P01 | Editar owner real invalida hit viejo sin cutover; otro owner sigue válido | BLOCKED_FIXTURE/EDITOR; conservar JVM4/4 sin llamarlo físico |
| X08-P01 | Buscar A, cambiar de app, volver: no conserva resultado autorizado ni detalle/token antiguo. Nueva consulta sin regrant indica no disponible, no cero completo | CORE DEV; nuevo gesto nativo permite búsqueda fresca |
| X08-P02 | Revocación durante lectura, cierre owner y ABA de puntero | Solo automatizados; no manipular bases del teléfono |
| X09-P01 | Review colapsada→Abrir→Aprobar sin Apply implícito; pausa invalida apertura | BLOCKED_HOST_DEBUG; no es funcionalidad CORE DEV |
| X10-P01 | Apply explícito una vez, receipt durable, UNKNOWN solo verifica sin reejecutar | BLOCKED_HOST_DEBUG/FIXTURE; no provocar corte de energía durante escrituras |
| X12-P01 | Cambiar de app y volver, bloquear/desbloquear pantalla, cerrar/relanzar: sin crash, sin duplicación, grants revocados; contenido confirmado persiste | CORE DEV; grabación de cada transición |
| X12-P02 | Desde Ajustes Android aumentar fuente a 200% o máximo disponible: Chat/menú/Avisos/Buscar/Guardados legibles, controles alcanzables, sin texto truncado que impida actuar; restaurar valor original al terminar | Usuario; registrar porcentaje real |
| X12-P03 | TalkBack: nombres, roles, orden/foco y confirmaciones comprensibles; botones operables. SwitchAccess solo si usuario sabe usarlo; registrar servicio usado | Usuario/chat manual; emulador semántico no lo sustituye |
| X12-P04 | Orientación admitida y teclado: no oculta acción crítica ni concede grants. Ventana>=840dp solo si hardware la ofrece de forma ordinaria | No forzar densidad/tamaño; expanded N_A si no soportado |
| X12-P05 | Uso breve en reposo/retorno con datos móviles apagados, sin ANR/cierre. Registrar batería/memoria observadas, sin afirmar rendimiento prolongado | CORE DEV; sin instalar monitores ni modificar sistema |
| X12-P06 | Reinicio normal del teléfono solo cuando usuario lo decida, sin escritura en curso: datos confirmados conservados y grants no restaurados | Reservado usuario; no hard reset ni corte durante Apply |

## Cierre humano
A3 solo puede cerrarse después de revisar evidencias de su grupo y cualquier FAIL material. A5 permanece OPEN mientras falten integraciones y X03 live. No inferir cierre global de una campaña offline parcial.
Transferir al repositorio únicamente resultados y evidencias saneadas con writer CAS; no volver a correr suites verdes ni alterar sus cierres históricos. Las brechas nuevas reciben B1–B5 y permiso individual, no reutilizan el permiso X12.
