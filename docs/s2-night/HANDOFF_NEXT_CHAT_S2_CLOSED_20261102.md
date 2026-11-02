# FARO V5 — HANDOFF CLEAN PARA CHAT NUEVO (02/11/2026)

## Estado autorizado
- Repositorio: etacsantafe-source/FARO-V5.
- Rama de trabajo aislada: work/s2-chat-finish-night-20261102.
- S2.A6: CLOSED, 8/8, congelado en docs/s2-night/RUN_STATE.json (commit 5fa0e55c).
- Dictamen físico y alcance del aviso en docs/s2-night/T4_PHYSICAL_UI_FIX_HANDOFF_20261102.md (commit 41695c46).
- V2 OFF; no activar producción. main intacta; no abrir S3/S4 sin autorización explícita. Writer NONE, HANDOFF CLEAN. No iniciar automatizaciones ni editar repositorio al entrar en chat nuevo; primero conversar.

## Evidencia no repetir
- GitHub Actions #41421309967 SUCCESS: F1 JVM 4/4, A5 JVM 9/9, Android emulador 5/5, XML comprobado. Corrida posterior de UI informada verde por usuario; no confundir con verificación independiente del XML.
- Usuario probó en teléfono: Chat con Groq real, Review, Apply, cierre y relanzamiento con perfil `Prueba T4` persistente.
- Cinco bases SQLite integridad OK; Reality: 1 apply_attempt COMMITTED y 1 execution_receipt MATCH fingerprint; Workspace: 13 recibos distintos; Auxiliary: 1 delivery_notice y 1 delivery_intent con notice_id coincidente.
- delivery_intent.state PLANNED: el bridge de certificación aislada documenta explícitamente que no se despacha notificación Android y dispatcher es opcional null. No afirmar POSTED_TO_OS ni entrega efectiva de notificación. Aviso durable sí certificado.
- No repetir Groq, Apply, Termux, ADB, SAF ni pruebas físicas de S2.

## Próximo chat
El usuario dirá `Continuar FARO V5`. Recuperar este handoff y RUN_STATE.json. Presentar cierre S2 8/8, sin reabrirlo. Conversar sobre próximos pasos y solicitar autorización antes de cualquier acción en GitHub o automatización. No abrir S3 ni S4 automáticamente.
