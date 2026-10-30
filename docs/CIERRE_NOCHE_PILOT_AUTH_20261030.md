# FARO V5 — CIERRE Noche · autorización de piloto de 3 horas (2026-10-30)

Estado inicial: **AUTHORIZED_FOR_THREE_HOUR_PILOT / INACTIVE_UNTIL_SCHEDULED_START**.

## Autoridad
- Usuario autorizó expresamente en este chat el 2026-10-30: «Probamos acá. Activarlo».
- El usuario amplió expresamente la autorización en este chat: «No. Hacela por 3 horas». Esto **sustituye** la duración y programación anteriores. Es un solo piloto de 3 horas, con tres ejecuciones horarias, no una noche sin fin.
- Repositorio: `etacsantafe-source/FARO-V5` (PRIVADO).
- Rama de escritura exclusiva: `work/cierre-noche-pilot-20261030`.
- Referencia fuente: `work/cloud-afternoon-pilot-20261030` @ `03f3cf146b06807324858e824845dc9713db94fa`.
- Protocolo: `docs/cierre-noche/PROTOCOL_V1.md` de la rama `docs/cierre-noche-v1-20261030` @ `38ad3e911ed578d15b9824305eaadedf1a44c997`.
- Permiso del piloto ampliado: inicio programado **2026-10-30 12:09:00 -03**; corte global **2026-10-30 15:09:00 -03** (18:09 UTC), sin prórroga. El permiso queda NO_WRITE a partir del corte. Cada ejecución deberá verificar la hora local y los permisos al iniciar y antes de cada escritura.
- Dentro del permiso: tres ejecuciones programadas 12:09, 13:09 y 14:09 (-03); **máximo 50 min por ejecución** desde inicio efectivo o corte global, lo que ocurra antes. Desde minuto 45 de cada ejecución no iniciar tareas nuevas ni Actions. Cada ejecución debe registrar lo que efectivamente duró; no se garantiza que ChatGPT mantenga 50 minutos continuos.
- **Una única automatización**: `6ae4b1ffede88191812b719ffa87fc8c`, frecuencia horaria, `COUNT=3`; sin automaciones adicionales, solapamientos, extensiones automáticas ni reactivación tras vencimiento. Si alguna instancia sigue en marcha, la siguiente debe fallar en modo seguro por escritor/lease; no sumar dos writers.

## Alcance
Certificar capacidad operativa mediante trabajo útil acotado en **S2.A6**: preflight GitHub/writer/exclusividad, investigar un hueco real independiente de F1/F2/F4, implementar cambio mínimo solo cuando sea seguro, usar GitHub Actions para gate dirigido si corresponde, verificar SHA/XML, documentar ROJO/hipótesis y checkpoint, terminar limpio.
Garantías/contraejemplos del runtime (continuidad efectiva, corte, recuperación) deben registrarse con verdad: **NOT_TESTED** si no se observan.
No promover 3/8 sin pruebas completas, no activar V2, no abrir S3/S4, no modificar `main` ni original `etacsantafe-source/FARO`, ni FARO Auto/V4.4/V1, no publicar, no merge, no modificar permisos/secretos, no usar Termux, no ejecutar T4 físico. F3/S5 queda WAIT_HUMAN.
Si el tiempo real no alcanza, priorizar checkpoint y no comenzar modificación riesgosa.

## Recuperación
1. Verificar HEAD y Actions reales antes de cada escritura y después de cortes.
2. Exigir writer único CAS sobre HEAD esperado, commits atómicos, sin force push.
3. Actualizar checkpoint en la rama exclusiva luego de cada unidad; no guardar secretos, código/logs enteros.
4. Al expirar autorización (15:09 -03), detener escrituras y nuevas pruebas/Actions, liberar writer si aún es posible y dar estado terminal (incluidos trabajos no terminados).
5. Cada nueva ejecución dentro de estas 3 horas debe recuperar los commits y Actions reales de la ejecución anterior y reconciliar todo intento interrumpido. A partir del corte global, cualquier tarea automática posterior tiene **NO_WRITE** sin una nueva autorización explícita.

**Documento de autorización de piloto, no certificado de ejecución.**
