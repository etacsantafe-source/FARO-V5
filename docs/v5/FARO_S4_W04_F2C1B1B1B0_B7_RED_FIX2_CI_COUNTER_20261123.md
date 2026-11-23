# FARO V5 — F2C1B1B1B0 · B7 RED_FIX2 · workflow counter blocks one-file RED_FIX

Fecha 2026-11-23. Scope unchanged `S4_W04_F2C1B1B1B0_LEASED_OWNER_REAL_SOURCE_DEV`. Commit funcional revisado `f0220502b761d79e4d3de8dbe64a01f441542ace`.
**RED_FIX2, no B7 PASS.**

## B7 — Resultado real y causa
- F2C1B1B1B0 base código `ce97438755eabcd0fb868d31673b5df68cfefb5b`, CI `45193030044` falla 4/4 en lectura. XML artifact `14826568341`, revisión `docs/v5/FARO_S4_W04_F2C1B1B1B0_B7_RED_FIX_NESTED_OWNER_SQLITE_20261123.md` blob `5deaa55fc75219f2dfdb97b2f223041703941639`.
- RED_FIX1 `f0220502b761d79e4d3de8dbe64a01f441542ace` modifica SOLO `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10Evidence2DevSelectionReadPort.kt`. Es la ruta de lectura no recursiva para callbacks de autorización dentro de la transacción; mantiene ambas políticas antes y después, y preserva la ruta F2C0 anterior. Guardia de código `45194704552` SUCCESS.
- CI `45194704679` job `137761945423`: **FAILURE en paso `Mandatory exact four READY paths` ANTES de compilar y ejecutar pruebas**. Log: `FARO_PROTOCOL_GATE_PASS`, `FARO_PROTOCOL_PREFLIGHT_PASS` para la ruta modificada y luego `test "$count" -eq 4` (count=1) sale 1. No hubo nuevo XML: **0/4 ejecutadas**, NO inferir resultado Kotlin.
- Causa concreta: la lógica del workflow presupone 4 rutas en TODOS los commits, pero el protocolo permite RED_FIX de 1–4 rutas exactas del permiso vigente. El guard permite y aprobó el commit con 1 archivo.

## RED_FIX2 exactamente una ruta
Modificar `.github/workflows/faro-v5-s4-w04-f2c1b1b1b0-owner-backed-source.yml`: cambiar `test "$count" -eq 4` por **`test "$count" -ge 1` y `test "$count" -le 4`**. Cada ruta sigue pasando preflight contra el READY original y la auditoría B7 RED_FIX de su predecesor; no se amplía ninguna autorización. No cambiar fuentes, fixture, test classes, CI de otras unidades ni seguridad.
Reejecutar la CI dirigida SOLO t07–t10 con XML cuatro casos. Si falla, investigar evidencia real. F2C1B1B1B1 Android y F3 pendientes. V2 productiva, Apply, pagos, stock, G15 y Auto/Noche OFF.
