# FARO V5 — AUTO de espera útil de GitHub Actions V1 (modo manual)

**AUTORIZADO POR USUARIO el 2026-11-06, y ACTIVO para turnos MANUALES** de «Continuar FARO V5». Permite aprovechar el tiempo *dentro de la misma ejecución de ChatGPT* mientras GitHub Actions procesa CI. No activa FARO Noche, tareas programadas, agente remoto, V2 productiva, CLOUD queue ni ejecución fuera del chat. «AUTO OFF» histórico se refiere a la automatización programada, no a este protocolo de espera manual.

## Regla de ejecución

1. Recuperar HEAD, pointer, permiso, writer GLOBAL CAS, cola y Actions por **SHA exacto**; priorizar el último CI fallido y checkpoint, sin repetir trabajo completado.
2. Tras B6, liberar writer ANTES de CI. Si el workflow está QUEUED/IN_PROGRESS, activar **AUTO_ESPERA_EN_TURNO**: en el mismo turno trabajar en una oportunidad independiente de solo lectura (estudiar fuentes existentes, comparar V4.4 y alternativas reales, preparar B1–B5, análisis adversarial y plan de pruebas). No declarar PASS sin logs/XML. No iniciar nuevo código funcional mientras la unidad anterior no tenga B7 y el permiso esté READY.
3. **No ocupar writer en espera.** Para documentación durable (si permitida) verificar writer, adquirir CAS estrictamente y soltarlo de inmediato; preferir investigación y análisis sin escritura durante CI. Ante writer HELD ajeno, seguir con READ_ONLY; sin takeover, sin cancelaciones de Actions en marcha.
4. **CI ROJO / fallido / cancelado / timed out:** interrumpir oportunidad secundaria, inspeccionar logs, mantener B7 en RED_FIX y tratar exclusivamente la unidad fallida hasta corrección autorizada; no avanzar a otra B6.
5. **CI VERDE:** corroborar que SHA coincide y revisar XML/logs dirigidos, ejecutar B7 independiente; actualizar permiso a BLOCKED y checkpoint; liberar writer. Después seguir la próxima unidad autorizada. Un «rojo»/«verde» que envíe el usuario dispara verificación, no sustituye pruebas técnicas.
6. Si GitHub CI sigue pendiente al finalizar el turno, guardar estado honesto y proporcionar enlace a Actions; en el próximo mensaje «Continuar FARO V5» o aviso de usuario retomar. **No prometer trabajo en segundo plano tras terminar la respuesta.**
7. Banco de oportunidades elegibles (en orden): (A) auditoría B7 si la CI ya es verde; (B) N033B3 B1–B5 contratos y preflight; (C) revisión acotada de huecos ya conocidos sin reabrir etapas; (D) comparativa contra V4.4 y proyectos externos; (E) reproducir/anotar fallas del CI rojo. Nunca inventar tests, cambios funcionales, firmas, permisos o resultados.
8. Reportar al final: unidad, SHA/CI, writer, próxima tarea, S2 formal/S3 estimado y formal, estado V2 y termómetro cualitativo. Preservar reglas de siete bloques, catálogo 172, no APIs pagas, y single-writer.

## Estados independientes
- `manual_auto_wait.enabled=true`: **regla de continuidad activa por autorización de este turno**, no un proceso persistente ni job.
- `automation.activation_authorized=false`, `queue.mode=STAGED`: FARO Noche/A-B/programadas **siguen OFF**.
- `app_v2_enabled=false`: no se habilita app productiva.
- Si no hay Actions pendientes, ir directo a B7/cierre y siguiente unidad; no inventar demora para usar el modo.

**Alcance de autorización:** organización del trabajo manual e investigación independiente durante espera, sin modificar límites vigentes ni autorizar expresamente nuevas acciones funcionales fuera del protocolo. No se crea ninguna automatización ni agenda sin pedido separado.
