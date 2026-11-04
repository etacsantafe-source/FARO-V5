# FARO V5 — reglas obligatorias de agentes y trabajo manual

**LEER ANTES DE PROGRAMAR.** La IA piensa; FARO ejecuta. La autoridad vigente es el HEAD real de `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`, más el puntero `docs/v5/FARO_CONTINUITY_POINTER.json`. No usar como estado actual C138 o snapshot de 2026-10-30. El repositorio V5 es PÚBLICO desde 2026-10-31; **no cambiar visibilidad** ni tocar `main` o V4.4.

## Regla que no se puede omitir en ningún chat: siete bloques + guardia
1. Leer puntero, `docs/v5/FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md`, `docs/v5/FARO_V2_PROTOCOL_RETRO_AUDIT_20261104.json`, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`.
2. Ejecutar por unidad B1 evidencia → B2 comparación V4.4 y reutilización → B3 investigación de alternativas → B4 unificación → B5 auditoría adversarial **ANTES** de escribir → B6 implementar+CI → B7 auditoría final. Omitir únicamente bloques con evidencia vigente de la misma unidad y SHA, no inventar PASS.
3. **NO ESCRIBIR CÓDIGO funcional si el permiso está BLOCKED**, aunque el usuario diga «continuar». Antes de adquirir writer para código, comprobar el preflight con la ruta exacta, por ejemplo:
   `python3 scripts/quality/faro-protocol-gate.py --mode preflight --unit M2A --path v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt`
   Si STOP, completar B1–B5 en docs, luego emitir permiso limitado en un commit **solo de documentación**. Nunca autoaprobar preauditoría sin evidencia verificada.
4. Cada commit de `v5/**`, `scripts/v5/**` o workflow funcional exige `FARO-UNIT:<id>` en el mensaje, scope limitado y permiso anterior válido. No mezclar código y cambio de permiso en un mismo commit; documentar B7 (PASS o RED_FIX de la misma unidad) antes de otro commit funcional.
5. **Control automático**: `.github/workflows/faro-v5-protocol-guard.yml`, job `seven-block-evidence`, revisa todos los commits desde `197e68bc1d9c52917a9066a27e6a0fa235a28217`. Si CI roja, STOP. El workflow no es automáticamente una regla required status; no confundir visibilidad de fallo con bloqueo administrativo de push. 14 tests sintéticos confirmados PASS por run `41876852639`, commit `59962008f0c78366c7a687b8974741b396c751ab`.
6. Antes de cualquier escritura adquirir el **writer GLOBAL CAS** contra HEAD exacto, revalidarlo, hacer checkpoint durable, liberar writer. Si ajeno HELD/UNKNOWN, solo READ_ONLY, sin takeover. No mantener writer durante CI.

## Objetivo y límites actuales
- PRIORIDAD: auditar retrospectivamente **M0/M1/M2A/M2B/M3** (B1–B5 faltantes) antes de nueva escritura funcional; V2 mínima **aislada de desarrollo** → `V2_DEV_CORE_READY` probado → cierres S3 → S4; no dispersarse.
- S2 CLOSED 8/8 sin repetir. S3 ≈40% desarrollado **estimado** y 0/5 cerrado; S4 **no abierta**. App V2 productiva OFF, Noche OFF, Auto OFF, cola CLOUD STAGED.
- Cinco owners, AtomicApply/receipts/Undo/recovery, Evidence≠Reality, único writer, LOCAL_ONLY sin APIs pagas, catálogo de 172 (Guardados +8 **no aprobados**). No ISO/IRAM, secretos, credenciales, firmas, datos del usuario, promociones ni cambios de teléfono sin autorización.
- CI exacta por SHA + logs/XML; una sola campaña Android física cuando sea procedente. Mantener checkpoints, % S3 estimado y cerrado, y termómetro cualitativo al terminar turno.
- «Continuar FARO V5» basta para reanudar **trabajo manual previamente autorizado y acotado**, sin habilitar V2 productiva, Auto/Noche, pagos o escrituras fuera de rama. El guardia prevalece frente a una sugerencia improvisada de programar sin B1–B5.

## Limitaciones no ocultables
Un agente autorizado a reescribir las propias reglas o un administrador que desactive Actions puede eludir un status check si no es required. El usuario debe configurar `seven-block-evidence` como required status check en la rama para que GitHub bloquee la integración de cambios rojos. No afirmar protección obligatoria del servidor hasta verificarla.
