# FARO V5 — verificación de bootstrap del writer compartido (02/11/2026)

Estado: **REFERENCIA DE COORDINACIÓN CREADA Y CAS API REMOTO VERIFICADO / AUTOMATIZACIÓN AÚN BLOQUEADA**.

## Evidencia GitHub verificada
- Repo: `etacsantafe-source/FARO-V5`.
- Rama de estado exclusiva: `faro-v5-writer-lock`. **No es una rama de código de producto**.
- Bootstrap inicial: `12a90c393324056a75cc1738415d31e6f83eab4f`, estado `RELEASED`, epoch 0, sin secreto.
- Test real sobre GitHub usando CAS remoto de refs (API de GitHub, NO `git push`): `HELD` temporal, actualización competidora con expected SHA obsoleto **RECHAZADA**, liberación final `RELEASED` en `d0aff23ed1d9b27e14f73ecdcf3e23f619aa2932`, epoch 1.
- Actions `#41476966501`: unit tests + Git bare LOCAL `SUCCESS`. No equiparar este PASS con prueba de push CAS real GitHub.
- Commits de código de referencia antes de documentar: rama `work/faro-auto-v2-integration-20261102` `9b865a8b7720453f916997cbefb47195c3b2e787`; `main` `9bae214541f466a62e149853931b96abb8d74ed9` sin modificaciones.

## Gates todavía pendientes para habilitar Noche
1. Probar `faro-writer-cas.py` con Git remoto y credenciales del ejecutor real, incluyendo acquire/verify/release, dos contendientes y recuperación de fallo. El test API demuestra semántica CAS del servidor, no todo el wrapper CLI.
2. Conectar TODOS los procesos que puedan escribir (Auto, Noche A/B, manual y GitHub API) al mismo writer, con revalidación del HEAD de la rama destino antes de cada commit.
3. La cola `scripts/v5/automation-v2` actualmente está dentro del worktree y NO está ignorada por `.gitignore`: encolar puede hacer fallar el preflight por árbol sucio. Rediseñar almacenamiento compartido/durable y probado; no solucionarlo ocultando una cola local no compartida.
4. El runner `faro-guarded-run.py` permite sólo ejecutar `faro-auto-v2-readiness.py`; no admite tareas de desarrollo. Revisión de la allowlist, checkpoints y admisión por mutación antes de permitir trabajo real.
5. Certificar carga de recursos, fallo de conexión, jobs en vuelo, cierre de ventana/lease perdido, limpieza de secretos y automatizaciones reales antes de programarlas.

## Prohibiciones vigentes
- **No activar Noche A/B, FARO Auto, V2 de app, S3 ni S4 por este documento.**
- No modificar main, FARO histórico, S2 cerrado 8/8, APK ni datos reales.
- Si writer `HELD`, no tomarlo por vencimiento; si estado no verificable, detener escrituras.
- Tras cada prueba del writer, comprobar `RELEASED` con lectura remota. Un test fallido no habilita el sistema.

## Decisión operativa
La referencia ausente quedó resuelta. El estado de activación continúa **NOT_READY**; no afirmar certificación global ni establecer `FARO_V5_WRITER_COORDINATION_READY=YES` hasta terminar los gates.
