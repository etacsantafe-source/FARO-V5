# FARO V5 · S3.A5.7 X04/X05 — especificación de ejecución para Work (B6 solamente)
Fecha: 2026-11-11 AR. Estado: **ANEXO DOCUMENTAL DE PRECISIÓN, NO B6 / NO CI / NO B7**.
Unidad: `S3_A5_7_X04_X05_ANDROID_GUARDADOS`.
PREVALENCIA: `AGENTS.md`, permiso `FARO_PROTOCOL_WORK_PERMIT.json` y B1–B5 certificado `S3_A5_7_X04_X05_ANDROID_B1_B5_20261111.md`. Este anexo no modifica permisos, alcance, contratos ni resultados ya certificados.

## Estado y orden de ejecución
- Checkpoint al redactar: `10df7531dd3d3aa0779d9415188174d56a6257d9`; permiso `READY` para **sólo dos rutas**; guard documental `43067788907` SUCCESS. Revalidar HEAD, lock y permiso de nuevo antes de cada escritura. Writer global CAS; release antes de CI. NO iniciar código desde este anexo sin preflight.
- NO repetir A3 M01–M05, ni A5 anteriores B7 PASS. A3 pruebas 11/11 y navegación M04 13/13 ya certificadas; **no son pruebas Android X04/X05**.
- Work B6: escribir sólo `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57BookmarksUiTest.kt` y `.github/workflows/faro-v5-s3-a5-7-bookmarks-android-directed.yml`; CI exacta, ZIP/artefacto, B7 independiente y checkpoint. Nuevos owners, Gradle, catálogo, UI de producción o código A3: **NO**.

## Mapa de fuentes y selectores ACTUALES (no adivinar)
| Pantalla | Archivo real | Selector/expectativa |
|---|---|---|
| Launcher DEV nativo | `FaroV5V2CoreDevActivity.kt` | botón `Abrir Chat local de desarrollo` y `Consultar Guardados locales (DEV)` |
| Chat Compose | `FaroV5ActiveChatScreen.kt` + `FaroV5MessageTimeline.kt` | `Buscar en esta conversación` y `Enviar`; editor Compose vía `hasSetTextAction()` |
| Fila de Chat | `FaroV5BookmarkActionSurface.kt` | `Guardar / Quitar` → diálogo Compose título **`Guardar mensaje`** → `Confirmar` → `Guardados actualizado` |
| Guardados nativos | `FaroV5V2CoreDevBookmarkFolderControls.kt` | `Ver activos`, `Ver quitados`, `Referencia 1`, `Abrir fuente`, `Quitar referencia`, `Restaurar` |
| Mutación nativa | mismo controles | diálogo Android `Confirmar Guardados` → `Confirmar`; resultado `Guardados actualizado` o `Resultado pendiente de verificar` |
| Conversación original | `FaroV5ShellNavigation.kt` y `FaroV5MessageTimeline.kt` | fuente Workspace, messageId exacto, foco efímero; confirmar mensaje real en timeline, no texto de editor |

Reutilizar helpers de `FaroV5S3A57ComposeChatUiTest.kt`, `FaroV5S3A57ActivityAvisosComposeUiTest.kt`, `FaroV5S3A57SearchBackgroundRevokeUiTest.kt`. Estas clases ya tienen reglas `createAndroidComposeRule<FaroV5V2CoreDevActivity>()`, visita de Buttons nativos, `waitUntil` y reinicio de Activity sin mocks. No duplicar sus pruebas.

## Puntos críticos descubiertos en código real
1. **No existe navegación DEV Compose → menú nativo Guardados**. `FaroV5SurfaceHost.install` reemplaza el contenido de Activity con Compose; antes de pulsar `Consultar Guardados locales (DEV)`, recrear Activity mediante `rule.activityRule.scenario.recreate()` y recuperar la Activity y sus Buttons NUEVOS. Jamás conservar View/Button/Session de Activity anterior.
2. **No asumir que «Referencia 1» es la recién creada**. `AuxiliaryBookmarkStore.read` usa `ORDER BY bookmark_id` y M02 genera `BookmarkId(UUID.randomUUID())`. El orden es lexicográfico de UUID, NO temporal. Los dos métodos Android de una clase comparten sandbox de aplicación salvo aislamiento explícito. Preparar escenario limpio por test EN EL EMULADOR de forma demostrable y segura (antes de abrir stores) o una estrategia de asociación exacta por UI que verifique destino. Si no se puede garantizar, detener con `NEEDS_PREP`; **prohibido** fingir correlación por índice, exponer ID/cuerpo en metadatos, inyectar un port productivo o hacer `pm clear` contra G15.
3. **Tras prepare/confirm native se borran las filas existentes**: `prepare()` llama `rows.removeAllViews()` y `confirm()` sólo cambia `status`. Exigir `Guardados actualizado` primero, luego clic explícito `Ver activos`/`Ver quitados` para releer. No dar por restaurado un item sólo por el resultado de texto.
4. **Diálogos distintos**: para `Guardar mensaje` usar nodos Compose y `rule.onNodeWithText("Confirmar")`; para `Confirmar Guardados` usar interacción REAL del diálogo Android. Gradle NO declara Espresso/UIAutomator como dependencia directa: no introducir imports suponiéndolas disponibles. Alternativa test-only sin nueva dependencia: `InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow` y `AccessibilityNodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)`, previa identificación exacta de la ventana `Confirmar Guardados` y botón `Confirmar`. Es API oficial de Android; validar su disponibilidad/runtime en CI y **fallar cerrado**, no inyectar coordenadas mágicas. Fuente primaria: https://developer.android.com/reference/android/app/UiAutomation .
5. **Thread/espera**: `FaroV5V2CoreDevActivity.openLocalChat()` ejecuta los cinco stores en executor; native read también es asíncrona. Esperar señal de pantalla real, no sleep arbitrario ni `try/catch` que marque éxito. El timeout de Compose no prueba error SQLite: reutilizar diagnóstico no sensible de clases View/contadores de etiquetas como en `FaroV5S3A57SearchBackgroundRevokeUiTest.kt`.
6. **Previo al permiso, verificar el mensaje**: escribir texto aleatorio sintético, click `Enviar`, esperar que esté como UNO en timeline y que `Enviar` vuelva deshabilitado (editor vacío). No aceptar el eco del editor como persistencia.
7. **El foco**: la presentación del mensaje original sola no demuestra scroll ni resaltado exacto. La prueba X04 requiere el destino correcto confirmado; si no se puede observar foco desde la semántica sin tocar UI productiva, limitar declaración y conservar M04 (foco tipado) como certificación distinta.
8. **Revocación**: `onPause` cierra Bookmark read/Review/Search/Activity; un `recreate()` exige nuevo click nativo para reabrir Guardados. Si la pantalla muestra `No disponible`, no interpretarlo como resultado vacío ni retentar silenciosamente.
9. **Conflictos entre tests**: evitar depender de orden de JUnit, cantidad de mensajes anterior, IDs generados, filas `Referencia 1` preexistentes o leyenda `Vacío` sin lectura autorizada. No serializar cuerpos privados ni tokens/grants en los logs.

## Escenario de Work X04 — pasos atómicos
A. Comprobar APK `ar.com.faro.alpha.v2coredev`; click real botón nativo abrir Chat. Esperar Compose Chat con `Buscar en esta conversación` y `Enviar`.
B. Enviar un USER sintético único y verificar estado aceptado en timeline con editor limpio. Con UNA sola fila del mensaje nuevo identificada, click `Guardar / Quitar`.
C. Verificar que aparece `Guardar mensaje` y que no apareció `Guardados actualizado` antes de confirmar. Click Compose `Confirmar`, esperar `Guardados actualizado`; no duplicar ni retry.
D. Recrear Activity → botón nativo `Consultar Guardados locales (DEV)` explícito → esperar `Referencias activas` con fila correlacionada de manera demostrable; verificar que los metadatos NO muestran cuerpo del mensaje.
E. Click `Abrir fuente`, esperar Chat Compose real, verificar mensaje USER original por UI canónica y ausencia de `Aplicar cambios`. No declarar foco físico si no se puede demostrar.
F. Registro de cobertura: gestos Android reales sobre emulador, no G15.

## Escenario Work X05 — pasos atómicos
A. Iniciar en sandbox DEV verificablemente aislado (o una selección inequívoca de item); crear un mensaje sintético y guardarlo con confirmación real.
B. Recrear y abrir Guardados con gesto nativo; elegir fila correcta, pulsar `Quitar referencia` → esperar diálogo nativo `Confirmar Guardados` y review `Quitar referencia de Guardados` → clic `Confirmar` → estado `Guardados actualizado`.
C. Pulsar `Ver activos`, releer y verificar que esa referencia ya no está activa; no confundir pantalla limpiada por prepare con ausencia persistida.
D. Pulsar `Ver quitados` (recuperación solicitada por el usuario); confirmar fila correlacionada; click `Restaurar` → diálogo `Restaurar referencia quitada` → `Confirmar` → `Guardados actualizado`.
E. `ActivityScenario.recreate()` + NUEVO permiso nativo Guardados → `Ver activos`; referencia canónica aparece de nuevo, sin resucitar contenido por un token/Grant almacenado.
F. En ambas pruebas, validar ausencia de Apply implícito y no inferir un receipt sólo por texto: CAS/recibos ya certificados por A3.

## Workflow dirigido y trazabilidad binaria
- Copiar el patrón REAL ya verde de `faro-v5-s3-a5-7-compose-chat-android-directed.yml`: `on: push` de rama exacta + las DOS rutas, Java 21, `platforms;android-37.0`, `build-tools;36.0.0`, `-PfaroG5AndroidTestV2CoreDev=true`, `:app:assembleV2CoreDev` + `:app:assembleV2CoreDevAndroidTest`, dos APKs, emulador `api-level: 35`, `adb install` y `am instrument`.
- Aceptación **2 tests ejecutados de verdad**, `OK (2 tests)`, `INSTRUMENTATION_CODE: -1`, marcador `S3_A5_7_X04_X05_BOOKMARKS_ANDROID_PASS 2/2` sólo luego de evaluar ambos resultados. Si la estrategia de aislamiento hace necesarias ejecuciones separadas de un test, primero revisar el contrato de prueba documental con B5/permiso pertinente: NUNCA imprimir `OK (2 tests)` sintético para disfrazar `OK (1 test)`.
- Registrar SHA y salida de instrumentación con `OK`, no sólo `gradlew build`. Guard obligatorio `seven-block-evidence` SUCCESS en el SHA funcional. Si rojo, documentar `B7 RED_FIX` independiente ANTES de otro commit funcional en la misma unidad.
- No reabrir A3, catálogo180, Reality COMMITTED/Undo, main/V4.4, V2 productiva, G15, Auto/Noche/S4. No añadir API paga, red ni permisos.
- Al final: B7 independiente con evidencia, checkpoint y writer RELEASED. El máximo dictamen permitido es `X04_X05_PASS_SCOPED_ANDROID_EMULATOR`; A5 global OPEN, campaña G15 reservada al final.

## Banco de fallos y respuesta directa (sin investigación duplicada)
| Síntoma | Causa concreta a verificar primero | Acción permitida |
|---|---|---|
| `Confirmar` Compose no aparece | Review no autorizado o no aparece la fila después de guardar USER | esperar estado real, revisar permiso/perfil, no fakear |
| Ningún botón nativo tras Chat | Activity con `setContent` Compose | recrear y buscar Activity nueva |
| `Confirmar Guardados` invisible para Compose | es `android.app.AlertDialog` en otra ventana | UiAutomation accesibilidad/Views Android sin dependencia nueva |
| `Referencia 1` abre otra conversación | `ORDER BY bookmark_id` UUID, dos tests comparten datos | aislar prueba o asociación correcta, STOP si incierto |
| Filas desaparecen tras `Quitar referencia` | `prepare()` limpia filas, aún sin prueba de mutación | esperar éxito y solicitar `Ver activos` |
| Timeout inicial o después de recreate | native open/Compose mounted o destino restaurado | diagnóstico no sensible y RED_FIX; no aumentar timeout ciegamente |
| `No disponible` al volver de background | grant revocado por `onPause()` | nuevo click nativo explícito, no describirlo como vacío |
| CI verde de Gradle pero Android sin `OK (2 tests)` | compiló APK pero no corrió instrumentación | B7 RED_FIX / INFRA STOP; no PASS |
