# FARO V5 — Dictamen de certificación S2.A6 · 02/11/2026

**Decisión ejecutada: 6/8 criterios CERTIFIED. S2.A6 sigue OPEN.**
El contador anterior 3/8 era una foto histórica de FARO Noche, no una valoración de la evidencia posterior. Esta revisión certifica los criterios 5, 6 y 7 que sí cuentan con evidencia dirigida; mantiene abiertos el 4 (F1 integrado) y el 8 (T4 campaña global), sin fabricar pruebas físicas ni desbloquear producción.

La fuente autoritativa actual de esta rama es [`docs/s2/S2_A6_CURRENT.json`](S2_A6_CURRENT.json). Los archivos `docs/CIERRE_NOCHE_V14_RUN_STATE.json` y `docs/faro-noche-v15/RUN_STATE.json` son **históricos** y no se reescriben retroactivamente.

## Decisión por criterio congelado

| # | Criterio | Estado actual | Evidencia precisa |
|---|---|---|---|
| 1 | FIVE_OWNERS | **CERTIFIED** | Certificación previa, sin cambios |
| 2 | FBS2_ISOLATED_RESTORE | **CERTIFIED** | Certificación previa, sin cambios |
| 3 | JOURNAL_REPLAY_WAL | **CERTIFIED** | Certificación previa, sin cambios |
| 4 | F1_ANDROID_CHAT_E2E | **OPEN** | [#41266155404](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41266155404) 27/27 automática y [#41277545474](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41277545474) integración PASS; Groq debug real y revocación fueron PASS **reportados por usuario**. No hay registro inequívoco de la **cadena completa en teléfono** Chat→Review humano→Apply aislado→receipt→Delivery→reinicio sin duplicar |
| 5 | F2_ANDROID_ACTIVATION_RECOVERY | **CERTIFIED** | [#41276096648](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41276096648) 5/5 Android; [#41277220813](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41277220813) 16/16 JVM; [#41379627980](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41379627980) otros 5/5 Android actuales. Recuperación WAL, rollback, no replay duplicado y puntero V1 aislado. La dimensión **teléfono físico global** pertenece además al criterio T4 |
| 6 | F3_FIVE_STORE_BACKUP_SAF_CONSENT | **CERTIFIED — alcance aislado S2** | [#41379627980](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41379627980): 6 F3 consentimiento + 8 F3 proveedor/journal, y 6 T4 instrumentadas. APK V5 [release](https://github.com/etacsantafe-source/FARO-V5/releases/tag/faro-v5-t4-saf-auto-v5-20261101), SHA256 `2619ce5ecc9ff1a34f9b460506147742022ef0eab14991e40869c8743afdd789`: el usuario **reportó** exportación y restauración verdes en Moto G15, con evidencia fotográfica previa de consentimiento nuevo desmarcado. El código solo devuelve VERDE tras verificar cinco owners con datos no vacíos, journal validado, V1 sintética intacta y V2 OFF |
| 7 | F4_INTEGRAL_REGRESSIONS | **CERTIFIED** | [#41277545474](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41277545474): jobs `f4-jvm-36` y `f4-android-27` ambos SUCCESS, 36/36 + 27/27. [#41379627980](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41379627980) 32/32 adicional. Comparación GitHub de rama F4 con rama T4: ningún `src/main` modificado |
| 8 | T4_PHYSICAL | **IN_PROGRESS** | Ya hubo prueba real de consentimiento, SAF V4/V5 con exportación/restauración verde **reportadas**, y otros 27 ensayos instrumentados en dispositivo informados previamente. Pero no existe evidencia completa y coherente del **recorrido integrado físico F1→F4 sobre un único build**; no certificar el conjunto por dos respuestas «Verde» de SAF |

## Lo que queda para terminar S2

**Un solo bloqueo de evidencia compartido por los criterios 4 y 8:** demostrar de forma controlada en el Moto G15 el camino integrado **Chat → Review → aprobación → Apply aislado → receipt → Delivery → reinicio/recuperación**, con el proveedor real solo cuando exista consentimiento explícito en el dispositivo. Es distinto de la prueba debug de conexión Groq y del SAF aislado. Las pruebas JVM/emulador existentes ya cubren gran parte de esta secuencia, pero no sustituyen la observación física que exige T4.

**Prohibido para “cerrar” artificialmente:** activar `V2_ACTIVE_ENABLED` para exportar stores reales, publicar `main`, abrir S3/S4, usar claves Groq en CI, pedir otra exportación/restauración SAF que ya se comprobó o afirmar PASS físico de acciones que no se hicieron. El cierre productivo posterior requiere su propio gate y autoridad.

## Trazabilidad y protección
- Estado antes de esta valoración: 3/8 por checkpoints históricos de 30/10–01/11.
- Estado después: **6/8**, criterios certificados 1,2,3,5,6,7. **F1 (#4) abierto, T4 (#8) incompleto**, S2.A6 OPEN.
- Código funcional probado `0a2c1f4e8bcfa04820f4ede7513c041da936cfac`; #41379627980 `completed/success`; release #41380364442 `completed/success`.
- `main` = `9bae214541f466a62e149853931b96abb8d74ed9` al verificar. V2 OFF; S3/S4 sin abrir; sin cambios productivos; la actualización es únicamente documental.
- Los «VERDE» físicos V5 son resultados **informados por el usuario**, no capturas del estado final. Esta distinción no invalida el avance SAF, pero impide inventar detalle literal adicional.

**No se requieren más certificaciones documentales repetidas**: resolver solamente el bloqueo integrado F1/T4 y, cuando exista evidencia, actualizar el contador a 8/8 mediante un único dictamen final.

## Decisión humana posterior: congelación de recorridos imposibles (02/11/2026)

**APROBADO — CONGELAR F1 (#4) Y T4 (#8)**. Motivo: el usuario indicó que los recorridos completos no pueden realizarse todavía porque faltan funciones visibles, especialmente Chat. El código confirma `V2_ACTIVE_ENABLED=false` y la superficie Chat integrada de producción no está disponible para esa prueba física. Seguir repitiendo SAF no puede desbloquearlos.

**Estado operativo:** `S2.A6 = OPEN, EXECUTION FROZEN_HANDOFF_CLEAN, 6/8 CERTIFIED`. No se confunde «congelado» con «certificado»: permanecen abiertos los criterios 4 y 8, con dependencia documentada de una superficie integrada Chat → Review → Apply → receipt → Delivery → reinicio/recuperación en un único APK aislado. Los seis restantes **ya certificados** no se deshacen, ni se vuelven a probar por formalidad.

**Reanudación:** cuando exista una UI Chat integrada segura y testeable sin afectar la V1, retomar únicamente F1/T4 contra los contratos congelados; obtener los consentimientos humanos de Android donde corresponda. No habilitar V2 prematuramente, no modificar main, no abrir S3/S4 ni activar FARO Noche por esta decisión. El desarrollo/certificación pendiente queda congelado sin más órdenes de prueba inmediata al usuario.

**No declarar «S2 COMPLETADA» ni «8/8»**: este es un handoff técnico-documental con bloqueo explícito. El progreso de las otras fases requiere autorización y un plan separado; no se deriva de congelar estos dos criterios.
