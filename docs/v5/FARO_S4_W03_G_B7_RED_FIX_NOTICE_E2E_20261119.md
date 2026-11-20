# FARO V5 — W03-G · B7 RED_FIX por cobertura Android incompleta (19/11/2026)

Unit `S4_W03_G_GLOBAL_B7_ANDROID_DEV`; code `2a68b42af4fc6aa8ab14f758e4a3c2b992fc5ce8`, parent permit `7a6291475f701bacd4b89639ab9832cd0f604739`.

## B7 — RED_FIX (mismo alcance, no cierre global)

FARO protocol guard `44647621469` SUCCESS. API35 workflow `44647621706` primer intento FAIL por error transitorio de resolución Maven Kotlin 2.4.10 en Gradle **antes de compilar test**; mismo SHA, segundo intento job `135995670608` **SUCCESS**, JUnit XML **1/1**, sin errores ni skips, `W03_G_ANDROID_API35_REAL_OWNER_1_1_PASS`, manifest offline PASS. APK sha256: `fd4b849b14dfc57b48c63740346260d6689929bfb8d4e7e62f95ca54b25ee1e0`. Artifact `14588664894`, ZIP digest `sha256:3b3d9d84af0cf3b2e39146b29963a54e5bd64de687e316055fe663f7ea03e357`. No need to repeat this base test under same SHA.

**Defecto de aceptación detectado:** el test cubre Matter/Reminder Review→Apply/receipt, descarte, estado, pausar y reabrir en fuente 200 %, pero no ejercita la ruta Android Reminder→local Notice Auxiliary→Revisar Visto/Descartar CAS con revisión/recibo. Panel de Recordatorios no ofrece gesto nativo para `S4W03OwnerSessionActions.reconcileLocalNoticesAfterExplicitRequest`. Tampoco están cubiertos tests negativos de ausencia de read grant de Avisos, stale revision y deshacer. B5 impide declarar W03-G global PASS. El problema es **cobertura real y conexión de UI faltantes**, no falla de la prueba 1/1 existente.

**RED_FIX limitado a la misma unidad G y al permiso vigente de 10 rutas.** Implementar click explícito de conciliación local (sin OS), flujo de lectura Avisos autorizado y test API35 dirigido con receipt de Seen/Dismiss y denegaciones; ampliar XML exacto. Si existe fallo real, corregir sin reescribir A-D o E B7. No tocar shipping ni main; no habilitar permiso POST_NOTIFICATIONS. Tras CI real y XML/hash verificar B7 final, consumir permiso, release writer. 
