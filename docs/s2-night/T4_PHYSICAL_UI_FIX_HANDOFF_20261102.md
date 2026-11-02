# FARO V5 S2 — T4 físico observado y corrección UI (02/11/2026)

## Evidencia de usuario, NO verificación automatizada de hardware
- APK aislada anterior validada en Actions #41421309967, paquete `ar.com.faro.alpha.s2isolated.v5dev`.
- Usuario informó Chat abierto y respuesta real de Groq: VERDE.
- Mensaje de cambio de nombre creó Review `Pendiente de revisión`; después usuario informó `Aplicado y registrado`: VERDE.
- Tras force-stop y relanzamiento explícito, usuario informó que el cambio `Prueba T4` aparecía de nuevo: persistencia observada.
- No se obtuvo aún atestación inequívoca de recibo y aviso únicos/no duplicados tras relanzamiento. S2.A6 permanece OPEN 6/8; no declarar 8/8 por inferencia.
- Android Recientes ocultaba el Chat porque el manifest debug contenía `excludeFromRecents=true`; corrección anterior `f3532d54` lo dejó `false`, pero la APK original instalada no contiene esa corrección.

## Hallazgo UI que motivó hotfix
- Usuario vio el JSON interno `{"type":"profile.update_declared","display_name":"Prueba T4"}` en vez de una propuesta comprensible.
- `FaroV5PlanReview` y mensajes FARO en `FaroV5MessageTimeline` presentan la acción conocida como "Cambio de nombre declarado / Nuevo nombre: Prueba T4", sin alterar el payload canónico ni autoridad del Apply. Tipos desconocidos se muestran textuales sin inventar acciones.
- La pantalla Chat aislada debug tenía `FLAG_SECURE` y producía capturas negras. Se quitó SOLO de `FaroV5F1IsolatedChatActivity` (debug); `FaroV5AiSettingsActivity` conserva `FLAG_SECURE` para proteger la clave Groq, y no se modifica producción.
- Tests Android: verificar capturas permitidas en Chat debug y descripción humanizada de la propuesta durante el flujo integrado.
- Último SHA de código y test: `ada8abb7a019751c77cb6cec8989781735bcc6e0`; requiere GitHub Actions SUCCESS con XML y una nueva APK validada antes de instalar. NO reutilizar artifact #13129913168 para comprobar estas correcciones, porque corresponde al SHA anterior `def90be3`.

## Próximo paso mínimo
1. Verificar Actions del nuevo SHA, XML UI y única APK; no repetir SAF.
2. Actualizar por ADB solo la APK aislada sin borrar datos, para verificar capturas y texto humanizado.
3. Comprobar recibo, aviso y ausencia de duplicación tras relanzamiento, sin repetir llamada Groq o Apply ya observados salvo error. Solo con evidencia física suficiente emitir dictamen final 8/8.
Protecciones conservadas: V2 OFF, main y FARO habitual intactos, S3/S4 cerrados, writer NONE.
