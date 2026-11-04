# FARO V5 — M2.A: autoridad DEV aislada de cinco owners (04/11/2026)

**Predecesor M1:** `d2a41acb7dc63429a0075adc222807b02e8ee502`. CI [#41864185753](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41864185753) **SUCCESS**, log `V2_CORE_M1_ISOLATION_PASS 4/4`, build PASS; regresión [#41864185671](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41864185671) **SUCCESS**, XML Home 9/9 + Workspace 7/7 + Bridge 5/5 + Avisos 4/4 = 25/25. No repetir por SHA.

## M2.A — unidad de implementación (no activación productiva)

- Fuente **únicamente** `v5/app/src/v2CoreDev/kotlin/.../FaroV5V2CoreDevAuthority.kt`, fuera de shipping y debug habitual. El builder NO está conectado todavía a la UI: `M2_A_CODE_CI_PENDING`, no `V2_DEV_CORE_READY`.
- Sobre un **root privado provisto sólo por el futuro launcher DEV**, exige bootstrap V1 recuperable y ejecuta cambio V2 con `FileBootstrapJournal`, `GenerationSwitchCoordinator`, `ValidatedGenerationActivationCoordinator`, `FileGenerationReplayFence` y el mismo `SingleProcessMaterialWriteGate`. Inicializa cinco stores solamente en la nueva generación V2, no sustituye el V1 habitual.
- La admisión V2 `v2HostCertified=true` se pasa explícitamente sólo desde la clase del source-set DEV: `FaroV5HostCertification.V2_ACTIVE_ENABLED` shipping permanece `false`. Antes y después de mutar el puntero hay revalidación de formatos/authority, verificadores canónicos y health de cinco stores. Proyecciones OS son no-op **exclusivamente en DEV**, no se envía nada ni se habilita Apply.
- Se expone una sesión local con Workspace read/timeline, selección y Composer bajo fences de puntero, journal, replay y cierre; **no** se exponen ModelGateway, Assistant dispatch, ReviewedApply, Delivery ni OS.
- Un stage a medio materializar, journal inconsistente, workspace corrupto o puntero inesperado falla cerrado. Reinicio reabre el grafo V2 activo. Recuperación automática sólo para etapas ya VALIDATED o posteriores (cuarentena ante estados PREPARING/STAGED o intervención manual), sin afirmar que todas las interrupciones se recuperan silenciosamente.

## Pruebas de aceptación dirigidas

`FaroV5V2CoreDevAuthorityTest` (variant `testV2CoreDevUnitTest`): (1) pointer y journal COMPLETED + replay fence, V1 rechaza V2; (2) selección real sobre Workspace persiste después de cerrar/reabrir y callbacks viejos rechazan; (3) workspace corrompido bloquea reinicio sin regenerarlo. El workflow requiere XML exacto `3/3` y mantiene prueba M1 `4/4`. **CI pendiente** al escribir este documento, no contar PASS anticipado.

## Pendiente real

1. Resolver cualquier fallo de CI dirigida M2.A y comprobar log XML exacto por SHA.
2. **M2.B** montar entrada exclusiva DEV con aplicación/launcher propio y root bajo `noBackupFilesDir`, negar inicialización de CompositionRoot shipping y cualquier UI de Apply/IA/OS. No reutilizar la Activity F1 que necesita Groq.
3. **M3** conectar actividad DEV a sesión Workspace/Timeline/Composer, pruebas de mensaje y reinicio con seguridad; no Search shipping sin ACL legítima.
4. **M4/M5** completar negativos de egress/recovery y evidencia Android consolidada; entonces `V2_DEV_CORE_READY`.

**Controles:** S2 8/8 cerrado; S3 ≈40% desarrollado / 0 de 5 formal; S4 sin abrir; V2 productiva OFF; Auto/Noche OFF; CLOUD STAGED; catálogo 172; no APIs pagas. Writer CAS debe liberarse al finalizar el checkpoint. No añadir S4 sin dependencia demostrada.

## CI primer intento: fallo de descubrimiento de tarea (no de compilación)

- Código `db2724c6581cc3acb26801fa763825c410d7e4dd`, run [#41868115531](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41868115531): **FAILURE** antes de ejecutar JVM porque AGP 9 sólo creó `testDebugUnitTest` y no `testV2CoreDevUnitTest` para un build-type extra.
- Cambio mínimo: sólo en el comando de CI M2 se pide `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false` (opción documentada de AGP 9); mantiene intactos Gradle de producción/debug y el build type aislado. **Corrección pendiente nueva CI**; no marcar M2 PASS hasta XML real 3/3.

## Segundo CI, error estructural de source-set (NO ejecución de M2)

- SHA `6f8a3b2957d1f75826270e174053b1fe5f1cfa24`, Actions [#41869278395](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41869278395): ya existe tarea `testV2CoreDevUnitTest`; compilación de todos los tests comunes falló por `FaroV5T4PostPickerGateTest` que referencia `FaroV5T4PostPickerGate` (solo `src/debug`). **Ninguna prueba M2 fue ejecutada**.
- **Arreglo mínimo sin editar código S2:** trasladar exactamente el mismo blob de prueba (SHA `dcc71c5a56628909d14c64615fd00baaf475609b`) de `src/test/kotlin/...` a `src/testDebug/kotlin/...`. El código T4 permanece intacto; una prueba de `debug` solo debe compilar contra la variante `debug`.
- Nueva CI dirigida exige XML **M2 3/3 + T4 debug 4/4 + M1 aislamiento 4/4**. El alcance de certificación sigue siendo JVM y manifiestos, no Android físico.
