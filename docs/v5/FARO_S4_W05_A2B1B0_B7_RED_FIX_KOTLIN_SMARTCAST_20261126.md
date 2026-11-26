# FARO V5 — W05-A2B1B0 — auditoría B7 RED_FIX (no PASS)

Unidad: S4_W05_A2B1B0_SQL14_RESERVATION_ATOMIC_APPLY_PORT.
Código auditado: b2705e1c7733d74e0339bcb50ef7b023f0bfb603.
Fecha: 2026-11-26. Scope original: 4 rutas exactas, B1–B5 VERIFIED, preflight CI SUCCESS.

## B7 — RED_FIX

- Guardia de siete bloques 45835861380 SUCCESS para el SHA auditado.
- CI 45835861255 FAILURE en :store:reality:compileKotlin, sin tests ejecutados ni XML original.
- Diagnóstico real de log de GitHub Actions: Kotlin SMARTCAST_IMPOSSIBLE al usar
  request.reservationRef (propiedad pública de módulo externo) tras comprobación null.
  Archivo RealityHousehold14ReservationApplyPort.kt, línea 82.
- No se declaran tests PASS; no se considera concluida la unidad.
- Corrección mínima: copiar reservationRef a variable local estable y validar la copia.
- Workflow dirigido de RED_FIX deberá aceptar el subconjunto exacto de rutas cambiadas,
  siempre dentro del permiso actual (archivo fuente + workflow); ejecutar preflight
  contra el permiso READY del padre y JUnit XML real 5/5.
- No tocar V2 productiva, stock real, pagos, N-016, Auto/Noche ni otros propietarios.
- Mantener RED_FIX en last_code_review hasta que el siguiente código consiga guardia
  SUCCESS, test JUnit XML original sin errores y B7 PASS independiente.

Este documento no autoriza ejecución productiva ni presenta B7 PASS.
