# FARO V5 · W02-E · B7 RED_FIX por auditoría de navegación y lifecycle

## B7
- SHA `bfbfa8469a1fbbf799d8a3511f37da8ddff137e9`, guardia 44447323234 SUCCESS. Al detectar estos puntos, el run Android 44447323486 seguía en espera. Este checkpoint no atribuye un resultado Android a ese SHA.
- En `FaroV5ActiveChatScreen.kt` la operación Confirmar establece `openedReview = null` y recarga la Review con una revisión nueva. `FaroV5PlanReview.kt` ofrece «Ver propuesta aprobada» en ese estado. La prueba W02 saltaba ese paso y consultaba «Aplicar cambios» sin volver a abrir la Review. Alinear la prueba con el flujo explícito de Chat y esperar la semántica del drawer tras el montaje asíncrono de la sesión.
- La recarga de W02 al volver al primer plano se invocaba dentro de `onResume`, donde el registro Lifecycle aún puede estar STARTED. El worker exige RESUMED. Programar la recarga en el hilo UI después del callback y mantener la comprobación de foreground y epoch antes de entregar resultados.
- Limitar RED_FIX a la Activity de W02 y su suite Android. Conservar los gates de commit, font scale 200%, manifest y XML. Writer tras el código RELEASED epoch 1276.
