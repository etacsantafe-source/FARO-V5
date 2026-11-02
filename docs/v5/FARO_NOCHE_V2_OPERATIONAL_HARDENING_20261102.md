# FARO V5 — Noche/Auto hardening y activación futura — 2026-11-02

ESTADO: CÓDIGO PREPARADO EN RAMA AISLADA. NO AUTORIZA ACTIVAR NOCHE.

## Cambios
- Writer: --target-branch obligatorio; acquire/verify comprueban SHA real de rama remota y lock CAS. Una operación mutante posterior debe verificar nuevamente antes de escribir.
- Runner: admite únicamente faro-auto-v2-readiness.py y faro-s3-verify.py, sin shell arbitrario ni escrituras de aplicación.
- Cola LOCAL: FARO_V5_QUEUE_ROOT absoluto, privado 0700, fuera del repositorio; lock por flock, estado durable con fsync, y STOP tras RUNNING/BLOCKED incierto. Requiere FARO_V5_SINGLE_HOST_QUEUE_ACK=YES.
- Cola COMPARTIDA entre sesiones cloud: referencia GitHub independiente faro-v5-night-queue; cualquier sesión debe leer el último SHA, poseer writer, modificar por CAS, registrar checkpoint y liberar. La cola local NO sustituye la cola cloud.
- GitHub Actions: probe aislado de Git push CAS real sobre faro-v5-writer-ci-probe, sin tocar writer productivo, con limpieza al finalizar.

## Límites aún obligatorios
- Probar Actions reales y verificar eliminación de la rama temporal de probe.
- Certificar el CLI Git del host real que ejecute automatizaciones: push, verify, release; credenciales seguras y guardas Resource Guard.
- Acordar protocolo operativo de Noche con tareas S3 acotadas y commits por CAS, integración GitHub nativa en cada sesión; no basta un PASS de tests Python.
- No activar antes de autorización explícita en el próximo chat, no usar APIs pagas.

## Invariantes
S2 CLOSED 8/8; app V2 OFF; main intacta; S3/S4 no abiertas en este trabajo; Noche A/B no activadas; writer al final RELEASED.
