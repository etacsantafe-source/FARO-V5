# FARO S2 Noche A1 · 02/11/2026

Base: a942a53bd84ee849502d966ca9b52e002a194c6e. Branch exclusiva: work/s2-chat-finish-night-20261102. Writer NONE al handoff.

Cambio funcional: Activity de Chat accesible en launcher **solo debug** con cinco stores persistentes aislados, Groq real condicionado por consentimiento/Keystore local, Composer/Timeline/Review, Apply a Reality de prueba con receipt y Notice durable idempotente. No notificación del SO. Sin cambios a main, V1 ni V2_ACTIVE_ENABLED. No se enviaron secretos ni mensajes reales.

Validación **PENDIENTE** al commit: CI nuevo dirigido `.github/workflows/faro-v5-s2-chat-isolated.yml` compila APK y ejecuta 4 JVM. No afirmar PASS hasta comprobar run/XML. Emulador de esta Activity, A5 y T4 físico pendientes.

Próximo turno: comprobar run del SHA, corregir ROJO, añadir Android E2E/reinicio/consentimiento y A5, preparar APK única. Reutilizar F2/F3/F4 previos. S2 OPEN 6/8. V2 OFF.
