# FARO Auto V2 — integración aislada (2026-11-02)

Estado: PREPARACIÓN PARCIAL / NO OPERATIVO / NO AUTORIZADO PARA EJECUTAR.

## Evidencia del teléfono
- La copia histórica local `~/FARO-v5` apunta a `etacsantafe-source/FARO`, rama `work/faro-v5-design-clean-20261019`, HEAD `44f1e6eb7`. No modificarla.
- Su daemon informó `STOPPED`, `AUTO_STOP_IDLE`, `IDLE`, sin tarea.
- Resource Guard V1 devolvió `STOP` / exit 20: memoria disponible 849 MiB, umbral stop 885 MiB. No arrancar worker.
- Sus scripts v5 tienen sintaxis Bash válida, pero esto NO certifica ejecución ni integración.
- El nuevo repositorio FARO-V5 no tenía daemon/instalador Auto v5 en `scripts/v5`: no copiar scripts históricos a ciegas.
- FARO Noche: 25 automatizaciones históricas consultadas, 0 habilitadas. Su funcionamiento de ejecución no está certificado.

## Entregado en esta rama
- `scripts/v5/faro-auto-v2-preflight`: comprobación READ_ONLY, fail-closed, de remoto correcto, rama aislada, worktree limpio, S2 8/8, app V2 OFF, S3/S4 OFF y autorización de automatización desactivada.
- El preflight NO concede permisos, NO consulta un writer global en vivo y NO ejecuta tareas.

## Requisitos pendientes antes de habilitar Auto/Noche
1. Implementar writer único compartido por Auto, Noche A/B y sesiones manuales, usando referencia GitHub dedicada con actualización CAS y validación de HEAD; documentar identidad, epoch, expiración y prueba de liveness. Un lease vencido jamás autoriza toma de control si puede existir un escritor activo.
2. Diseñar cola/checkpoint durable y protocolo de recuperación sin duplicar trabajo. El writer debe comprobarse en vivo antes de cada mutación, no inferirse de RUN_STATE.
3. Portar de forma revisada daemon, worker, Resource Guard, auto-stop y permisos desde copia histórica o reconstruirlos con pruebas, sin hacer fetch/merge automático contra el repositorio antiguo.
4. Probar escenarios de exclusión, pérdida de red, proceso colgado, HEAD cambiado, falta de memoria y dos escritores simultáneos, en rama aislada y con operaciones inocuas.
5. Registrar PASS/FAIL con evidencia. Recién después, solicitar autorización separada para activar Auto/Noche y para abrir S3.

## Invariantes
S2 CLOSED 8/8. App V2 OFF. S3/S4 no abiertos. Main, repo histórico y APK sin cambios. Ningún daemon, automation o writer se inicia por crear esta rama.

## Avance de integración (2026-11-02)
- Writer CAS compartido implementado en `scripts/v5/faro-writer-cas.py`, con rechazo de HELD y sin toma automática por vencimiento.
- Runner único para Auto, Noche A/B y manual en `scripts/v5/faro-guarded-run.py`; exige autorización explícita, preflight, adquisición, verificación y liberación del writer.
- Pruebas de identidad/admisión y guardas de ejecución en `test_faro_writer_cas.py` y `test_faro_guarded_run.py`.
- Actions #41475066442 SUCCESS para primera versión de tests writer; **no equivale a certificación del runner nuevo**.
- Pendiente: nuevo CI del runner, pruebas de concurrencia Git remota, integración con daemon real y sesiones Noche, verificación de Resource Guard en entorno nuevo, protocolo de revalidación por cada commit, recuperación de fallos y cierre de permisos.
- Estado: **NO COMPLETADO / NO ACTIVADO**. No abrir S3 todavía; no confundir guardas de seguridad con ejecución operativa completa.
