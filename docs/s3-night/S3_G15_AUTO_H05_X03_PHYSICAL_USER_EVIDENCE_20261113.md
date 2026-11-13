# FARO V5 — Resultados físicos G15: batería automatizada H05 y X03
Fecha AR: 2026-11-12 23:59:37 (según ruta local de evidencia), reportada 2026-11-13.
**Fuente: transcripción de Termux aportada por el usuario.** No se accedió a PNG, XML, bases o log privado desde Work/GitHub. Este documento es evidencia humana de ejecución, NO certificación independiente de imágenes.
Paquete G15: `ar.com.faro.g15isolated.v2coredev`. Paquete X03 independiente: `ar.com.faro.alpha.x03aidev`. Moto G15 Android15; ADB `192.168.1.227:38627` en el momento reportado, batería5% con cargador, no apagar durante escritura.

## Resultado exactamente observado
- Preflight: `PASS_DISPOSITIVO_CARGADOR_Y_2_APPS`; WakeLock ACTIVADO y LIBERADO.
- X03: `PASS_UID_SEPARADO_DE_S2_Y_G15`, `PASS_PROCESO_FISICO_LAUNCH`. No HTTP ni Groq real.
- H01 Chat: `PASS_FISICO_USUARIO_ANTERIOR_NO_REPETIR`, confirmado por el usuario: un mensaje nuevo se vio una vez y persistió al reabrir. Captura previa en conversación, no archivada en repo. No volver a enviar token incierto anterior.
- H02 Guardados: `PASS_FISICO_USUARIO_ANTERIOR_NO_REPETIR`, confirmado por el usuario: cancelar→Ver activos Vacío, guardar→Guardados actualizado, consultar/abrir fuente, quitar→quitados→restaurar→fuente exacta. No repetir carpeta ya PASS.
- H04 Buscar: `PASS_FISICO_USUARIO_ANTERIOR_NO_REPETIR` según confirmación del usuario; búsqueda y revocación al pasar a HOME.
- H05 HOME_RETURN: `PASS_PROCESO_REANUDADO_UI_REQUIERE_REVISION`, capturas privadas `h05-01-antes-home.png` y `h05-02-regreso-home.png` sin inspección independiente.
- H05 RELAUNCH: `PASS_PROCESO_RECREADO_UI_REQUIERE_REVISION`, captura `h05-03-reinicio-proceso.png`.
- H05 FONT_200: `PASS_TECNICO_ESCALA_2_CONTROLES_5_VISUAL_PENDIENTE`, captura `h05-04-fuente-200.png`. `FONT_RESTORE: OK`; no marcar legibilidad al 200% PASS visual.
- X03 screenshot privado `x03-01-abre-sin-consentimiento-nuevo.png`, sin verificación visual independiente.
- H03 `BLOQUEADO_POLITICA_Y_HOST`; H05 TalkBack `NOT_RUN_REQUIERE_USUARIO_Y_LECTOR_REAL`; bloqueo de pantalla `NOT_RUN_EVITA_PIN_Y_BLOQUEO_ADB`.
- H06 `PENDIENTE_HUMANO_FREE_CLAVE_Y_CONSENTIMIENTO`; H07 `PENDIENTE_HUMANO_CONSENTIMIENTO_CONSULTA`; H08 `NO_EJECUTADO_REQUIERE_APROBACION_EXPLICITA`.
- Notificación `Termux:API no confirmada`, no afirmar éxito de notificación en esta batería; fin fue visible por terminal.
- Resultado global exacto: `ESTADO_FINAL: PARCIAL_OK_REQUIERE_HUMANO`. Evidencia local `/data/data/com.termux/files/home/faro-g15-isolated/s3-auto-20261112-235937` en teléfono, no sincronizada con repo.

## Decisión y no repetición
Conservar H01/H02/H04 como PASS físico **reportado por usuario**, H05 como PASS técnico de proceso/escala y revisión de UI pendiente, aislamiento+arranque X03 como PASS físico técnico. Ninguna de estas observaciones cierra U01 A4, U03 integrador, H06/07 Groq o H08 Apply; no transformar la falta de fixture/credencial en FAIL de aplicación.
No volver a ejecutar este robot ni forzar Gestos/UI/Reality8. No introducir clave Groq en Termux, CI, chat, archivos de log o comandos. Un único consentimiento humano Free y test remoto seguro separado; antes de nuevo B6 funcional requiere B1–B5 + READY propio.
Si se quiere verificar H05 legibilidad, revisar únicamente capturas privadas, sin reejecutar la campaña; quitar datos personales antes de compartir.
Histórico exacto: IME G15 visible PASS scoped; Android API35 IME2/2 #43302542761; X03 distintiva2/2 #43344576978; U02 B7; no repetir.
S3 scope 3/5, formal0/5 (sin recalcular). V2 productiva/S4/Auto/Noche OFF. Writer se libera tras commit documental y guard.
