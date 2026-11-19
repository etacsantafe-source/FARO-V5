# FARO V5 — W03 Asuntos · Handoff ejecutable Work Turbo
Fecha 2026-11-19; rama work/s3-night-20261102; HEAD base bfc7b1ec967d0465fcdd95617516300e84f96ad4. **PREPARED DOC_ONLY / NO READY**.
Leer antes de hacer nada: AGENTS.md, pointer, permit, protocolo B1–B7; `docs/v5/FARO_S4_W03_MATTERS_B1_B5_PREAUDIT_20261119.md`; S4 P08 §W03; P02 L08, P03 R08, P05 A01–A03, P07 G03/G04/P7T17; W02 B7 global y W01 B7 global (NO repetir).

## Revalidación de Work
1. Releer HEAD, writer remoto CAS `faro-v5-writer-lock`, epoch, guard del SHA actual y permiso. Si ajeno HELD/UNKNOWN => READ_ONLY; prohibido takeover.
2. Este handoff **NO** emite READY. Para cada unidad completar preauditoría vigente B1–B5 (incluyendo B3 primaria con URLs, licencia y B5 adversarial), confirmar rutas exactas/código actual, guard de commit documental SUCCESS, publicar permiso READY **solo esas rutas**, preflight `python3 scripts/quality/faro-protocol-gate.py --mode preflight --unit <UNIT> --path <PATH>` por ruta, entonces B6 con writer CAS y mensaje `FARO-UNIT:<id>`; no mezclar permiso y código.
3. Tras B6 liberar writer mientras CI corre, verificar run exacto SHA, XML/JUnit, artifact/hash/manifest si Android; B7 independiente y consumir READY en BLOCKED antes de otra unidad. Si CI roja, RED_FIX acotado. No repetir tests inalterados W01/W02.

## Cadena de unidades encadenadas sin pausas innecesarias
| Orden | ID | Entregable + B7 requerido |
|---|---|---|
| A | S4_W03_A_MATTER_DETAILS_PERSISTENCE | prioridad, revisión, historial, migración/backup/restore y tests legacy/Finance9 |
| B | S4_W03_B_MATTER_REVIEW_APPLY | alta, edición, Esperar/Resolver/Cerrar/Reabrir, Review/Apply, receipts/Undo |
| C | S4_W03_C_REMINDER_CIVIL_DUE | regla y ocurrencia, recurrencia y calendario civil con tests DST |
| D | S4_W03_D_REMINDER_REVIEW_AND_LOCAL_NOTICE | create/update/cancel/attend, link con grant Matter, Avisos local desacoplados |
| E | S4_W03_E_MATTERS_ANDROID_DEV | Chat/drawer→Asuntos, UI 200%/TalkBack/offline, API35 XML y APK aislada |
| F opcional | S4_W03_F_OPTIONAL_OS_DELIVERY | OS opt-in separado, si no se autoriza: DEFERRED/NOT_RUN, nunca PASS |
| G | S4_W03_G_GLOBAL_B7_ANDROID_DEV | B7 final por SHA/runs/XML/ZIP/hashes, permiso BLOCKED, writer RELEASED |

**Aceptación estricta:** PF15–PF17/P7T17 y T-A01..A06, T-B01..B07, T-C01..C07, T-D01..D07, T-E01..E08. Todos DISEÑADOS/NOT_RUN al publicar este handoff. F agrega T-F01..F07 si se autoriza. Error de B5=STOP. V4.4 y W01/W02 jamás modificar para pasar gate.
**Ámbitos:** Matter/Reminder separados; Notice Auxiliary no cierre; OS Delivery no pago/cierre, permisos Android opcionales; sin SCHEDULE_EXACT_ALARM automático, sin Internet/IA paga ni Autómata/Noche, V2 productiva OFF. W08 necesita Due confiable pero no es parte de W03. G15 físico NOT_RUN hasta campaña posterior.
**Siguiente acción Work:** B1–B5 individual W03-A contra HEAD nuevo → guard documental → READY exact-route → preflight → B6/JVM/CI → B7 → W03-B, etc. No esperar nueva orden salvo bloqueo real o decisión humana imprescindible.