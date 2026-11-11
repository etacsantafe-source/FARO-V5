# X03 U01 — B7 RED_FIX 2: sincronización de aceptación en test

## B7
Disposición RED_FIX de `29a5d212d916ffe09e31e2acd0faa1110ebe3073`, misma unidad. Guardia de ese SHA `43103133054` SUCCESS. Run corregido `43103133222` en curso; no declarar que ya pasó ni que ya falló.

Evidencia causal del primer SHA 5cd61c47: run `43102225011`, job `130796325369`, Android API35, compilación y manifest PASS; prueba localV2DevNoInternetNoImplicitModelOrApplyAfterRecreate PASS, prueba explícita FAIL en línea 96, no encuentra Consultar FARO. Clasificación TEST_ASSERTION / UI_SYNCHRONIZATION. No fallo de compilación ni boot.

El helper send espera una única coincidencia del body. Esa coincidencia puede ser el editor aún sin vaciar, y Enviar deshabilitado también significa busy=true durante IO. Compose waitForIdle no certifica recepción de IO Workspace. Por tanto el test observa antes del recibo canónico y falla al buscar el botón. La corrección anterior de epoch no afecta esta causa: send conserva el mismo helper en 29a5d212.

Corrección acotada exclusivamente a la ruta test ya autorizada: esperar en conjunto editor de chat vacío y una única coincidencia del body en timeline; después comprobar Enviar deshabilitado y botón. La condición de aceptación se fortalece; no agregar sleeps, no relajar assertIsEnabled/anti-dispatch/idempotencia. Mantener las dos pruebas reales y barrera lifecycle RED_FIX1. El producto no cambia en este commit. Solo se programa otra CI por este delta causal; no rerun ciego ni cancelación de CI activa.

B1–B5 original/delta y permiso de siete rutas continúan válidos; registrar last_code_review RED_FIX antes del commit test. Si persiste ausencia del botón tras confirmación canónica, extraer nuevo stack/estado y revisar causa sin asumir que sea la misma sincronización. X03 global OPEN, no XML/APK B7 PASS certificado aún.
