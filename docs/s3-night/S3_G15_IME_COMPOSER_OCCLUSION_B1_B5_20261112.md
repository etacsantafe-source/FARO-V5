# FARO V5 / Moto G15 — IME oculta composer — PREAUDIT B1-B5

Fecha 2026-11-12. Rama work/s3-night-20261102. Esto es investigación y diseño, NO una autorización de código ni certificación de solución.

## B1 — evidencia física / diagnóstico

En captura Moto G15 Android15: Chat local con teclado abierto; visible campo de Buscar en esta conversación, NO visible el composer Preguntale a FARO ni botón Enviar. El script A3 V3 imprimió A3-STOP NO_CERTIFICADO — Envío quizá comprometido, sin respuesta inequívoca: NO reenvíes. Aviso terminal PRUEBA TERMINADA sí llegó. El estado at-most-once existe en Termux bajo $HOME/faro-g15-isolated/a3-physical-v3-state.json. NO se confirma pérdida, envío persistido ni duplicación.

La Activity FaroV5V2CoreDevActivity en v5/app/src/v2CoreDev/AndroidManifest.xml NO declara windowSoftInputMode. El manifest binario de la APK aislada compilada en run 43272636986 tampoco muestra este atributo; en cambio v5/app/src/main/AndroidManifest.xml declara windowSoftInputMode=adjustResize para FaroV5BootstrapActivity. El Chat en v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ActiveChatScreen.kt presenta un Column(fillMaxSize) con Box(weight(1f)) para timeline y composer debajo, sin imePadding explícito. La timeline tiene campo Buscar en esta conversación distinto del composer Preguntale a FARO. Hipótesis: gestión IME/insets de Activity/Compose, todavía NO demostrada por una prueba controlada del teclado.

## B2 — reutilización y compatibilidad

Reutilizar el patrón adjustResize existente en Activity de app principal y el layout Chat actual. No refactorizar owners, permisos, Workspace, Reality, Guardados ni otros módulos. Mantener la app original y su UID totalmente intactos. No repetir 5/5 navegación, A3 carpeta ni A4 estados negativos ya PASSED scoped. Mantener datos previos; no reinstalar incompatible, no uninstall/pm clear, no APK androidTest en G15. La APK aislada corregida se firmaría localmente con la clave privada ya existente, no alojar secretos en Git.

## B3 — documentación externa

Android Jetpack Compose: https://developer.android.com/develop/ui/compose/system/setup-e2e recomienda android:windowSoftInputMode=adjustResize para recepción de IME insets; https://developer.android.com/develop/ui/compose/system/insets-ui explica imePadding y consumo de WindowInsets; https://developer.android.com/reference/android/view/WindowManager.LayoutParams describe SOFT_INPUT_ADJUST_RESIZE y su deprecación API30 en favor de insets explícitos. Una línea de manifest sola puede no resolver edge-to-edge Android15.

**Comparación explícita de proyectos reales**: [Jetchat de Google / android/compose-samples](https://github.com/android/compose-samples/blob/main/Jetchat/README.md) es una app de Chat Compose que documenta transiciones IME sincronizadas con `Modifier.navigationBarsPadding().imePadding()` en `ConversationContent`, con gestión de foco y entrada; inspiración de comportamiento de UI, **no** autoridad sobre Workspace/Apply FARO, no copiar sus dependencias ni código sin revisión. [Jetpack Compose Window Insets](https://developer.android.com/develop/ui/compose/system/insets-ui) advierte que los componentes al final de un scroll pueden quedar ocultos si se usa `contentPadding` sin el `Spacer` o manejo de insets apropiado. Alternativa A: `adjustResize` más prueba real; B: `imePadding` solo si falla A y tras revisar consumo de Scaffold; alternativa C descartada: hacer scroll automático o tocar por coordenada sin asegurar visibilidad del composer.

## B4 — propuesta mínima, no implementada

1. Añadir adjustResize solo a FaroV5V2CoreDevActivity en manifest V2CoreDev. Verificar MANIFEST BINARIO de APK resultante.
2. Si la prueba IME de Android35/15 continúa ocultando composer, aplicar Modifier.imePadding/consumeWindowInsets a región correcta de Chat, sin doble padding con Scaffold/NavDisplay.
3. Agregar prueba Android dirigida de teclado visible/oculto, etiqueta y bounds de ambos campos, composer y Enviar alcanzables por encima de IME, estado tras cierre teclado y cambio de app. Emulador API35 y prueba G15 posterior con APK de aplicación aislada, no instrumentación destructiva en móvil.
4. Endurecer automatización A3 para identificar editor de abajo, distinguirlo de Buscar, validar bounds tras abrir IME, y NO pulsar Enviar fuera de viewport. Releer STATE SEND_CLICK_UNCERTAIN, timeline y borrador antes de cualquier nuevo mensaje; nunca reenviar el mismo token a ciegas.

## B5 — auditoría adversarial / límites

Riesgos: falta de ajuste de IME por edge-to-edge; padding doble si Scaffold ya consume insets; UiAutomator devuelve controles no visibles y scripts pueden pulsar sobre teclado; una sesión anterior pudo persistir un envío aun si el resultado no es visible; firma/UID erróneo dañaría instalación existente. Medidas: fail closed ante bounds ambiguos o resultado incierto, cotejar captura/UI tree/IME heights, scopes mínimos por archivo, permiso único y guard B7, instalar solo paquete aislado con SHA comprobado y firma local constante, no usar datos ajenos. Sin B6, CI de fix ni prueba física de solución. La autorización funcional actual se encuentra BLOCKED/consumida; nuevo B6 requiere PREAUDIT aprobada por guard y READY individual por rutas con preflight. NO activar V2 productiva, S4, Auto, Noche o API paga. A3 y A4 siguen OPEN.
