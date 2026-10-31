# FARO V5 — Cierre Noche V1.3, A6 checkpoint

- Turno: Noche A V1.3, 2026-10-31 04:35–2026-10-31T07:39:11.580Z (UTC end), duración real 124s, no se simuló espera.
- Repo privado: etacsantafe-source/FARO-V5; rama única work/cierre-noche-v13-20261030.
- HEAD de código antes de checkpoint: `dbc9b359f9a477971c3ea95fd706bb6e29a43ea6`; commit funcional: `dbc9b359f9a477971c3ea95fd706bb6e29a43ea6`. La confirmación de cierre actualiza HEAD por CAS.
- Writer: epoch 30, owner NOCHE_A_V13_TURN6_20261031_0435, liberación CAS a NONE en este mismo checkpoint; verificar por lectura posterior.
- Cambios: F3 SAF fallos de lanzamiento/cierre ahora cancelan intento externo aún sin URI, limpian contraseña de preview/restore, descartan consentimiento y recuperan UI; prueba Android de recreación sin credenciales. Tres archivos (Activity, MaintenanceRuntime, AndroidTest). No cambios en main, V4.4, FARO Auto, visibilidad, secretos ni pagos.
- Revisión: diff estático verificado. Compilación/Android NO CERTIFICADOS por falta de runner. Test nuevo `credentialsAndConsentAreDiscardedOnActivityRecreation` NOT_RUN.
- CI previo #41177654138 SHA cf8b223 y #41179478414 SHA e05f6d6: FAILURE_PRE_RUN_NO_RUNNER, sin XML.
- CI automático #41181395040 SHA dbc9b359f9a477971c3ea95fd706bb6e29a43ea6: FAILURE_PRE_RUN_NO_RUNNER, tres jobs 124479354266/124479354466/124479354625, sin pasos/XML, ~4 segundos de pared. No se reintentó ni se modificaron pagos. Minutos facturados no inferidos de tiempo de pared.
- Criterios S2.A6 congelados: 1 FIVE_OWNERS CERTIFIED; 2 FBS2_ISOLATED_RESTORE CERTIFIED; 3 JOURNAL_REPLAY_WAL CERTIFIED; 4 F1 IN_PROGRESS; 5 F2 IN_PROGRESS; 6 F3 IN_PROGRESS; 7 F4 IN_PROGRESS; 8 T4 NOT_RUN. Total 3/8. V2 OFF; S3/S4 cerrados.
- Bloqueo: runners GitHub Actions fallan antes de asignación, causa exacta no confirmada; T4 requiere dispositivo físico; F1 proveedor remoto real requiere credencial y consentimiento seguros.
- Próxima acción: verificar disponibilidad de runners en modo READ_ONLY; no repetir ciegamente. Al volver CI, certificar prueba Android F3 y flujo SAF positivo, luego F2 recuperación protegida; mantener F1 real pendiente sin inventar certificación.
