# FARO V5 — CIERRE Noche · autorización de piloto único (2026-10-30)

Estado inicial: **AUTHORIZED_FOR_ONE_CONTROLLED_PILOT / NOT_STARTED**.

## Autoridad
- Usuario autorizó expresamente en este chat el 2026-10-30: «Probamos acá. Activarlo».
- Interpretación de menor riesgo: **un solo ciclo de prueba**, no una noche completa ni autorización recurrente.
- Repositorio: `etacsantafe-source/FARO-V5` (PRIVADO).
- Rama de escritura exclusiva: `work/cierre-noche-pilot-20261030`.
- Referencia fuente: `work/cloud-afternoon-pilot-20261030` @ `03f3cf146b06807324858e824845dc9713db94fa`.
- Protocolo: `docs/cierre-noche/PROTOCOL_V1.md` de la rama `docs/cierre-noche-v1-20261030` @ `38ad3e911ed578d15b9824305eaadedf1a44c997`.
- Permiso de piloto **hasta 2026-10-30 13:03:46 -03** (16:03:46 UTC), inclusivo de cierre; iniciar solo si hay tiempo útil suficiente. No renovar ni prorrogar automáticamente.
- Dentro del permiso: una sesión, máximo **50 min desde inicio efectivo**; desde minuto 45 no iniciar tareas nuevas ni Actions.
- No programar más ciclos; ninguna automatización adicional. Una programación de una sola ocurrencia.

## Alcance
Certificar capacidad operativa mediante trabajo útil acotado en **S2.A6**: preflight GitHub/writer/exclusividad, investigar un hueco real independiente de F1/F2/F4, implementar cambio mínimo solo cuando sea seguro, usar GitHub Actions para gate dirigido si corresponde, verificar SHA/XML, documentar ROJO/hipótesis y checkpoint, terminar limpio.
Garantías/contraejemplos del runtime (continuidad efectiva, corte, recuperación) deben registrarse con verdad: **NOT_TESTED** si no se observan.
No promover 3/8 sin pruebas completas, no activar V2, no abrir S3/S4, no modificar `main` ni original `etacsantafe-source/FARO`, ni FARO Auto/V4.4/V1, no publicar, no merge, no modificar permisos/secretos, no usar Termux, no ejecutar T4 físico. F3/S5 queda WAIT_HUMAN.
Si el tiempo real no alcanza, priorizar checkpoint y no comenzar modificación riesgosa.

## Recuperación
1. Verificar HEAD y Actions reales antes de cada escritura y después de cortes.
2. Exigir writer único CAS sobre HEAD esperado, commits atómicos, sin force push.
3. Actualizar checkpoint en la rama exclusiva luego de cada unidad; no guardar secretos, código/logs enteros.
4. Al expirar autorización, detener escrituras y pruebas, liberar writer, y dar estado terminal (incluidos trabajos no terminados).
5. Cualquier tarea automática posterior tiene **NO_WRITE** sin una nueva autorización explícita.

**Documento de autorización de piloto, no certificado de ejecución.**
