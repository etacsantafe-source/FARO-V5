# FARO V5 — Baseline de importación sin historia (2026-10-30)

Referencia fuente: `etacsantafe-source/FARO`, rama `work/faro-v5-design-clean-20261019`, commit `44f1e6eb77102a71955b4fb113582d209d94ac69`.

## Contrato de importación

- Únicamente subárbol de código/build/pruebas `v5/` completo: **541 blobs** y 3.274.580 bytes de archivos (sin contenido externo ni historial).
- Igualdad entre blob SHA y modo de archivo de la instantánea en origen y destino para 541/541, verificada antes de commit.
- Un script de soporte T4 (`scripts/v5/faro-v5-s0-t4-physical`) requerido literalmente por `v5/build.gradle.kts`.
- Nunca importar ramas, tags, commits anteriores, `app-v3/`, `app-v4/`, `app/`, `ci/`, `diagnostics/`, docs legadas ni claves de firma.
- Material de firma antiguo `ci/faro-alpha-signing.p12.b64`: NO incluido. Certificar y gestionar firma futura por vía externa, no guardar en Git.
- Escaneo preliminar de patrones de token GitHub/Groq y PEM privado sin positivos al transferir archivos de texto. **No equivale a auditoría completa de secretos ni autoriza publicación**.

## Handoff C138

- S2.A6: OPEN, 3/8 criterios congelados.
- F1: Android Chat/Composer/Review/Apply/Delivery IN PROGRESS.
- F2: Activación/recuperación V2 Android IN PROGRESS.
- F3: Respaldo/restauración 5 stores/SAF IN PROGRESS; bloqueo material F3/S5 sin resolver.
- F4: Regresiones integrales/T4 físico PENDING/NOT_RUN.
- Writer: NONE al cierre C138; V2 OFF; V1 vigente; S3/S4 no abiertos.
- Evidencia completa original: `docs/v5/checkpoints/V5_S2_A6_C138_SAF_HOST_BOUNDARY_20261030.md` del repo privado original.

No mover autoridad durable, liberar V2, declarar S2 PASS, ni ejecutar automáticamente hasta certificación y autorización.
